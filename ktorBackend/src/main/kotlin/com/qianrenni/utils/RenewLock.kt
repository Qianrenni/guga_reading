package com.qianrenni.utils

import com.qianrenni.database.redisManager
import io.ktor.server.application.*
import io.lettuce.core.ScriptOutputType
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds


class RenewLock(
    private val lockKey: String,
    private val lockValue: String,
    private val lockTimeout: Int,
    private val renewInterval: Long? = null,
    private val application: Application
) {
    private val interval = renewInterval ?: (lockTimeout / 3).toLong().coerceAtLeast(1)
    private var renewJob: Job? = null

    /**
     * 启动自动续期
     */
    fun start(): Job {
        renewJob = CoroutineScope(Dispatchers.IO).launch {
            val redis = application.redisManager.getAsyncCommands()
            while (isActive) {
                try {
                    val luaScript = """
                        if redis.call("get", KEYS[1]) == ARGV[1] then
                            return redis.call("expire", KEYS[1], ARGV[2])
                        else
                            return 0
                        end
                    """
                    val result = redis.eval<Long>(
                        luaScript,
                        ScriptOutputType.INTEGER,
                        arrayOf(lockKey),
                        lockValue,
                        lockTimeout.toString()
                    ).await()

                    if (result == 0L) {
                        application.log.info("Lock $lockKey is no longer held (deleted or stolen). Stopping renewal.")
                        break
                    }
                    application.log.debug("Lock renewed: $lockKey")
                } catch (e: CancellationException) {
                    application.log.debug("Lock renewal cancelled for $lockKey")
                    break
                } catch (e: Exception) {
                    application.log.warn("Failed to renew lock $lockKey: ${e.message}")
                    break
                }
                delay(interval.seconds)
            }
        }
        return renewJob!!
    }

    /**
     * 停止自动续期
     */
    suspend fun stop() {
        renewJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                application.log.debug("Canceling lock renewal task for $lockKey")
                try {
                    job.join()
                } catch (e: CancellationException) {
                    // Expected
                } catch (e: Exception) {
                    application.log.warn("Error while cancelling lock renewal: ${e.message}")
                }
            }
        }
    }
}
