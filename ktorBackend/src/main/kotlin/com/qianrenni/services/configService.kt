package com.qianrenni.guga.com.qianrenni.services

import com.qianrenni.services.registerCaptchaService
import com.qianrenni.services.registerEmailService
import com.qianrenni.services.registerUserService
import io.ktor.server.application.Application


fun Application.configService() {
    registerEmailService()
    registerUserService()
    registerCaptchaService()
}