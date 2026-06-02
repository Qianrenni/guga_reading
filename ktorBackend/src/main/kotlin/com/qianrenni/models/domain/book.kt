package com.qianrenni.guga.com.qianrenni.models.domain

import com.qianrenni.enums.BookStatus
import com.qianrenni.guga.com.qianrenni.models.tables.BookTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class Book(
    val id: Int,
    val name: String,
    val author: String,
    val cover: String,
    val description: String,
    val category: String,
    val tags: String,
    val totalChapter: Int,
    val wordsCount: Int,
    val isActive: Boolean,
    val isEnded: Boolean,
    val parentId: Int,
    val status: BookStatus,
    val createdAt: String,
    val updatedAt: String
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
    parentId = this[BookTable.parentId],
    status = this[BookTable.status],
    createdAt = this[BookTable.createdAt].toString(),
    updatedAt = this[BookTable.updatedAt].toString()
)
