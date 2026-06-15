package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.controller.RequestUpdateBookChapter
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.models.domain.*
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.io.File
import kotlin.io.path.Path

class AuthorBookService(private val application: Application) {
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

    suspend fun getBook(userId: Int): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.innerJoin(AuthorBookTable, { BookTable.id }, { AuthorBookTable.bookId })
                .selectAll().where { AuthorBookTable.userId eq userId }
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
        coverFile: File
    ) {
        require(bookId > 0)
        checkAuthor(userId, bookId)
        var targetId: Int? = null
        application.databaseManager.suspendedTransaction {
            val alreadyBook = BookTable.selectAll().where { BookTable.id eq (-bookId) }.firstOrNull()
                ?.toBook(application.appConfig.serverUrl)
            when (alreadyBook) {
                null -> {
                    targetId = BookTable.insertAndGetId {
                        it[BookTable.id] = (-bookId)
                        it[BookTable.name] = bookName
                        it[BookTable.author] = author
                        it[BookTable.tags] = tags
                        it[BookTable.description] = description
                        it[BookTable.category] = category
                    }.value
                }

                else -> {
                    BookTable.update({ BookTable.id eq alreadyBook.id }) {
                        it[BookTable.name] = bookName
                        it[BookTable.author] = author
                        it[BookTable.tags] = tags
                        it[BookTable.description] = description
                        it[BookTable.category] = category
                    }
                    targetId = alreadyBook.id
                }
            }
        }
        withContext(Dispatchers.IO) {
            coverFile.copyTo(
                Path(application.appConfig.staticDir + "/book/${targetId}/cover.webp").toFile(),
                overwrite = true
            )
            coverFile.deleteOnExit()
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
        withContext(Dispatchers.IO) {
            chapterStore.use {
                targetId?.let { chapterStore.update(targetId, requestUpdateBookChapter.content) }
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
        return withContext(Dispatchers.IO) {
            chapterStoreService.use {
                chapterIds.map { chapterStoreService.readChapter(it) }
            }
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

}

private val AuthorBookServiceAttributeKey = AttributeKey<AuthorBookService>("AuthorBookService")

val Application.authorBookService: AuthorBookService
    get() = attributes[AuthorBookServiceAttributeKey]

fun Application.registerAuthorBookService() {
    attributes[AuthorBookServiceAttributeKey] = AuthorBookService(this)
}
