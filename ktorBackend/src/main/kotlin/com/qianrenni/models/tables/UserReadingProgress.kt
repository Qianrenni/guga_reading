package com.qianrenni.guga.com.qianrenni.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserReadingProgressTable : Table(name = "user_reading_progress") {
    val userId = integer("userId").references(UserTable.id)
    val bookId = integer("bookId").references(BookTable.id)
    val lastChapterId = integer("lastChapterId")
    val lastPosition = integer("lastPosition")
    val lastReadAt = datetime("lastReadAt")
}
