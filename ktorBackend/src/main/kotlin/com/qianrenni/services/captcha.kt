package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.redisManager
import com.qianrenni.utils.KaptchaImageGenerator
import com.qianrenni.utils.TokenGenerator
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

class CaptchaService(val application: Application) {
    companion object {
        val attributeKey = AttributeKey<CaptchaService>("CaptchaService")
    }

    /**
     * 生成随机验证码文本
     * @param length 验证码长度,默认4
     * @return 由字母和数字组成的随机字符串
     */
    fun generateCaptchaText(length: Int = 4): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length).map { chars.random() }.joinToString("")
    }

    suspend fun getCaptcha(): Pair<String, ByteArray> {
        val (text, image) = withContext(Dispatchers.Default) {
            val text = generateCaptchaText()
            val image = KaptchaImageGenerator.generate(text)
            Pair(text, image)
        }
        val redis = application.redisManager.getAsyncCommands()
        val captchaId = TokenGenerator.uuid()
        redis.setex(captchaId, application.appConfig.captchaExpire.toLong(), text).await()
        return Pair(captchaId, image)
    }

    suspend fun verifyCaptcha(text: String, captchaId: String): Boolean {
        val redis = application.redisManager.getAsyncCommands()
        val storedText = redis.get(captchaId).await() ?: return false

        // 验证成功后删除验证码(防止重放攻击)
        redis.del(captchaId).await()

        return storedText.equals(text, ignoreCase = true)
    }

    /**
     * 获取数字验证码(用于忘记密码等场景)
     * @param keyPrefix 缓存键前缀
     * @param length 验证码长度,默认6
     * @param expire 有效期,默认120秒
     * @return 验证码
     */
    suspend fun getVerifyCode(
        keyPrefix: String,
        length: Int = 6,
        expire: Long = application.appConfig.captchaExpire.toLong()
    ): String {
        val answer = generateCaptchaText(length)
        val redis = application.redisManager.getAsyncCommands()
        val existing = redis.get(keyPrefix).await()
        if (existing != null) {
            throw IllegalArgumentException("Previous verify code exists, please try again later")
        }
        redis.setex(keyPrefix, expire, answer).await()
        return answer
    }

    /**
     * 验证数字验证码
     * @param keyPrefix 缓存键前缀
     * @param answer 用户输入的验证码
     * @return 验证结果
     */
    suspend fun verifyCode(keyPrefix: String, answer: String): Boolean {
        val redis = application.redisManager.getAsyncCommands()
        val cachedAnswer = redis.get(keyPrefix).await() ?: return false
        if (answer == cachedAnswer) {
            redis.del(keyPrefix).await()
            return true
        }
        return false
    }
}

val Application.captchaService: CaptchaService
    get() = attributes[CaptchaService.attributeKey]

fun Application.registerCaptchaService() {
    attributes[CaptchaService.attributeKey] = CaptchaService(this)
}