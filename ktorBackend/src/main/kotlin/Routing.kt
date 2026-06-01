package com.qianrenni

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ucasoft.ktor.simpleCache.cacheOutput
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import io.ktor.resources.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import kotlinx.serialization.Serializable
import io.ktor.server.http.content.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        cacheOutput(2.seconds) {
            get("/short") {
                call.respond(Random.nextInt().toString())
            }
        }
        cacheOutput {
            get("/default") {
                call.respond(Random.nextInt().toString())
            }
        }
        get<Articles> { article ->
            // Get all articles ...
            call.respond("List of articles sorted starting from ${article.sort}")
        }
        staticResources("/static", "static")
    }
}