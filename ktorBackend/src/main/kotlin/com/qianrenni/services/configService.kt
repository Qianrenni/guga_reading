package com.qianrenni.guga.com.qianrenni.services

import com.qianrenni.services.*
import io.ktor.server.application.*


fun Application.configService() {
    registerRightService()
    registerEmailService()
    registerUserService()
    registerCaptchaService()
    registerCacheService()
}