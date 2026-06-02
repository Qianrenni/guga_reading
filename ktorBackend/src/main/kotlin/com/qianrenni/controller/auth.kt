package com.qianrenni.guga.com.qianrenni.controller

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qianrenni.config.appConfig
import com.qianrenni.guga.com.qianrenni.models.domain.FullUser
import com.qianrenni.services.userService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class  RequestTokenGet(
    @SerialName("username") val userName: String,
    @SerialName("password") val password: String,
    @SerialName("captcha") val captcha: String
)
@Serializable
data class ResponseTokenGet(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user") val user: FullUser,
)

fun Routing.auth() {
    route("/token") {
        post("/get") {
            val xCaptchaId = call.requireHeader("x-captcha-id")
            val requestTokenGet=call.receive<RequestTokenGet>()
            val user=application.userService.login(xCaptchaId, requestTokenGet)
            val token=JWT.create()
                .withAudience(application.appConfig.audience)
                .withIssuer(application.appConfig.issuer)
                .withSubject(
                    Json.encodeToString(user)
                )
                .withExpiresAt(Date(System.currentTimeMillis() + application.appConfig.accessTokenExpire))
                .sign(Algorithm.HMAC256(application.appConfig.secretKey))
            call.respond(
                hashMapOf("access_token" to token)
            )
        }
    }
}