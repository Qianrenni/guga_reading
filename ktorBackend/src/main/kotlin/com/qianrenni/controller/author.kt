package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.services.generatePermissionCode
import io.ktor.server.auth.*
import io.ktor.server.routing.*


fun Routing.author() {
    route("/author") {
        authenticate("auth-jwt") {
            // GET /author/count - 获取作者数量
            install(PermissionCheck) {
                requiredPermissions = listOf(
                    generatePermissionCode(
                        resource = ResourceTypeEnum.BOOK,
                        action = ActionEnum.CREATE,
                        scope = ScopeEnum.OWN
                    )
                )
            }
            get("/count") {
            }

            // GET /author/book - 获取作者图书列表
            get("/book") {
            }

            // POST /author/book - 创建作者图书
            post("/book") {
            }

            // PATCH /author/book - 更新作者图书
            patch("/book") {
            }

            // DELETE /author/book - 删除作者图书
            delete("/book") {
            }

            // GET /author/chapter - 获取作者图书章节
            get("/chapter") {
            }

            // PATCH /author/chapter - 更新作者图书章节
            patch("/chapter") {
            }

            // DELETE /author/chapter - 删除作者图书章节
            delete("/chapter") {

            }

            // GET /author/book-statistics - 获取作者图书阅读数据
            get("/book-statistics") {

            }

            // GET /author/content - 获取章节内容
            get("/content") {
            }

            // GET /author/draft/chapter - 获取草稿章节
            get("/draft/chapter") {
            }
            route("/status") {
                // PATCH /author/status/chapter - 更新章节状态
                patch("chapter") {
                }

                // PATCH /author/status/book - 更新书籍状态
                patch("/book") {

                }
            }

        }
    }
}
