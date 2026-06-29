package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.requirePermission
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.generatePermissionCode
import com.qianrenni.services.systemService
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.system() {
    route("/system") {
        authenticate("auth-jwt") {
            // GET /system/info - 系统信息
            get("/info") {
                call.requirePermission(
                    listOf(
                        generatePermissionCode(
                            resource = ResourceTypeEnum.PERMISSION,
                            action = ActionEnum.READ,
                            scope = ScopeEnum.ALL
                        )
                    )
                )
                val info = application.systemService.getSystemInfo()
                call.respond(ResponseModel.Success(data = info))
            }

            // GET /system/logs - 列出日志文件
            get("/logs") {
                call.requirePermission(
                    listOf(
                        generatePermissionCode(
                            resource = ResourceTypeEnum.PERMISSION,
                            action = ActionEnum.READ,
                            scope = ScopeEnum.ALL
                        )
                    )
                )
                val files = application.systemService.listLogFiles()
                call.respond(ResponseModel.Success(data = files))
            }

            // GET /system/logs/read - 读取日志内容（分页+级别筛选+正则搜索）
            get("/logs/read") {
                call.requirePermission(
                    listOf(
                        generatePermissionCode(
                            resource = ResourceTypeEnum.PERMISSION,
                            action = ActionEnum.READ,
                            scope = ScopeEnum.ALL
                        )
                    )
                )
                val fileName = call.requireQueryParameter("file")
                val level = call.queryParameters["level"]
                val regex = call.queryParameters["regex"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 100

                val result = application.systemService.readLogFile(
                    fileName = fileName,
                    level = level,
                    regex = regex,
                    page = page,
                    size = size
                )
                call.respond(ResponseModel.Success(data = result))
            }
        }
    }
}