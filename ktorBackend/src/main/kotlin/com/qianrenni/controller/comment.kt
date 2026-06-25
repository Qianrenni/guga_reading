package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.plugins.requirePermission
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.commentService
import com.qianrenni.services.generatePermissionCode
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ContentBody(val content: String)

@Serializable
data class LineCommentBody(val line: Int, val content: String)

@Serializable
data class CommentStatusBody(val status: String)

fun Routing.comment() {
    // ==================== 无需认证 - 公开读取 ====================
    route("/comment") {

        // GET /comment/book/{bookId} - 分页获取书评列表
        get("/book/{bookId}") {
            val bookId = call.requirePathParameter("bookId").toInt()
            val page = call.request.queryParameters["page"]?.toInt() ?: 1
            val size = call.request.queryParameters["size"]?.toInt() ?: 20
            val parentId = call.request.queryParameters["parentId"]?.toInt()
            val result = application.commentService.getBookReviews(bookId, page, size, parentId)
            call.respond(ResponseModel.Success(data = result))
        }

        // GET /comment/chapter/{bookId}/{chapterId} - 获取章所有行评论
        get("/chapter/{bookId}/{chapterId}") {
            val bookId = call.requirePathParameter("bookId").toInt()
            val chapterId = call.requirePathParameter("chapterId").toInt()
            val result = application.commentService.getChapterComments(chapterId)
            call.respond(ResponseModel.Success(data = result))
        }
    }

    // ==================== 需认证 - 用户操作 ====================
    authenticate("auth-jwt") {
        route("/comment") {

            // ===== 书评 =====

            // POST /comment/book/{bookId} - 创建/更新书评
            post("/book/{bookId}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                val body = call.receive<ContentBody>()
                val result = application.commentService.createBookReview(
                    userId = user.id,
                    bookId = bookId,
                    content = body.content
                )
                call.respond(ResponseModel.Success(data = result))
            }

            // DELETE /comment/book/{bookId} - 删除自己的书评
            delete("/book/{bookId}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                application.commentService.deleteMyReview(userId = user.id, bookId = bookId)
                call.respond(ResponseModel.Empty(message = "书评已删除"))
            }

            // GET /comment/book/{bookId}/mine - 获取自己的书评
            get("/book/{bookId}/mine") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                val result = application.commentService.getMyBookReview(userId = user.id, bookId = bookId)
                call.respond(ResponseModel.Success(data = result))
            }

            // ===== 章节行评论 =====

            // POST /comment/chapter/{bookId}/{chapterId} - 创建/更新行评论
            post("/chapter/{bookId}/{chapterId}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                val chapterId = call.requirePathParameter("chapterId").toInt()
                val body = call.receive<LineCommentBody>()
                val result = application.commentService.upsertLineComment(
                    userId = user.id,
                    bookId = bookId,
                    chapterId = chapterId,
                    line = body.line,
                    content = body.content
                )
                call.respond(ResponseModel.Success(data = result))
            }

            // DELETE /comment/chapter/{bookId}/{chapterId} - 删除行评论
            delete("/chapter/{bookId}/{chapterId}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                val chapterId = call.requirePathParameter("chapterId").toInt()
                val line = call.requireQueryParameter("line").toInt()
                call.respond(ResponseModel.Empty(message = "评论已删除"))
            }
        }
    }

    // ==================== 管理端 - 需权限 ====================
    authenticate("auth-jwt") {
        route("/admin/comments") {

            // GET /admin/comments - 获取评论列表
            get {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.COMMENT, ActionEnum.READ, ScopeEnum.ALL))
                )
                val page = call.request.queryParameters["page"]?.toInt() ?: 1
                val size = call.request.queryParameters["size"]?.toInt() ?: 20
                val status = call.request.queryParameters["status"]
                val bookId = call.request.queryParameters["bookId"]?.toIntOrNull()
                val keyword = call.request.queryParameters["keyword"]
            }

            // PATCH /admin/comments/{id}/status - 审核评论
            patch("/{id}/status") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.COMMENT, ActionEnum.AUDIT, ScopeEnum.ALL))
                )
                val user = call.getCurrentUser()
                val commentId = call.requirePathParameter("id").toInt()
                val body = call.receive<CommentStatusBody>()
                call.respond(ResponseModel.Empty(message = "审核成功"))
            }

            // DELETE /admin/comments/{id} - 强制删除评论
            delete("/{id}") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.COMMENT, ActionEnum.DELETE, ScopeEnum.ALL))
                )
                val user = call.getCurrentUser()
                val commentId = call.requirePathParameter("id").toInt()
                call.respond(ResponseModel.Empty(message = "评论已删除"))
            }
        }
    }

    // ==================== 作者端 ====================
    authenticate("auth-jwt") {
        route("/author/comments") {

            // GET /author/comments/book/{bookId} - 获取作者某本书的评论
            get("/book/{bookId}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                val page = call.request.queryParameters["page"]?.toInt() ?: 1
                val size = call.request.queryParameters["size"]?.toInt() ?: 20
                val status = call.request.queryParameters["status"]
            }
        }
    }
}
