package com.qianrenni

import com.qianrenni.config.loadConfig
import com.qianrenni.controller.configureRouting
import com.qianrenni.database.configureDatabase
import com.qianrenni.database.configureRedis
import com.qianrenni.guga.com.qianrenni.plugins.configureMetrics
import com.qianrenni.guga.com.qianrenni.services.configService
import com.qianrenni.plugins.*
import com.qianrenni.services.configureChapterStore
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

/**
 * 应用主入口
 * 配置依赖注入和应用生命周期
 */
fun Application.main() {
    // 1. 加载配置
    loadConfig()
    // 2. 初始化数据库
    configureDatabase()
    // 3. 初始化 Redis
    configureRedis()
    configureHTTP()
    configureRateLimiting()
    configureResources()
    configureSecurity()
    configureStatusPages()
    configureRouting()
    configureChapterStore()
    configureMetrics()
    configService()
}
fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8000, host = "0.0.0.0", module = Application::main)
        .start(wait = true)
}
