package com.qianrenni.controller

import com.qianrenni.config.appConfig
import com.qianrenni.guga.com.qianrenni.controller.auth
import com.qianrenni.guga.com.qianrenni.controller.user
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")
        captcha()
        auth()
        user()
        if (application.appConfig.environment != "dev") {
            test()
        }
    }

}