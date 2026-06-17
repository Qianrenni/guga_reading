package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.controller.RequestUpdateBookChapter
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.io.File
import kotlin.io.path.Path

class AuthorService(private val application: Application) {
    companion object {
        val attributeKey = AttributeKey<AuthorService>("AuthorBookService")
    }
    suspend fun checkAuthor(userId: Int, bookId: Int) {
        application.databaseManager.suspendedTransaction(readOnly = true) {
            require(
                AuthorBookTable.selectAll().where {
                    (AuthorBookTable.userId eq userId) and (AuthorBookTable.bookId eq bookId)
                }.count() > 0
            )
        }
    }

    suspend fun getAuthorCount(): Int {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            AuthorTable.selectAll().count().toInt()
        }
    }

    suspend fun getBook(userId: Int, bookIds: List<Int>): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable
                .innerJoin(AuthorBookTable, { BookTable.id }, { AuthorBookTable.bookId })
                .selectAll()
                .where {
                    if (bookIds.isEmpty()) {
                        AuthorBookTable.userId eq userId
                    } else {
                        (AuthorBookTable.userId eq userId) and (AuthorBookTable.bookId inList bookIds)
                    }
                }
                .map { it.toBook(application.appConfig.serverUrl) }
        }
    }

    suspend fun createBook(
        userId: Int,
        bookName: String,
        author: String,
        tags: String,
        description: String,
        category: String,
        coverFile: File
    ) {
        var bookId: EntityID<Int>? = null
        application.databaseManager.suspendedTransaction {
            bookId = BookTable.insertAndGetId {
                it[BookTable.name] = bookName
                it[BookTable.author] = author
                it[BookTable.tags] = tags
                it[BookTable.description] = description
                it[BookTable.category] = category
                it[BookTable.status] = BookStatus.PENDING
            }
            AuthorBookTable.insert {
                it[AuthorBookTable.userId] = userId
                it[AuthorBookTable.bookId] = bookId.value
            }
        }
        bookId?.let {
            withContext(Dispatchers.IO) {
                coverFile.copyTo(
                    Path(application.appConfig.staticDir + "/book/${it.value}/cover.webp").toFile(),
                    overwrite = false
                )
                coverFile.deleteOnExit()
            }
        }

    }

    suspend fun updateBook(
        userId: Int,
        bookId: Int,
        bookName: String,
        author: String,
        tags: String,
        description: String,
        category: String,
        coverFile: File?
    ) {
        checkAuthor(userId, bookId)
        var targetId: Int? = null
        application.databaseManager.suspendedTransaction {
            val alreadyBook = BookTable.selectAll().where { BookTable.id eq bookId }.firstOrNull()
                ?.toBook(application.appConfig.serverUrl)
            when (alreadyBook) {
                null -> {}
                else -> {
                    when (alreadyBook.status) {
                        BookStatus.PENDING, BookStatus.REJECTED -> {
                            BookTable.update({ BookTable.id eq alreadyBook.id }) {
                                it[BookTable.name] = bookName
                                it[BookTable.author] = author
                                it[BookTable.tags] = tags
                                it[BookTable.description] = description
                                it[BookTable.category] = category
                                it[BookTable.status] = BookStatus.PENDING
                            }
                            targetId = alreadyBook.id
                        }

                        BookStatus.PUBLISHED -> {
                            targetId = BookTable.insertAndGetId {
                                it[BookTable.id] = (-bookId)
                                it[BookTable.name] = bookName
                                it[BookTable.author] = author
                                it[BookTable.tags] = tags
                                it[BookTable.wordsCount] = alreadyBook.wordsCount
                                it[BookTable.totalChapter] = alreadyBook.totalChapter
                                it[BookTable.description] = description
                                it[BookTable.category] = category
                                it[BookTable.status] = BookStatus.PENDING
                            }.value
                            AuthorBookTable.insert {
                                it[AuthorBookTable.userId] = userId
                                it[AuthorBookTable.bookId] = targetId
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
        withContext(Dispatchers.IO) {
            when (coverFile) {
                null -> {
                    if (targetId != bookId) {
                        File(application.appConfig.staticDir + "/book/${bookId}/cover.webp").copyTo(
                            Path(application.appConfig.staticDir + "/book/${targetId}/cover.webp").toFile(),
                            overwrite = true
                        )
                    }
                }

                else -> {
                    coverFile.copyTo(
                        Path(application.appConfig.staticDir + "/book/${targetId}/cover.webp").toFile(),
                        overwrite = true
                    )
                    coverFile.deleteOnExit()
                }
            }

        }
    }

    suspend fun deleteBook(
        userId: Int,
        bookId: Int
    ) {
        checkAuthor(userId, bookId)
        var deleteCount = 0
        application.databaseManager.suspendedTransaction {
            val book = BookTable
                .innerJoin(AuthorBookTable, { BookTable.id }, { AuthorBookTable.bookId })
                .selectAll()
                .where { (BookTable.id eq bookId) and (AuthorBookTable.userId eq userId) and (BookTable.status eq BookStatus.PENDING) }
                .firstOrNull()
                ?.toBook(application.appConfig.serverUrl)
            book?.let {
                AuditBookTable.deleteWhere {
                    AuditBookTable.bookId eq book.id
                }
                AuthorBookTable.deleteWhere {
                    (AuthorBookTable.userId eq userId) and (AuthorBookTable.bookId eq book.id)
                }
                deleteCount = BookTable.deleteWhere {
                    BookTable.id eq book.id
                }
            }
        }
        if (deleteCount > 0) {
            val bookDir = Path(application.appConfig.staticDir + "/book/${bookId}")
            bookDir.toFile().deleteRecursively()
        }
    }
    suspend fun getBookChapter(userId: Int, bookId: Int, chapterId: List<Int>?): List<BookCatalogItem> {
        checkAuthor(userId, bookId)
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable.selectAll().where {
                if (chapterId == null) {
                    (BookChapterTable.bookId eq bookId)
                } else {
                    (BookChapterTable.bookId eq bookId) and (BookChapterTable.id inList chapterId)
                }
            }.map {
                it.toBookCatalogItem()
            }
        }
    }

    suspend fun updateBookChapter(requestUpdateBookChapter: RequestUpdateBookChapter, userId: Int) {
        checkAuthor(userId, requestUpdateBookChapter.bookId)
        var targetId: Int? = null
        application.databaseManager.suspendedTransaction {
            val bookCatalogItem = BookChapterTable.selectAll().where {
                (BookChapterTable.bookId eq requestUpdateBookChapter.bookId) and (BookChapterTable.order eq requestUpdateBookChapter.order)
            }.firstOrNull()?.toBookCatalogItem()
            when (bookCatalogItem) {
                null -> {
                    targetId = BookChapterTable.insertAndGetId {
                        it[BookChapterTable.bookId] = requestUpdateBookChapter.bookId
                        it[BookChapterTable.title] = requestUpdateBookChapter.title
                        it[BookChapterTable.order] = requestUpdateBookChapter.order
                        it[BookChapterTable.wordCount] = requestUpdateBookChapter.content.length
                    }.value
                }

                else -> {
                    BookChapterTable.update({
                        (BookChapterTable.bookId eq requestUpdateBookChapter.bookId) and (BookChapterTable.order eq requestUpdateBookChapter.order)
                    }) {
                        it[BookChapterTable.title] = requestUpdateBookChapter.title
                        it[BookChapterTable.wordCount] = requestUpdateBookChapter.content.length
                    }
                    targetId = bookCatalogItem.id
                }
            }
        }
        val chapterStore =
            ChapterStoreService(
                bookId = requestUpdateBookChapter.bookId,
                baseDir = application.appConfig.contentDir + "/book"
            )
        chapterStore.use {
            targetId?.let { chapterStore.update(targetId, requestUpdateBookChapter.content) }
        }
    }
    suspend fun deleteBookChapter(
        userId: Int,
        bookId: Int,
        chapterId: Int
    ) {
        checkAuthor(userId, bookId)
        var deleteCount = 0
        application.databaseManager.suspendedTransaction {
            deleteCount = BookChapterTable.deleteWhere {
                (BookChapterTable.bookId eq bookId) and (BookChapterTable.id eq chapterId) and (BookChapterTable.status eq BookStatus.PENDING)
            }
        }
        if (deleteCount > 0) {
            val chapterStoreService =
                ChapterStoreService(bookId = bookId, baseDir = application.appConfig.contentDir + "/book")
            chapterStoreService.use {
                it.delete(chapterId)
            }
        }
    }
    suspend fun getChapterContent(
        userId: Int,
        bookId: Int,
        chapterIds: List<Int>
    ): List<String> {
        checkAuthor(userId, bookId)
        val chapterStoreService = ChapterStoreService(
            bookId = bookId,
            baseDir = application.appConfig.contentDir + "/book"
        )
        return chapterStoreService.use {
            chapterIds.map { chapterStoreService.readChapter(it) }
        }
    }
    suspend fun getBookReadStatistic(userId: Int, bookId: Int): List<ChapterReadStatistics> {
        checkAuthor(userId, bookId)
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            ChapterReadStatisticsTable.selectAll().where {
                ChapterReadStatisticsTable.bookId eq bookId
            }.map {
                it.toChapterReadStatistics()
            }
        }
    }
    suspend fun getDraftChapter(
        userId: Int,
    ): List<BookCatalogItem> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable
                .innerJoin(AuthorBookTable, { BookChapterTable.bookId }, { AuthorBookTable.bookId })
                .selectAll()
                .where {
                    (AuthorBookTable.userId eq userId) and (BookChapterTable.status neq BookStatus.PUBLISHED)
                }.map {
                    it.toBookCatalogItem()
                }
        }
    }
    suspend fun updateStatusChapter(
        userId: Int,
        bookId: Int,
        chapterId: Int
    ) {
        checkAuthor(userId, bookId)
        var targetId: Int? = null
        var updateCount = 0
        application.databaseManager.suspendedTransaction {
            updateCount = BookChapterTable.update({
                (BookChapterTable.bookId eq bookId) and (BookChapterTable.id eq chapterId) and (BookChapterTable.status eq BookStatus.PENDING)
            }) {
                it[BookChapterTable.status] = BookStatus.REVIEWING
            }
            val chapterAuditUserId = application.auditService.checkAuditChapter(chapterId)
            val bookAuditUserId = application.auditService.checkAuditBook(bookId)
            when (chapterAuditUserId) {
                null -> {
                    when (bookAuditUserId) {
                        null -> {
                            val auditorId = application.auditService.getAbsentAuditor()
                            AuditBookChapterTable
                                .insert {
                                    it[AuditBookChapterTable.bookChapterId] = chapterId
                                    it[AuditBookChapterTable.userId] = auditorId
                                }
                            AuditBookTable
                                .insert {
                                    it[AuditBookTable.bookId] = bookId
                                    it[AuditBookTable.userId] = auditorId
                                }
                            targetId = auditorId
                        }

                        else -> {
                            AuditBookChapterTable
                                .insert {
                                    it[AuditBookChapterTable.bookChapterId] = chapterId
                                    it[AuditBookChapterTable.userId] = bookAuditUserId
                                }
                            targetId = bookAuditUserId
                        }
                    }
                }

                else -> {
                    targetId = chapterAuditUserId
                }
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            targetId?.let {
                if (updateCount > 0) {
                    val email = application.userService.getUserById(it).email
                    application.emailService.sendEmail(
                        listOf(email),
                        "新增书籍章节审核通知",
                        "审核员,有新的章节需要审核,请及时处理"
                    )
                }
            }
        }
    }

    suspend fun updateStatusBook(
        userId: Int,
        bookId: Int
    ) {
        checkAuthor(userId, bookId)
        var targetId: Int? = null
        var updateCount = 0
        application.databaseManager.suspendedTransaction {
            updateCount = BookTable.update({
                (BookTable.id eq bookId) and (BookTable.status eq BookStatus.PENDING)
            }) {
                it[BookTable.status] = BookStatus.REVIEWING
            }
            val bookAuditUserId = application.auditService.checkAuditBook(bookId)
            when (bookAuditUserId) {
                null -> {
                    val auditorId = application.auditService.getAbsentAuditor()
                    AuditBookTable
                        .insert {
                            it[AuditBookTable.bookId] = bookId
                            it[AuditBookTable.userId] = auditorId
                        }
                    targetId = auditorId
                }

                else -> {
                    targetId = bookAuditUserId
                }
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            targetId?.let {
                if (updateCount > 0) {
                    val email = application.userService.getUserById(it).email
                    application.emailService.sendEmail(
                        listOf(email),
                        "新增书籍审核通知",
                        "审核员,有新的书籍需要审核,请及时处理"
                    )
                }
            }
        }
    }
}


val Application.authorService: AuthorService
    get() = attributes[AuthorService.attributeKey]

fun Application.registerAuthorBookService() {
    attributes[AuthorService.attributeKey] = AuthorService(this)
}
