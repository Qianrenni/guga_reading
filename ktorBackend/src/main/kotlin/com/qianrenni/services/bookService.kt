package com.qianrenni.guga.com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.databaseManager
import com.qianrenni.guga.com.qianrenni.models.domain.Book
import com.qianrenni.guga.com.qianrenni.models.domain.BookCatalogItem
import com.qianrenni.guga.com.qianrenni.models.domain.toBook
import com.qianrenni.guga.com.qianrenni.models.domain.toBookCatalogItem
import com.qianrenni.guga.com.qianrenni.models.tables.BookChapterTable
import com.qianrenni.guga.com.qianrenni.models.tables.BookTable
import com.qianrenni.services.ChapterStoreService
import com.qianrenni.services.cache
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
                BookTable.selectAll().count()
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
                BookTable.selectAll().orderBy(Random()).limit(5).map { it.toBook() }
                    .map { it.copy(cover = "${application.appConfig.serverUrl}/static/book/${it.id}/cover.webp") }
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
                BookTable.selectAll().where { (BookTable.name like "%$query%") or (BookTable.author like "%$query%") }
                    .map { it.toBook() }
                    .map { it.copy(cover = "${application.appConfig.serverUrl}/static/book/${it.id}/cover.webp") }
            }
        }
    }

    suspend fun getBookList(bookIds: List<Int>): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.selectAll().where { BookTable.id inList bookIds }.map { it.toBook() }
                .map { it.copy(cover = "${application.appConfig.serverUrl}/static/book/${it.id}/cover.webp") }
        }
    }

    suspend fun getBookCatalog(bookId: Int): List<BookCatalogItem> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable.selectAll().where { BookChapterTable.bookId eq bookId }.orderBy(BookChapterTable.order)
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
                BookChapterTable.selectAll()
                    .where { (BookChapterTable.id eq chapterId) and (BookChapterTable.bookId eq bookId) }.firstOrNull()
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
            BookTable.selectAll().where { (BookTable.category eq category) }.offset(start = offSet.toLong())
                .limit(count = limit).map { it.toBook() }
                .map { it.copy(cover = "${application.appConfig.serverUrl}/static/book/${it.id}/cover.webp") }
        }
    }
}

private val BookServiceAttributeKey = AttributeKey<BookService>("bookServiceAttributeKey")

val Application.bookService: BookService
    get() = attributes[BookServiceAttributeKey]

fun Application.registerBookService() {
    attributes[BookServiceAttributeKey] = BookService(this)
}
