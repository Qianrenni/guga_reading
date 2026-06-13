package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.models.domain.Book
import com.qianrenni.models.domain.BookCatalogItem
import com.qianrenni.models.domain.toBook
import com.qianrenni.models.domain.toBookCatalogItem
import com.qianrenni.models.tables.BookChapterTable
import com.qianrenni.models.tables.BookTable
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

class BookService(private val application: Application) {
    suspend fun getBookCount(): Long {
        return application.cache(
            keyPrefix = "book_service",
            args = listOf("book_count"),
            serializer = Long.serializer()
        ) {
            application.databaseManager.suspendedTransaction(readOnly = true) {
                BookTable.selectAll().where { BookTable.status eq BookStatus.PUBLISHED }.count()
            }
        }
    }

    suspend fun getCategory(): List<String> {
        return application.cache(
            keyPrefix = "book_service",
            args = listOf("category"),
            serializer = ListSerializer(String.serializer())
        ) {
            application.databaseManager.suspendedTransaction(readOnly = true) {
                BookTable.select(BookTable.category).withDistinct(true).map { it[BookTable.category] }
            }
        }
    }

    suspend fun getRecommendBook(query: String): List<Book> {
        return application.cache(
            keyPrefix = "book_service",
            args = listOf("recommend_book", query),
            serializer = ListSerializer(Book.serializer())
        ) {
            application.databaseManager.suspendedTransaction(readOnly = true) {
                BookTable.selectAll().where { BookTable.status eq BookStatus.PUBLISHED }.orderBy(Random()).limit(5)
                    .map { it.toBook(application.appConfig.serverUrl) }
            }
        }
    }

    suspend fun getSearchBook(query: String): List<Book> {
        return application.cache(
            keyPrefix = "book_service",
            args = listOf("search_book", query),
            serializer = ListSerializer(Book.serializer())
        ) {
            application.databaseManager.suspendedTransaction(readOnly = true) {
                BookTable.selectAll()
                    .where { (BookTable.status eq BookStatus.PUBLISHED) and ((BookTable.name like "%$query%") or (BookTable.author like "%$query%")) }
                    .map { it.toBook(application.appConfig.serverUrl) }
            }
        }
    }

    suspend fun getBookList(bookIds: List<Int>): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.selectAll().where { (BookTable.status eq BookStatus.PUBLISHED) and (BookTable.id inList bookIds) }
                .map { it.toBook(application.appConfig.serverUrl) }
        }
    }

    suspend fun getBookCatalog(bookId: Int): List<BookCatalogItem> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable
                .selectAll()
                .where {
                    (BookChapterTable.status eq BookStatus.PUBLISHED) and (BookChapterTable.bookId eq bookId)
                }
                .orderBy(BookChapterTable.order)
                .map { it.toBookCatalogItem() }
        }
    }

    suspend fun getBookChapter(chapterId: Int, bookId: Int): String {
        return application.cache(
            keyPrefix = "book_service",
            args = listOf("book_chapter", bookId.toString(), chapterId.toString()),
            serializer = String.serializer()
        ) {
            val result = application.databaseManager.suspendedTransaction(readOnly = true) {
                BookChapterTable
                    .selectAll()
                    .where {
                        (BookChapterTable.id eq chapterId) and (BookChapterTable.bookId eq bookId) and (BookChapterTable.status eq BookStatus.PUBLISHED)
                    }.firstOrNull()
            }
            if (result == null) {
                throw IllegalArgumentException("书籍内容遍历攻击")
            }
            val chapterStore = ChapterStoreService(
                bookId = bookId,
                baseDir = application.appConfig.contentDir + "/book",
            )
            chapterStore.loadIndex()
            chapterStore.readChapter(chapterId)
        }
    }

    suspend fun getBookSelect(category: String, offSet: Int, limit: Int): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.selectAll()
                .where { (BookTable.category eq category) and (BookTable.status eq BookStatus.PUBLISHED) }
                .offset(start = offSet.toLong())
                .limit(count = limit).map { it.toBook(application.appConfig.serverUrl) }
        }
    }
}

private val BookServiceAttributeKey = AttributeKey<BookService>("bookServiceAttributeKey")

val Application.bookService: BookService
    get() = attributes[BookServiceAttributeKey]

fun Application.registerBookService() {
    attributes[BookServiceAttributeKey] = BookService(this)
}
