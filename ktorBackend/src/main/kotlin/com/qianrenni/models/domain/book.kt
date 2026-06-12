package com.qianrenni.models.domain

import com.qianrenni.enums.BookStatus
import com.qianrenni.models.tables.BookChapterTable
import com.qianrenni.models.tables.BookTable
import com.qianrenni.models.tables.UserReadingProgressTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class Book(
    val id: Int,
    val name: String = "",
    val author: String = "",
    val cover: String = "",
    val description: String = "",
    val category: String = "",
    val tags: String = "",
    val totalChapter: Int = 0,
    val wordsCount: Int = 0,
    val isActive: Boolean = true,
    val isEnded: Boolean = false,
    val parentId: Int,
    val status: BookStatus,
    val createdAt: String = "",
    val updatedAt: String = ""
)

fun ResultRow.toBook() = Book(
    id = this[BookTable.id].value,
    name = this[BookTable.name],
    author = this[BookTable.author],
    cover = this[BookTable.cover],
    description = this[BookTable.description],
    category = this[BookTable.category],
    tags = this[BookTable.tags],
    totalChapter = this[BookTable.totalChapter],
    wordsCount = this[BookTable.wordsCount],
    isActive = this[BookTable.isActive],
    isEnded = this[BookTable.isEnded],
    parentId = this[BookTable.parentId] ?: -1,
    status = this[BookTable.status],
    createdAt = this[BookTable.createdAt].toString(),
    updatedAt = this[BookTable.updatedAt].toString()
)

@Serializable
data class BookCatalogItem(
    val id: Int,
    val bookId: Int,
    val title: String,
    val wordsCount: Int,
    val status: BookStatus,
    val order: Float,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)

fun ResultRow.toBookCatalogItem() = BookCatalogItem(
    id = this[BookChapterTable.id].value,
    bookId = this[BookChapterTable.bookId],
    title = this[BookChapterTable.title],
    wordsCount = this[BookChapterTable.wordCount],
    status = this[BookChapterTable.status],
    order = this[BookChapterTable.order].toFloat(),
    isActive = this[BookChapterTable.isActive],
    createdAt = this[BookChapterTable.createdAt].toString(),
    updatedAt = this[BookChapterTable.updatedAt].toString()
)

@Serializable
data class UserReadingProgress(
    val userId: Int,
    val bookId: Int,
    val lastChapterId: Int,
    val lastPosition: Int,
    val lastReadAt: String,
)

fun ResultRow.toUserReadingProgress() = UserReadingProgress(
    userId = this[UserReadingProgressTable.userId],
    bookId = this[UserReadingProgressTable.bookId],
    lastChapterId = this[UserReadingProgressTable.lastChapterId],
    lastPosition = this[UserReadingProgressTable.lastPosition],
    lastReadAt = this[UserReadingProgressTable.lastReadAt].toString(),
)