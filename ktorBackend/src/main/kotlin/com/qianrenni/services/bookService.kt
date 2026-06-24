package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

class BookService(private val application: Application) {
    companion object {
        val attributeKey = AttributeKey<BookService>("bookServiceAttributeKey")
    }
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
                BookTable.selectAll()
                    .where { (BookTable.status eq BookStatus.PUBLISHED) and (BookTable.isActive eq true) }
                    .orderBy(Random()).limit(5)
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
                    .where { (BookTable.status eq BookStatus.PUBLISHED) and (BookTable.isActive eq true) and ((BookTable.name like "%$query%") or (BookTable.author like "%$query%")) }
                    .map { it.toBook(application.appConfig.serverUrl) }
            }
        }
    }

    suspend fun getBookList(bookIds: List<Int>): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.selectAll()
                .where { (BookTable.status eq BookStatus.PUBLISHED) and (BookTable.isActive eq true) and (BookTable.id inList bookIds) }
                .map { it.toBook(application.appConfig.serverUrl) }
        }
    }

    suspend fun getBookCatalog(bookId: Int): List<BookCatalogItem> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable
                .selectAll()
                .where {
                    (BookChapterTable.status eq BookStatus.PUBLISHED) and (BookChapterTable.bookId eq bookId) and (BookChapterTable.isActive eq true)
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
                        (BookChapterTable.id eq chapterId) and (BookChapterTable.bookId eq bookId) and (BookChapterTable.status eq BookStatus.PUBLISHED) and (BookChapterTable.isActive eq true)
                    }.firstOrNull()
            }
            if (result == null) {
                throw IllegalArgumentException("书籍内容遍历攻击")
            }
            ChapterStoreService(
                name = bookId.toString(),
                baseDir = application.appConfig.contentDir + "/book",
            ).use {
                it.readChapter(chapterId)
            }
        }
    }

    suspend fun getBookSelect(category: String, offSet: Int, limit: Int): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.selectAll()
                .where { (BookTable.category eq category) and (BookTable.status eq BookStatus.PUBLISHED) and (BookTable.isActive eq true) }
                .offset(start = offSet.toLong())
                .limit(count = limit).map { it.toBook(application.appConfig.serverUrl) }
        }
    }
}


val Application.bookService: BookService
    get() = attributes[BookService.attributeKey]

fun Application.registerBookService() {
    attributes[BookService.attributeKey] = BookService(this)
}
