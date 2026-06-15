package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.auditBookService
import com.qianrenni.services.generatePermissionCode
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
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
                val user = call.getCurrentUser()
                val bookIds = call.request.queryParameters.getAll("bookIds")?.map { it.toInt() } ?: emptyList()
                val result = application.auditBookService.getAuditBooks(user.id, bookIds)

                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
            // PATCH /admin/book - 审核书
            patch("/book") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val isPass = call.requireQueryParameter("isPass").toBoolean()
                application.auditBookService.updateBook(userId = user.id, bookId = bookId, isPass = isPass)
                call.respond(
                    ResponseModel.Empty(
                        message = "审核成功"
                    )
                )
            }
            // GET /admin/chapter - 获取审核中的章节
            get("/chapter") {
                val user = call.getCurrentUser()
                val result = application.auditBookService.getAuditChapters(user.id)
                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
            get("/chapterByOrder") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val orders = call.request.queryParameters.getAll("orders")?.map { it.toFloat() } ?: emptyList()
                val result = application.auditBookService.getAuditChaptersByOrder(
                    userId = user.id,
                    bookId = bookId,
                    orders = orders
                )
                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
            // PATCH /admin/chapter - 审核章节
            patch("/chapter") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val chapterId = call.requireQueryParameter("chapterId").toInt()
                val isPass = call.requireQueryParameter("isPass").toBoolean()
                application.auditBookService.updateBookChapter(
                    userId = user.id,
                    bookId = bookId,
                    chapterId = chapterId,
                    isPass = isPass
                )
                call.respond(
                    ResponseModel.Empty(
                        message = "审核成功"
                    )
                )

            }

            // GET /admin/content/chapter - 获取审核中的章节内容
            get("/content/chapter") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val orders = call.request.queryParameters.getAll("orders")?.map { it.toFloat() } ?: emptyList()
                val result = application.auditBookService.getAuditContentChapter(
                    userId = user.id,
                    bookId = bookId,
                    orders = orders
                )
                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }

        }
    }
}
