package com.qianrenni.guga.com.qianrenni.controller

import com.qianrenni.guga.com.qianrenni.services.shelfService
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestShelfAdd(
    @SerialName("book_id") val bookId: Int,
)

fun Routing.shelf() {
    route("/shelf") {
        authenticate("auth-jwt") {
            get("/get") {
                val user = call.getCurrentUser()
                val result = application.shelfService.get(user.id)
                call.respond(ResponseModel.Success(result))
            }
            post("/add") {
                val user = call.getCurrentUser()
                val body = call.receive<RequestShelfAdd>()
                application.shelfService.add(bookId = body.bookId, userId = user.id)
                call.respond(ResponseModel.Empty("添加书籍到书架成功"))

            }
            delete("/delete/{book_id}") {
                val user = call.getCurrentUser()
                val bookId = call.requirePathParameter("book_id").toInt()
                application.shelfService.delete(bookId = bookId, userId = user.id)
                call.respond(ResponseModel.Empty("移除书架书籍成功"))
            }
        }
    }
}