package com.qianrenni.services

import io.ktor.server.application.*


fun Application.configService() {
    registerRightService()
    registerEmailService()
    registerUserService()
    registerCaptchaService()
    registerCacheService()
    registerBookService()
    registerReadProgressService()
    registerStatisticsService()
    registerShelfService()
    registerAdminService()
    registerAuthorBookService()
}