package com.qianrenni.controller

import com.qianrenni.config.appConfig
import com.qianrenni.guga.com.qianrenni.controller.auth
import com.qianrenni.guga.com.qianrenni.controller.book
import com.qianrenni.guga.com.qianrenni.controller.user
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        staticFiles("/static", File("D:\\project\\guga_reading\\backend\\static"))
        captcha()
        auth()
        user()
        book()
        if (application.appConfig.environment == "dev") {
            test()
        }
    }

}