package com.qianrenni.models.tables

import com.qianrenni.enums.ReportEnum
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserReadEventTable : Table(name = "user_read_event") {
    val userId = integer("userId").references(UserTable.id)
    val bookId = integer("bookId").references(BookTable.id)
    val chapterId = integer("chapterId").references(BookChapterTable.id)
    val eventTime = datetime("eventTime")
    val eventType = enumerationByName<ReportEnum>("eventType", 25)
}
