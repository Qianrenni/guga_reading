package com.qianrenni.controller

import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.readProgressService
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class RequestReadingProgressAdd(
     val bookId: Int,
     val lastChapterId: Int,
     val lastPosition: Int,
)

fun Routing.userReadingProgress() {
    route("/user_reading_progress") {
        authenticate("auth-jwt") {
            get("/get") {
                val user = call.getCurrentUser()
                val result = application.readProgressService.get(user.id)
                call.respond(ResponseModel.Success(result))
            }
            patch("/add") {
                val user = call.getCurrentUser()
                val body = call.receive<RequestReadingProgressAdd>()
                application.readProgressService.add(
                    user.id,
                    bookId = body.bookId,
                    lastChapterId = body.lastChapterId,
                    lastPosition = body.lastPosition
                )
                call.respond(ResponseModel.Empty("更新书籍阅读历史成功"))
            }
            delete("/delete/{bookId}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("bookId").toInt()
                application.readProgressService.delete(userId = user.id, bookId = bookId)
                call.respond(ResponseModel.Empty("删除书籍阅读历史成功"))
            }
        }

    }
}