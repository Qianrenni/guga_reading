package com.qianrenni.controller

import com.qianrenni.config.appConfig
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        staticFiles("/static", File(appConfig.staticDir))
        captcha()
        auth()
        user()
        book()
        userReadingProgress()
        statistics()
        shelf()
        admin()
        author()
        system()
        if (application.appConfig.environment == "dev") {
            test()
        }
    }

}