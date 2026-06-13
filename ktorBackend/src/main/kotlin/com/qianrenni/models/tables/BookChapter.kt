package com.qianrenni.models.tables

import com.qianrenni.enums.BookStatus
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object BookChapterTable : IntIdTable(name = "book_chapter") {
    val bookId = integer("bookId").references(BookTable.id)
    val title = varchar("title", 255)
    val wordCount = integer("wordCount")
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")
    val status = enumerationByName<BookStatus>("status", 25)
    val isActive = bool("isActive")
    val order = float("order")
}
