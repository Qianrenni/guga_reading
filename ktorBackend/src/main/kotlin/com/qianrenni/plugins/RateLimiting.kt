package com.qianrenni.plugins

import io.github.flaxoos.ktor.server.plugins.ratelimiter.RateLimiting
import io.ktor.server.application.*

fun Application.configureRateLimiting() {
    install(RateLimiting)
}
