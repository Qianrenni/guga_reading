package com.qianrenni.models.tables

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object AuthorTable : Table(name = "author") {
    val userId = integer("userId").references(UserTable.id)
    val name = varchar("name", 50)
    val createdAt = datetime("createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updatedAt").clientDefault { LocalDateTime.now() }
}

@Serializable
data class Author(
    val userId: Int,
    val name: String,
    val createdAt: String,
    val updatedAt: String
)

fun ResultRow.toAuthor() = Author(
    userId = this[AuthorTable.userId],
    name = this[AuthorTable.name],
    createdAt = this[AuthorTable.createdAt].toString(),
    updatedAt = this[AuthorTable.updatedAt].toString()
)