package com.qianrenni

import com.qianrenni.controller.Articles
import com.qianrenni.controller.captcha
import com.qianrenni.services.cache
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Default(
    val number: Int,
)
fun Application.configureRouting() {
    routing {
        captcha()
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/default") {
            val result = cache(
                args = listOf("default"),
                keyPrefix = "default",
                serializer = Default.serializer(),
            ) {
                Default(number = Random.nextInt())
            }
            call.respond(result)
        }
        get<Articles> { article ->
            // Get all articles ...
            call.respond("List of articles sorted starting from ${article.sort}")
        }
        staticResources("/static", "static")
    }

}