package com.qianrenni.guga.com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.guga.com.qianrenni.services.bookService
import com.qianrenni.plugins.requirePermission
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.generatePermissionCode
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Routing.book() {
    route("/book") {
        get("/count") {
            val result = application.bookService.getBookCount()
            call.respond(ResponseModel.Success(result))
        }
        get("/category") {
            val result = application.bookService.getCategory()
            call.respond(ResponseModel.Success(result))
        }
        get("/recommend") {
            val query = call.requireQueryParameter("query")
            val result = application.bookService.getRecommendBook(query)
            call.respond(ResponseModel.Success(result))
        }
        get("/search") {
            val query = call.requireQueryParameter("q")
            val result = application.bookService.getSearchBook(query)
            call.respond(ResponseModel.Success(result))
        }
        get("/list") {
            val bookIds = call.request.queryParameters.getAll("bookIds")?.map { it.toInt() } ?: emptyList()
            val result = application.bookService.getBookList(bookIds)
            call.respond(ResponseModel.Success(result))
        }
        get("/toc/{bookId}") {
            val bookId = call.requirePathParameter("bookId").toInt()
            val result = application.bookService.getBookCatalog(bookId)
            call.respond(ResponseModel.Success(result))
        }
        authenticate("auth-jwt") {
            get("/chapter/{chapterId}") {
                call.requirePermission(
                    permissions = listOf(
                        generatePermissionCode(
                            resource = ResourceTypeEnum.BOOK,
                            action = ActionEnum.READ,
                            scope = ScopeEnum.ALL
                        )
                    )
                )
                val chapterId = call.requirePathParameter("chapterId").toInt()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val result = application.bookService.getBookChapter(chapterId, bookId)
                call.respond(ResponseModel.Success(result))
            }
        }
        get("/select") {
            val category = call.requireQueryParameter("category")
            val offset = call.requireQueryParameter("offset").toInt()
            val limit = call.requireQueryParameter("limit").toInt()
            val result = application.bookService.getBookSelect(category, offset, limit)
            call.respond(ResponseModel.Success(result))
        }
        get("/{bookId}") {
            val bookId = call.requirePathParameter("bookId").toInt()
            val result = application.bookService.getBookList(listOf(bookId))
            call.respond(ResponseModel.Success(result.first()))
        }
    }
}