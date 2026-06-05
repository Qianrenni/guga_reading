package com.qianrenni.guga.com.qianrenni.controller

import com.qianrenni.guga.com.qianrenni.services.readProgressService
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestReadingProgressAdd(
    @SerialName("book_id") val bookId: Int,
    @SerialName("last_chapter_id") val lastChapterId: Int,
    @SerialName("last_position") val lastPosition: Int,
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
            delete("/delete/{book_id}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("book_id").toInt()
                application.readProgressService.delete(userId = user.id, bookId = bookId)
                call.respond(ResponseModel.Empty("删除书籍阅读历史成功"))
            }
        }

    }
}