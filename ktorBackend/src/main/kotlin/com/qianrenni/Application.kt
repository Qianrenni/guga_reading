package com.qianrenni

import com.qianrenni.config.loadConfig
import com.qianrenni.database.configureDatabase
import com.qianrenni.database.configureRedis
import com.qianrenni.plugins.*
import io.ktor.server.application.*

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
}
