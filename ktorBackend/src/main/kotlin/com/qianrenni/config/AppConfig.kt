package com.qianrenni.config

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*

/**
 * 应用配置管理类
 * 对应 Python 项目中的 app/core/config.py
 */
data class AppConfig(
    // 运行环境
    val environment: String = "dev",
    val allowHost: String = "localhost",
    // 数据库配置
    val mysqlDsn: String,
    val dbPoolSize: Int = 20,
    val dbMaxOverflow: Int = 10,
    val dbPoolRecycle: Int = 3600,

    // Redis 配置
    val redisUrl: String,
    val redisPoolSize: Int = 50,
    val redisWaitTimeout: Int = 3,

    // JWT 配置
    val secretKey: String,
    val audience: String = "",
    val issuer: String = "",
    val accessTokenExpire: Int = 43200,      // 12小时(秒)
    val refreshTokenExpire: Int = 604800,     // 7天(秒)
    val emailVerifyExpire: Int = 300,         // 5分钟(秒)
    val captchaExpire: Int = 120,             // 2分钟(秒)
    val permissionBitLength: Int = 32,

    // 缓存配置
    val bookCacheExpire: Int = 1800,          // 书籍缓存30分钟
    val permissionCacheExpire: Int = 604800,  // 权限缓存7天

    // 限流配置
    val ipLimitEnable: Boolean = true,
    val ipLimitWindow: Int = 60,              // 60秒窗口
    val ipLimitCount: Int = 30,               // 最多30次请求

    // 存储配置
    val bookShardCount: Int = 64,
    val staticDir: String = "static",
    val contentDir: String = "store",
    val chapterEncoding: String = "utf-8",

    // 服务器配置
    val serverUrl: String = "http://localhost:8080",

    // 邮箱配置
    val smtpServer: String = "smtp.qq.com",
    val smtpPort: Int = 465,
    val emailAccount: String = "",
    val emailCode: String = "",

    // 跨域配置
    val allowOrigins: String = "*"
) {
    companion object {
        fun fromConfig(config: ApplicationConfig): AppConfig {
            return AppConfig(
                environment = config.property("app.env").getString(),
                allowHost = config.property("app.server.allowHost").getString(),
                contentDir = config.property("app.contentDir").getString(),
                staticDir = config.property("app.staticDir").getString(),
                serverUrl = config.property("app.server.url").getString(),
                mysqlDsn = config.property("app.database.mysql-dsn").getString(),
                redisUrl = config.property("app.cache.redis-url").getString(),
                secretKey = config.property("app.security.secret-key").getString(),
                smtpServer = config.property("app.email.smtp-server").getString(),
                smtpPort = config.property("app.email.smtp-port").getString().toInt(),
                emailAccount = config.property("app.email.account").getString(),
                emailCode = config.property("app.email.code").getString(),
            )
        }
    }
}

private val AppConfigKey = AttributeKey<AppConfig>("AppConfig")

// Application 扩展属性用于存储配置
val Application.appConfig: AppConfig
    get() = attributes[AppConfigKey]

fun Application.loadConfig() {
    val config = HoconApplicationConfig(ConfigFactory.load())
    val appConfig = AppConfig.fromConfig(config)
    attributes.put(AppConfigKey, appConfig)
    log.debug(appConfig.toString())
}