package com.qianrenni.controller

import com.qianrenni.services.CaptchaService
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Routing.captcha() {
    route("/captcha") {
        get("/get") {
            val captchaService = CaptchaService(call.application)
            val (captchaId, image) = captchaService.getCaptcha()
            call.response.header("x-captcha-id", captchaId)
            call.respond(image)
        }
    }
}