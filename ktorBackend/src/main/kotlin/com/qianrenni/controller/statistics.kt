package com.qianrenni.controller

import com.qianrenni.enums.ReportEnum
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.statisticsService
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class RequestStatisticsReadEvent(
     val bookId: Int,
     val chapterId: Int,
     val eventType: String,
)

fun Routing.statistics() {
    route("/statistic") {
        authenticate("auth-jwt") {
            post("/book-chapter") {
                val user = call.getCurrentUser()
                val body = call.receive<RequestStatisticsReadEvent>()
                application.statisticsService.addUserReadEvent(
                    userId = user.id,
                    bookId = body.bookId,
                    chapterId = body.chapterId,
                    eventType = ReportEnum.fromValue(body.eventType),
                )
                call.respond(ResponseModel.Empty("添加成功"))
            }
        }
    }
}