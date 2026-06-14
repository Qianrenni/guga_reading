package com.qianrenni

import com.qianrenni.config.loadConfig
import com.qianrenni.controller.configureRouting
import com.qianrenni.database.configureDatabase
import com.qianrenni.database.configureRedis
import com.qianrenni.database.databaseManager
import com.qianrenni.plugins.*
import com.qianrenni.services.TaskConfig
import com.qianrenni.services.configService
import com.qianrenni.services.registerTaskManager
import com.qianrenni.services.taskManager
import com.qianrenni.workers.aggregateUserReadStatistics
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.time.temporal.ChronoUnit

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
    configureMetrics()
    configService()
    // 4. 注册并启动定时任务
    registerTaskManager()
    configureScheduledTasks()
}

/**
 * 配置定时任务
 */
private fun Application.configureScheduledTasks() {
    taskManager.apply {
        // 每小时整点执行统计聚合（秒 分 时 日 月 周）
        register(
            TaskConfig(
                name = "每小时阅读统计聚合",
                cronExpression = "0 5 * * * ?"
            ) { triggerTime ->
                val hourEnd = triggerTime.toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
                aggregateUserReadStatistics(hourEnd, databaseManager)
            }
        )
        start()
    }
}
fun main(args: Array<String>) {

    embeddedServer(Netty, port = 8000, host = "0.0.0.0", module = Application::main)
        .start(wait = true)
}
