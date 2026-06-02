package com.qianrenni.controller

import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.cache
import com.ucasoft.ktor.simpleCache.cacheOutput
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


fun Routing.test() {
    route("/test") {
        route("/cache") {
            cacheOutput(5.seconds) {
                get("/memory") {
                    call.respond(mapOf("result" to Random.nextInt()))
                }
            }
            get("/redis") {
                val result = cache(
                    args = listOf("default"),
                    keyPrefix = "default",
                    serializer = Int.serializer(),
                ) {
                    Random.nextInt()
                }
                call.respond(ResponseModel.Success(result))
            }
        }

        route("/response-model") {
            get("/success") {
                call.respond(ResponseModel.Success(data = "Success"))
            }
            get("/error") {
                call.respond(ResponseModel.Error(message = "Error"))
            }
            get("/empty") {
                call.respond(ResponseModel.Empty(message = "Empty"))
            }
        }
        get<Articles> { article ->
            // Get all articles ...
            call.respond("List of articles sorted starting from ${article.sort}")
        }
    }

}