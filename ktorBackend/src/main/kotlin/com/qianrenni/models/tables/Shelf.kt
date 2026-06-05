package com.qianrenni.guga.com.qianrenni.models.tables

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object ShelfTable : Table(name = "shelf") {
    val userId = integer("userId").references(UserTable.id)
    val bookId = integer("bookId").references(BookTable.id)
    val createdAt = datetime("createdAt")
}

@Serializable
data class Shelf(
    val userId: Int,
    @SerialName("book_id") val bookId: Int,
    @SerialName("created_at") val createdAt: String,
)

fun ResultRow.toShelf() = Shelf(
    userId = this[ShelfTable.userId],
    bookId = this[ShelfTable.bookId],
    createdAt = this[ShelfTable.createdAt].toString(),
)