package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.authorBookService
import com.qianrenni.services.generatePermissionCode
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.io.path.createTempFile

@Serializable
data class RequestUpdateBookChapter(
    val bookId: Int,
    val title: String,
    val order: Float,
    val content: String
)
fun Routing.author() {
    route("/author") {
        authenticate("auth-jwt") {
            // GET /author/count - 获取作者数量
            install(PermissionCheck) {
                requiredPermissions = listOf(
                    generatePermissionCode(
                        resource = ResourceTypeEnum.BOOK, action = ActionEnum.CREATE, scope = ScopeEnum.OWN
                    )
                )
            }
            get("/count") {
                val result = application.authorBookService.getAuthorCount()
                call.respond(ResponseModel.Success(result))
            }

            // GET /author/book - 获取作者图书列表
            get("/book") {
                val user = call.getCurrentUser()
                val bookIds = call.request.queryParameters.getAll("id")?.map { it.toInt() } ?: emptyList()
                val result = application.authorBookService.getBook(userId = user.id, bookIds = bookIds)
                call.respond(ResponseModel.Success(result))
            }

            // POST /author/book - 创建作者图书
            post("/book") {
                val user = call.getCurrentUser()
                val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024)
                var bookName: String? = null
                var author: String? = null
                var description: String? = null
                var category: String? = null
                var tags: String? = null
                var cover: File? = null
                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "name" -> {
                                    bookName = part.value
                                }

                                "author" -> {
                                    author = part.value
                                }

                                "description" -> {
                                    description = part.value
                                }

                                "category" -> {
                                    category = part.value
                                }

                                "tags" -> {
                                    tags = part.value
                                }
                            }
                        }

                        is PartData.FileItem -> {
                            val fileName = part.originalFileName
                            fileName?.let {
                                cover = createTempFile("cover_${user.id}", it.split(".").last()).toFile()
                            }
                            cover?.let {
                                part.provider().copyAndClose(it.writeChannel())
                            }
                        }

                        else -> {}
                    }
                    part.release()
                }
                //全部不为空才创建
                if (
                    bookName != null
                    && author != null
                    && description != null
                    && category != null
                    && tags != null
                    && cover != null
                ) {
                    application.authorBookService.createBook(
                        userId = user.id,
                        bookName = bookName,
                        author = author,
                        tags = tags,
                        description = description,
                        category = category,
                        coverFile = cover
                    )
                }
                call.respond(ResponseModel.Empty(message = "创建成功"))
            }

            // PATCH /author/book - 更新作者图书
            patch("/book") {
                val user = call.getCurrentUser()
                val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024)
                var bookName: String? = null
                var author: String? = null
                var description: String? = null
                var category: String? = null
                var tags: String? = null
                var cover: File? = null
                var bookId: Int? = null
                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "id" -> {
                                    bookId = part.value.toInt()
                                }

                                "name" -> {
                                    bookName = part.value
                                }

                                "author" -> {
                                    author = part.value
                                }

                                "description" -> {
                                    description = part.value
                                }

                                "category" -> {
                                    category = part.value
                                }

                                "tags" -> {
                                    tags = part.value
                                }
                            }
                        }

                        is PartData.FileItem -> {
                            val fileName = part.originalFileName
                            fileName?.let {
                                cover = createTempFile("cover_${user.id}", it.split(".").last()).toFile()
                            }
                            cover?.let {
                                part.provider().copyAndClose(it.writeChannel())
                            }
                        }

                        else -> {}
                    }
                    part.release()
                }
                //全部不为空才创建
                if (
                    bookId != null
                    && bookName != null
                    && author != null
                    && description != null
                    && category != null
                    && tags != null
                ) {
                    application.authorBookService.updateBook(
                        userId = user.id,
                        bookId = bookId,
                        bookName = bookName,
                        author = author,
                        tags = tags,
                        description = description,
                        category = category,
                        coverFile = cover
                    )
                }
                call.respond(ResponseModel.Empty(message = "更新成功"))
            }

            // DELETE /author/book - 删除作者图书
            delete("/book") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("id").toInt()
                application.authorBookService.deleteBook(userId = user.id, bookId = bookId)
                call.respond(ResponseModel.Empty(message = "删除成功"))
            }

            // GET /author/chapter - 获取作者图书章节
            get("/chapter") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val chapterId = call.request.queryParameters.getAll("chapterId")?.map { it.toInt() }
                val result = application.authorBookService.getBookChapter(
                    userId = user.id,
                    bookId = bookId,
                    chapterId = chapterId
                )
                call.respond(ResponseModel.Success(result))
            }

            // PATCH /author/chapter - 更新作者图书章节
            patch("/chapter") {
                val user = call.getCurrentUser()
                val requestUpdateBookChapter = call.receive<RequestUpdateBookChapter>()
                application.authorBookService.updateBookChapter(
                    userId = user.id,
                    requestUpdateBookChapter = requestUpdateBookChapter
                )
                call.respond(ResponseModel.Empty(message = "更新成功"))
            }

            // DELETE /author/chapter - 删除作者图书章节
            delete("/chapter") {
                val user = call.getCurrentUser()
                val chapterId = call.requireQueryParameter("chapterId").toInt()
                val bookId = call.requireQueryParameter("bookId").toInt()
                application.authorBookService.deleteBookChapter(
                    userId = user.id,
                    chapterId = chapterId,
                    bookId = bookId
                )
                call.respond(ResponseModel.Empty(message = "删除成功"))
            }

            // GET /author/book-statistics - 获取作者图书阅读数据
            get("/book-statistics") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val result = application.authorBookService.getBookReadStatistic(userId = user.id, bookId = bookId)
                call.respond(ResponseModel.Success(result))
            }

            // GET /author/content - 获取章节内容
            get("/content") {
                val user = call.getCurrentUser()
                val chapterIds = call.request.queryParameters.getAll("chapterId")?.map { it.toInt() } ?: emptyList()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val result = application.authorBookService.getChapterContent(
                    userId = user.id,
                    chapterIds = chapterIds,
                    bookId = bookId
                )
                call.respond(ResponseModel.Success(result))
            }

            // GET /author/draft/chapter - 获取草稿章节
            route("/draft") {
                get("/chapter") {
                    val user = call.getCurrentUser()
                    val result = application.authorBookService.getDraftChapter(userId = user.id)
                    call.respond(ResponseModel.Success(result))
                }
            }
            route("/status") {
                // PATCH /author/status/chapter - 更新章节状态
                patch("chapter") {
                    val user = call.getCurrentUser()
                    val bookId = call.requireQueryParameter("bookId").toInt()
                    val chapterId = call.requireQueryParameter("chapterId").toInt()
                    application.authorBookService.updateStatusChapter(
                        userId = user.id,
                        bookId = bookId,
                        chapterId = chapterId
                    )
                    call.respond(ResponseModel.Empty(message = "更新成功"))
                }

                // PATCH /author/status/book - 更新书籍状态
                patch("/book") {
                    val user = call.getCurrentUser()
                    val bookId = call.requireQueryParameter("bookId").toInt()
                    application.authorBookService.updateStatusBook(userId = user.id, bookId = bookId)
                    call.respond(ResponseModel.Empty(message = "更新成功"))
                }
            }

        }
    }
}
