package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.redisManager
import com.qianrenni.utils.KaptchaImageGenerator
import com.qianrenni.utils.TokenGenerator
import io.ktor.server.application.*

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

    fun getCaptcha(): Pair<String, ByteArray> {
        val text = generateCaptchaText()
        val image = KaptchaImageGenerator.generate(text)
        val redis = application.redisManager.getSyncCommands()
        val captchaId = TokenGenerator.uuid()
        redis.setex(captchaId, application.appConfig.captchaExpire.toLong(), text)
        return Pair(captchaId, image)
    }

    fun verifyCaptcha(text: String, captchaId: String): Boolean {
        val redis = application.redisManager.getSyncCommands()
        redis.get(captchaId)?.let {
            return when (it.equals(text, ignoreCase = true)) {
                true -> true
                false -> false
            }
        }
        return false
    }
}