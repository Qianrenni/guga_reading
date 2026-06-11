
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.qianrenni"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.qianrenni.ApplicationKt"
}
ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("guga_backend")
        imageTag.set("latest")
        portMappings.set(
            listOf(
                io.ktor.plugin.features.DockerPortMapping(
                    8000,
                    8000,
                    io.ktor.plugin.features.DockerPortMappingProtocol.TCP
                )
            )
        )
    }
}
kotlin {
    jvmToolchain(21)
}
dependencies {
    // Ktor 核心和插件
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.autoHeadResponse)
    implementation(ktorLibs.server.cachingHeaders)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.conditionalHeaders)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.csrf)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.partialContent)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.swagger)
    implementation(libs.flaxoos.ktor.server.rateLimiting)
    implementation(libs.logback.classic)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.ucasoft.ktorSimpleCache)
    implementation(libs.ucasoft.ktorSimpleMemoryCache)
    implementation(ktorLibs.server.contentNegotiation)
    // 数据库相关 - Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikaricp)
    implementation(libs.mysql.connector)

    // Redis 客户端
    implementation(libs.lettuce.core)

    // JWT 认证
    implementation(libs.jwt.api)

    // 密码哈希 (Bcrypt)
    implementation(libs.bcrypt)
    // 邮件发送
    implementation("com.sun.mail:jakarta.mail:2.0.2")
    implementation("pro.fessional:kaptcha:2.3.3")
    // Ktor Micrometer 插件
    implementation(ktorLibs.server.metrics.micrometer)
    // Micrometer Prometheus 注册表 (用于暴露 /metrics 接口给 Prometheus 抓取)
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.2")
    // msgpack 序列化
    implementation("org.msgpack:msgpack-core:0.9.8")
    implementation("org.lz4:lz4-java:1.8.0")
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
