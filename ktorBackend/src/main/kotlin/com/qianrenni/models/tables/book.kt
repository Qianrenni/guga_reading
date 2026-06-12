package com.qianrenni.models.tables

import com.qianrenni.enums.BookStatus
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime


object BookTable : IntIdTable(name = "book") {
    val name = varchar("name", 255)
    val author = varchar("author", 255)
    val cover = varchar("cover", 255)
    val description = text("description")
    val category = varchar("category", 25)
    val tags = varchar("tags", 255)
    val totalChapter = integer(name = "totalChapter")
    val createdAt = datetime("createdAt").default(LocalDateTime.now())
    val updatedAt = datetime("updatedAt").default(LocalDateTime.now())
    val wordsCount = integer("wordsCount")
    val isActive = bool("isActive")
    val isEnded = bool("isEnded")
    val parentId = integer("parentId").references(id).nullable()
    val status = enumerationByName<BookStatus>(
        name = "status",
        length = 25
    )
}