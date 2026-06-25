package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.models.tables.*
import com.qianrenni.schemas.PageResult
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.*

class CommentService(private val application: Application) {
    companion object {
        val attributeKey = AttributeKey<CommentService>("CommentService")
    }

    private val bookStoreBaseDir: String
        get() = application.appConfig.contentDir + "/comment/book"

    private val chapterStoreBaseDir: String
        get() = application.appConfig.contentDir + "/comment/chapter"


    /**
     * 创建/更新书评（UPSERT：每用户每书一条）
     */
    suspend fun createBookReview(userId: Int, bookId: Int, content: String) {
        require(content.isNotBlank()) { "评论内容不能为空" }
        require(content.length <= 300) { "评论内容不能超过300字" }

        // 检查书籍是否存在
        application.databaseManager.suspendedTransaction(readOnly = true) {
            val bookExists = BookTable.selectAll().where { BookTable.id eq bookId }
                .firstOrNull()
            require(bookExists != null) { "书籍不存在" }
        }
        val commentId = application.databaseManager.suspendedTransaction {
            // 插入新书评
            BookCommentTable.insertAndGetId {
                it[BookCommentTable.bookId] = bookId
                it[BookCommentTable.userId] = userId
                it[BookCommentTable.status] = BookStatus.PUBLISHED
            }.value
        }
        // 存储评论内容到 ChapterStoreService
        ChapterStoreService(
            name = "$bookId",
            baseDir = bookStoreBaseDir
        ).use { store ->
            store.update(commentId, content)
        }
    }

    /**
     * 分页获取书评
     */
    suspend fun getBookReviews(bookId: Int, page: Int, size: Int, parentId: Int?): PageResult<BookComment> {
        val offset = (page - 1) * size
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            val total = BookCommentTable.selectAll().where {
                (BookCommentTable.bookId eq bookId) and
                        (BookCommentTable.status eq BookStatus.PUBLISHED)
            }.also { query ->
                parentId?.let {
                    query.andWhere { BookCommentTable.parentId eq it }
                }
            }.count()
            val chapterStoreService = ChapterStoreService(
                name = bookId.toString(),
                baseDir = chapterStoreBaseDir
            )
            val rows = BookCommentTable
                .innerJoin(UserTable, { BookCommentTable.userId }, { UserTable.id })
                .select(
                    BookCommentTable.id,
                    BookCommentTable.bookId,
                    BookCommentTable.userId,
                    UserTable.userName,
                    UserTable.avatar,
                    BookCommentTable.status,
                    BookCommentTable.createdAt,
                    BookCommentTable.updatedAt,
                    BookCommentTable.parentId
                )
                .where {
                    (BookCommentTable.bookId eq bookId) and
                            (BookCommentTable.status eq BookStatus.PUBLISHED)
                }
                .orderBy(BookCommentTable.createdAt, SortOrder.DESC)
                .offset(offset.toLong())
                .limit(size)
                .map {
                    val content = chapterStoreService.readChapter(it[BookCommentTable.id].value)
                    it.toBookComment(
                        it[UserTable.userName],
                        application.userService.getUserAvatar(it[BookCommentTable.userId]),
                        content
                    )
                }
            chapterStoreService.close()
            PageResult(total = total.toInt(), items = rows, page = page, size = size)
        }
    }

    /**
     * 获取当前用户的书评
     */
    suspend fun getMyBookReview(userId: Int, bookId: Int): BookComment? {
        val contentStore = ChapterStoreService(
            name = bookId.toString(),
            baseDir = chapterStoreBaseDir
        )
        val result = application.databaseManager.suspendedTransaction(readOnly = true) {
            BookCommentTable
                .innerJoin(UserTable, { BookCommentTable.userId }, { UserTable.id })
                .select(
                    BookCommentTable.id,
                    BookCommentTable.bookId,
                    BookCommentTable.userId,
                    UserTable.userName,
                    UserTable.avatar,
                    BookCommentTable.status,
                    BookCommentTable.createdAt,
                    BookCommentTable.updatedAt,
                    BookCommentTable.parentId
                )
                .where {
                    (BookCommentTable.bookId eq bookId) and
                            (BookCommentTable.userId eq userId)
                }
                .map {
                    val content = contentStore.readChapter(it[BookCommentTable.id].value)
                    it.toBookComment(
                        it[UserTable.userName],
                        application.userService.getUserAvatar(it[BookCommentTable.userId]),
                        content
                    )
                }
        }
        return result.firstOrNull()
    }

    /**
     * 删除自己的书评（软删除）
     */
    suspend fun deleteMyReview(userId: Int, bookId: Int) {
        application.databaseManager.suspendedTransaction {
            BookCommentTable.update({
                (BookCommentTable.bookId eq bookId) and
                        (BookCommentTable.userId eq userId)
            }) {
                it[BookCommentTable.status] = BookStatus.DELETED
            }
        }
    }

    /**
     * 创建/更新章节行评论（UPSERT，基于 line 唯一）
     */
    suspend fun upsertLineComment(userId: Int, bookId: Int, chapterId: Int, line: Int, content: String) {
        require(content.isNotBlank()) { "评论内容不能为空" }
        require(content.length <= 2000) { "评论内容不能超过2000字" }
        require(line >= 0) { "非法的行号" }

        val targetId = application.databaseManager.suspendedTransaction {
            BookChapterCommentTable.insertAndGetId {
                it[BookChapterCommentTable.chapterId] = chapterId
                it[BookChapterCommentTable.line] = line
                it[BookChapterCommentTable.userId] = userId
                it[BookChapterCommentTable.status] = BookStatus.PUBLISHED
            }.value
        }
        // 存储评论内容
        ChapterStoreService(
            name = "$bookId",
            baseDir = chapterStoreBaseDir
        ).use { store ->
            store.update(targetId, content)
        }
    }

    /**
     * 删除章节行评论
     */
    suspend fun deleteLineComment(id: Int) {
        application.databaseManager.suspendedTransaction {
            BookCommentTable.update({
                BookChapterCommentTable.id eq id
            }) {
                it[BookChapterCommentTable.status] = BookStatus.DELETED
            }
        }
    }

    /**
     * 获取某章所有有评论的行 Map<line, BookComment>
     */
    suspend fun getChapterComments(chapterId: Int): Map<Int, List<BookChapterComment>> {
        val store = ChapterStoreService(name = "$chapterId", baseDir = chapterStoreBaseDir)
        val rows = application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterCommentTable
                .innerJoin(UserTable, { BookChapterCommentTable.userId }, { UserTable.id })
                .select(
                    BookChapterCommentTable.id,
                    BookChapterCommentTable.chapterId,
                    BookChapterCommentTable.userId,
                    UserTable.userName,
                    BookChapterCommentTable.status,
                    BookChapterCommentTable.createdAt,
                    BookChapterCommentTable.updatedAt,
                    BookChapterCommentTable.parentId,
                    BookChapterCommentTable.line
                )
                .where {
                    (BookChapterCommentTable.chapterId eq chapterId) and
                            (BookChapterCommentTable.status eq BookStatus.PUBLISHED)
                }
                .map {
                    val content = store.readChapter(it[BookChapterCommentTable.id].value)
                    it.toBookChapterComment(
                        userAvatar = application.userService.getUserAvatar(it[BookChapterCommentTable.userId]),
                        userName = it[UserTable.userName],
                        content = content
                    )

                }
        }
        store.close()
        return rows.groupBy { it.line }
    }
}

val Application.commentService: CommentService
    get() = attributes[CommentService.attributeKey]

fun Application.registerCommentService() {
    attributes[CommentService.attributeKey] = CommentService(this)
}

