package com.qianrenni.services

import com.qianrenni.database.redisManager
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CacheService(val application: Application) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun generateCacheKey(
        args: List<Any> = emptyList(),
        excludeArgs: List<Int> = emptyList(),
        keyPrefix: String
    ): String {
        val filteredArgs = args.filterIndexed { index, _ -> index !in excludeArgs }
        // 将参数转为字符串再序列化，避免 kotlinx.serialization 对 Any? 类型的限制
        val payload = filteredArgs.map { it.toString() }.joinToString("")

        return "$keyPrefix:${md5(payload)}"
    }

    suspend fun <T> cacheGetInternal(
        cacheKey: String,
        expire: Int,
        ignoreNull: Boolean,
        lockTimeout: Int,
        fallbackFunc: suspend () -> T,
        serializer: KSerializer<T>,
        redis: RedisAsyncCommands<String, String>
    ): T {
        // 1. 尝试读缓存
        val cached = redis.get(cacheKey).await()
        if (cached != null) {
            application.log.info("Cache hit: $cacheKey")
            return json.decodeFromString(serializer, cached)
        }
        // 2. 尝试加锁回源
        val lockKey = "lock:$cacheKey"
        val lockValue = UUID.randomUUID().toString()
        var lockAcquired = false
        var renewJob: Job? = null

        try {
            // Lettuce 设置 NX 和 EX
            val setResult = redis.setex(lockKey, lockTimeout.toLong(), lockValue).await()
            lockAcquired = setResult != null

            if (lockAcquired) {
                // 启动协程进行锁续期 (Watchdog)
                renewJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    while (isActive) {
                        delay(lockTimeout.seconds) // 每 1/3 超时时间续期
                        try {
                            redis.expire(lockKey, lockTimeout.toLong()).await()
                        } catch (e: Exception) {
                            application.log.error("Lock renew failed: $e")
                            break
                        }
                    }
                }

                // 双重检查
                val cached = redis.get(cacheKey).await()
                if (cached != null) return json.decodeFromString(serializer, cached)

                // 执行回源逻辑
                val result = fallbackFunc()

                // 决定是否缓存
                val isEmpty = result == null ||
                        (result is Collection<*> && result.isEmpty()) ||
                        (result is Map<*, *> && result.isEmpty())

                if (!(ignoreNull && isEmpty)) {
                    val encoded = json.encodeToString(serializer, result)
                    redis.setex(cacheKey, expire.toLong(), encoded).await()
                    application.log.info("Cache set: $cacheKey (expire=${expire}s)")
                }
                return result
            } else {
                // 未获取到锁：循环等待
                val startTime = System.currentTimeMillis()
                val maxWait = (lockTimeout + 1) * 1000L

                while (System.currentTimeMillis() - startTime < maxWait) {
                    delay(50.milliseconds)
                    val cached = redis.get(cacheKey).await()
                    if (cached != null) {
                        application.log.info("Cache filled by another worker: $cacheKey")
                        return json.decodeFromString(serializer, cached)
                    }
                }

                // 超时直接回源
                application.log.warn("Cache still empty after ${maxWait}ms, executing fallback directly: $cacheKey")
                return fallbackFunc()
            }
        } catch (e: Exception) {
            application.log.error("Error in cacheGet fallback: $e")
            throw e
        } finally {
            renewJob?.cancel() // 停止续期
            if (lockAcquired) {
                try {
                    // Lua 脚本安全释放锁
                    val luaScript = """
                        if redis.call("get", KEYS[1]) == ARGV[1] then
                            return redis.call("del", KEYS[1])
                        else
                            return 0
                        end
                    """
                    redis.eval<Long>(luaScript, ScriptOutputType.INTEGER, arrayOf(lockKey), lockValue).await()
                } catch (e: Exception) {
                    application.log.warn("Failed to release lock $lockKey: $e")
                }
            }
        }
    }

    /**
     * 简单设置缓存（不带锁）
     */
    suspend fun cacheSetSimple(
        key: String,
        value: String,
        expire: Long,
    ) {
        val redis = application.redisManager.getAsyncCommands()
        try {
            redis.setex(key, expire, value).await()
        } catch (e: Exception) {
            application.log.error("Error in cache set: $key: $e")
            throw IllegalStateException("服务器繁忙，请稍后再试")
        }
    }

    /**
     * 简单获取缓存值（字符串）
     */
    suspend fun cacheGetSimple(key: String): String? {
        val redis = application.redisManager.getAsyncCommands()
        return try {
            redis.get(key).await()
        } catch (e: Exception) {
            application.log.error("Cache get failed: $e")
            null
        }
    }

    /**
     * 简单删除缓存
     */
    suspend fun cacheDelete(key: String): Boolean {
        val redis = application.redisManager.getAsyncCommands()
        return try {
            redis.del(key).await() > 0
        } catch (e: Exception) {
            application.log.error("Cache delete failed: $e")
            false
        }
    }

    /**
     * 手动设置缓存
     */
    suspend fun <T> cacheSet(
        value: T,
        args: List<Any> = emptyList(),
        expire: Int = 300,
        ignoreNull: Boolean = true,
        excludeArgs: List<Int> = emptyList(),
        keyPrefix: String,
        lockTimeout: Int = 30,
        acquireLock: Boolean = true,
        serializer: KSerializer<T>,
    ): Boolean {
        val isEmpty = (value is Collection<*> && value.isEmpty()) || (value is Map<*, *> && value.isEmpty())
        if (ignoreNull && isEmpty) return false

        val redis = application.redisManager.getAsyncCommands()
        val cacheKey = generateCacheKey(args, excludeArgs, keyPrefix)
        val encoded = json.encodeToString(serializer, value)
        if (!acquireLock) {
            return try {
                redis.setex(cacheKey, expire.toLong(), encoded).await()
                true
            } catch (e: Exception) {
                application.log.error("Manual cache set failed: $e")
                false
            }
        }

        // 带锁逻辑 (与 cacheGet 类似，此处简化展示核心逻辑)
        val lockKey = "lock:$cacheKey"
        val lockValue = UUID.randomUUID().toString()
        var lockAcquired = false
        try {
            val setResult = redis.setex(lockKey, lockTimeout.toLong(), lockValue).await()
            lockAcquired = setResult != null
            if (!lockAcquired) return false
            // 双重检查
            if (redis.get(cacheKey).await() != null) return false
            redis.setex(cacheKey, expire.toLong(), encoded).await()
            return true
        } catch (e: Exception) {
            application.log.error("Manual cache set with lock failed: $e")
            return false
        } finally {
            if (lockAcquired) {
                try {
                    val luaScript =
                        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
                    redis.eval<Long>(luaScript, ScriptOutputType.INTEGER, arrayOf(lockKey), lockValue).await()
                } catch (e: Exception) {
                    application.log.warn("Failed to release lock in cacheSet: $e")
                }
            }
        }
    }
}

private val CacheServiceAttributeKey = AttributeKey<CacheService>("CacheService")
val Application.cacheService: CacheService
    get() = attributes[CacheServiceAttributeKey]

fun Application.registerCacheService() {
    this.attributes[CacheServiceAttributeKey] = CacheService(this)
}

/**
 * 在 Ktor 的 Application 或 Routing 中直接使用，语法类似 Python 的装饰器
 */
suspend fun <T> Application.cache(
    args: List<Any>,
    expire: Int = 300,
    ignoreNull: Boolean = true,
    excludeArgs: List<Int> = emptyList(),
    keyPrefix: String = "",
    lockTimeout: Int = 30,
    serializer: KSerializer<T>,
    block: suspend () -> T
): T {
    return this.cacheService.cacheGetInternal(
        expire = expire,
        ignoreNull = ignoreNull,
        lockTimeout = lockTimeout,
        fallbackFunc = block,
        cacheKey = this.cacheService.generateCacheKey(args = args, excludeArgs = excludeArgs, keyPrefix = keyPrefix),
        serializer = serializer,
        redis = this.redisManager.getAsyncCommands(),
    )
}

suspend fun <T> RoutingContext.cache(
    args: List<Any>,
    expire: Int = 300,
    ignoreNull: Boolean = true,
    excludeArgs: List<Int> = emptyList(),
    keyPrefix: String = "",
    lockTimeout: Int = 30,
    serializer: KSerializer<T>,
    block: suspend () -> T
): T {
    return call.application.cacheService.cacheGetInternal(
        expire = expire,
        ignoreNull = ignoreNull,
        lockTimeout = lockTimeout,
        fallbackFunc = block,
        cacheKey = call.application.cacheService.generateCacheKey(
            args = args,
            excludeArgs = excludeArgs,
            keyPrefix = keyPrefix
        ),
        serializer = serializer,
        redis = call.application.redisManager.getAsyncCommands(),
    )
}