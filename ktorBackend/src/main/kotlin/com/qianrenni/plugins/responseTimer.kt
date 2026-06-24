package com.qianrenni.plugins


import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*

// 1. 定义一个 Key,用于在请求的生命周期中传递“开始时间”
private val RequestStartTime = AttributeKey<Long>("RequestStartTime")

// 2. 创建插件
val ResponseTimePlugin = createApplicationPlugin("ResponseTime") {

    onCallRespond { call, _ ->
        val status = call.response.status()?.value ?: "200"
        val method = call.request.httpMethod.value
        val uri = call.request.uri
        // 3. 打印到控制台 (格式示例: [200] GET /api/captcha - 45ms)
        call.application.log.debug("[{}] {} {}", status, method, uri)
    }
}