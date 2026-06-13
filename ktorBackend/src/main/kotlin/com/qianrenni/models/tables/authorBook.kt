package com.qianrenni.models.tables

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object AuthorBookTable : Table(name = "author_book") {
    val userId = integer("userId").references(UserTable.id)
    val bookId = integer("bookId").references(BookTable.id)
    val createdAt = datetime("createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updatedAt").clientDefault { LocalDateTime.now() }
}

@Serializable
data class AuthorBook(
    val userId: Int,
    val bookId: Int,
    val createdAt: String,
    val updatedAt: String
)

fun ResultRow.toAuthorBook() = AuthorBook(
    userId = this[AuthorBookTable.userId],
    bookId = this[AuthorBookTable.bookId],
    createdAt = this[AuthorBookTable.createdAt].toString(),
    updatedAt = this[AuthorBookTable.updatedAt].toString()
)