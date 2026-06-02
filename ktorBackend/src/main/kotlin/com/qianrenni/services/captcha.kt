package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.redisManager
import com.qianrenni.utils.KaptchaImageGenerator
import com.qianrenni.utils.TokenGenerator
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

class CaptchaService(val application: Application) {

    /**
     * 生成随机验证码文本
     * @param length 验证码长度，默认4
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

        // 验证成功后删除验证码（防止重放攻击）
        redis.del(captchaId).await()

        return storedText.equals(text, ignoreCase = true)
    }
}
private val CaptchaServiceAttributeKey = AttributeKey<CaptchaService>("CaptchaService")

val Application.captchaService: CaptchaService
    get() = attributes[CaptchaServiceAttributeKey]

fun Application.registerCaptchaService() {
    attributes[CaptchaServiceAttributeKey] = CaptchaService(this)
}