package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.generatePermissionCode
import com.qianrenni.services.systemService
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.system() {
    route("/system") {
        authenticate("auth-jwt") {
            // GET /admin/systemInfo - 系统信息
            install(PermissionCheck) {
                requiredPermissions = listOf(
                    generatePermissionCode(
                        resource = ResourceTypeEnum.PERMISSION,
                        action = ActionEnum.READ,
                        scope = ScopeEnum.ALL
                    )
                )
            }
            get("/info") {
                val info = application.systemService.getSystemInfo()
                call.respond(ResponseModel.Success(data = info))
            }
        }

    }

}