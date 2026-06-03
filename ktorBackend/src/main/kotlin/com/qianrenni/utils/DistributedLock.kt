package com.qianrenni.utils

import com.qianrenni.database.redisManager
import io.ktor.server.application.*
import io.lettuce.core.ScriptOutputType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 基于 Redis 的分布式锁
 * 对应 Python 项目中的 app/utils/distribute_lock.py
 */
class DistributedLock(
    private val lockKey: String,
    private val expireTime: Int = 10,
    private val blocking: Boolean = false,
    private val timeout: Long = 10,
    private val application: Application
) {
    private val lockValue = UUID.randomUUID().toString()
    private var renewLock: RenewLock? = null

    /**
     * 尝试获取锁
     */
    private suspend fun tryAcquire(): Boolean {
        val redis = application.redisManager.getAsyncCommands()
        // SET NX EX - 仅在键不存在时设置，并设置过期时间
        val result = redis.setex(lockKey, timeout, lockValue).await()
        val flag = result != null
        if (flag) {
            renewLock = RenewLock(
                lockKey = lockKey,
                lockValue = lockValue,
                application = application,
                lockTimeout = timeout.toInt()
            )
            renewLock?.start()
        }
        return flag
    }

    /**
     * 释放锁
     */
    private suspend fun release() {
        val redis = application.redisManager.getAsyncCommands()
        val luaScript = """
            if redis.call("GET", KEYS[1]) == ARGV[1] then
                return redis.call("DEL", KEYS[1])
            else
                return 0
            end
        """
        redis.eval<Long>(luaScript, ScriptOutputType.INTEGER, arrayOf(lockKey), lockValue).await()
    }

    /**
     * 获取锁（带阻塞等待）
     */
    suspend fun acquire(): Boolean {
        return withContext(Dispatchers.IO) {
            var result = false
            return@withContext if (blocking) {
                while (isActive) {
                    if (tryAcquire()) {
                        result = true
                        break
                    }
                }
                result
            } else {
                tryAcquire()
            }
        }
    }

    /**
     * 释放锁
     */
    suspend fun releaseLock() {
        renewLock?.stop()
        release()
    }
}

/**
 * 便捷函数：创建分布式锁实例
 */
fun Application.distributedLock(
    lockKey: String,
    expireTime: Int = 10,
    blocking: Boolean = true,
    timeout: Long = 10
): DistributedLock {
    return DistributedLock(lockKey, expireTime, blocking, timeout, this)
}
