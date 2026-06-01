package com.qianrenni.guga.com.qianrenni.controller

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.request.requireHeader
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class  RequestTokenGet(
    @SerialName("username") val userName: String,
    @SerialName("password") val password: String,
    @SerialName("captcha") val captcha: String
)

fun Route.routeAuth() {
    route("/token") {
        post("/get") {
            val xCaptchaId = call.requireHeader("x-captcha-id")
            val requestTokenGet=call.receive<RequestTokenGet>()
        }
    }
}