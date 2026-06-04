package com.qianrenni.guga.com.qianrenni.plugins


import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*

// 1. 定义一个 Key,用于在请求的生命周期中传递“开始时间”
private val RequestStartTime = AttributeKey<Long>("RequestStartTime")

// 2. 创建插件
val ResponseTimePlugin = createApplicationPlugin("ResponseTime") {

    // 当请求刚进入时,记录当前时间戳
    onCall { call ->
        call.attributes.put(RequestStartTime, System.currentTimeMillis())
    }

    // 当响应准备发送出去时,计算差值并打印
    onCallRespond { call, _ ->
        val startTime = call.attributes.getOrNull(RequestStartTime)
        if (startTime != null) {
            val duration = System.currentTimeMillis() - startTime

            val status = call.response.status()?.value ?: "-"
            val method = call.request.httpMethod.value
            val uri = call.request.uri

            // 3. 打印到控制台 (格式示例: [200] GET /api/captcha - 45ms)
            call.application.log.info("[$status] $method $uri -  ${duration}ms")

            // (可选) 如果你想让前端也能看到这个耗时,可以取消下面这行的注释
            // call.response.headers.append("X-Process-Time", "${duration}ms")
        }
    }
}