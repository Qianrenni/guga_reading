package com.qianrenni.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object AuditBookChapterTable : Table(name = "audit_book_chapter") {
    val bookChapterId = integer("bookChapterId").references(BookChapterTable.id)
    val userId = integer("userId").references(UserTable.id)
    val createdAt = datetime("createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updatedAt").clientDefault { LocalDateTime.now() }
}
