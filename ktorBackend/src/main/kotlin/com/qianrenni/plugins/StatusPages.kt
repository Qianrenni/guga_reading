package com.qianrenni.plugins

import com.qianrenni.schemas.ResponseModel
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

/**
 * 全局异常处理和状态页配置
 * 对应原项目中的 StatusPages.kt 和 Python 项目的 error_handler.py
 */
fun Application.configureStatusPages() {
    install(StatusPages) {

        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ResponseModel.Error(message = cause.message ?: "参数错误"))
        }
        exception<MissingRequestParameterException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, message = ResponseModel.Error(message = "缺少header[${cause.parameterName}]"))
        }
        exception<ContentTransformationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, message = ResponseModel.Error(message = cause.message?:"数据格式错误"))
        }
    }
}
