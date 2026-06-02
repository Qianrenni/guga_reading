package com.qianrenni.guga.com.qianrenni.services

import com.qianrenni.services.registerCaptchaService
import com.qianrenni.services.registerEmailService
import com.qianrenni.services.registerRightService
import com.qianrenni.services.registerUserService
import io.ktor.server.application.*


fun Application.configService() {
    registerRightService()
    registerEmailService()
    registerUserService()
    registerCaptchaService()
}