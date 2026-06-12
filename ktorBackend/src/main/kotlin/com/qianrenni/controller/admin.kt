package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.services.generatePermissionCode
import io.ktor.server.auth.*
import io.ktor.server.routing.*


fun Routing.admin() {
    route("/admin") {

        authenticate("auth-jwt") {
            // GET /admin/book - 获取审核中的书
            install(PermissionCheck) {
                requiredPermissions = listOf(
                    generatePermissionCode(
                        resource = ResourceTypeEnum.BOOK,
                        action = ActionEnum.AUDIT,
                        scope = ScopeEnum.ALL
                    )
                )
            }
            get("/book") {
            }
            // PATCH /admin/book - 审核书
            patch("/book") {
            }
            // GET /admin/chapter - 获取审核中的章节
            get("/chapter") {
            }

            // PATCH /admin/chapter - 审核章节
            patch("/chapter") {
            }

            // GET /admin/content/chapter - 获取审核中的章节内容
            get("/content/chapter") {
            }

            // GET /admin/systemInfo - 系统信息(暂不实现)
            get("/systemInfo") {
            }
        }
    }
}
