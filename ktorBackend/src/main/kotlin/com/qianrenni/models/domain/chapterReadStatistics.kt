package com.qianrenni.models.domain

import com.qianrenni.models.tables.ChapterReadStatisticsTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class ChapterReadStatistics(
    val id: Int,
    val bookId: Int,
    val chapterId: Int,
    val hourStart: String = "",
    val uniqueReaderCount: Int = 0,
    val pageViewCount: Int = 0,
    val totalDuration: Double = 0.0
)

fun ResultRow.toChapterReadStatistics() = ChapterReadStatistics(
    id = this[ChapterReadStatisticsTable.id].value,
    bookId = this[ChapterReadStatisticsTable.bookId],
    chapterId = this[ChapterReadStatisticsTable.chapterId],
    hourStart = this[ChapterReadStatisticsTable.hourStart].toString(),
    uniqueReaderCount = this[ChapterReadStatisticsTable.uniqueReaderCount],
    pageViewCount = this[ChapterReadStatisticsTable.pageViewCount],
    totalDuration = this[ChapterReadStatisticsTable.totalDuration].toDouble()
)
