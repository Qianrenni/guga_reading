package com.qianrenni.models.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object ChapterReadStatisticsTable : IntIdTable(name = "chapter_read_statistics") {
    val bookId = integer("bookId").references(BookTable.id)
    val chapterId = integer("chapterId").references(BookChapterTable.id)
    val hourStart = datetime("hourStart")
    val uniqueReaderCount = integer("uniqueReaderCount")
    val pageViewCount = integer("pageViewCount")
    val totalDuration = float("totalDuration")
}
