package com.qianrenni.workers

import com.qianrenni.config.AppConfig
import com.qianrenni.database.DatabaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.enums.ReportEnum
import com.qianrenni.models.tables.*
import com.qianrenni.services.ChapterStoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

suspend fun aggregateUserReadStatistics(
    endTime: LocalDateTime,
    databaseManager: DatabaseManager
) {
    var startTime: LocalDateTime? = null
    do {
        startTime?.let {
            aggregateHourlyStatistics(it, it.plusHours(1), databaseManager)
        }
        startTime = null
        databaseManager.suspendedTransaction(readOnly = true) {
            UserReadEventTable
                .select(UserReadEventTable.eventTime)
                .where { UserReadEventTable.eventTime less endTime }
                .orderBy(UserReadEventTable.eventTime to SortOrder.ASC)
                .limit(1)
                .firstOrNull()
                ?.let {
                    startTime = it[UserReadEventTable.eventTime].truncatedTo(ChronoUnit.HOURS)
                }
        }
    } while (startTime != null)
}

/**
 * 将指定时间段内的 UserReadEvent 聚合到 ChapterReadStatistics
 * @param startTime 小时窗口开始 (含)
 * @param endTime   小时窗口结束 (不含)
 */
private suspend fun aggregateHourlyStatistics(
    startTime: LocalDateTime,
    endTime: LocalDateTime,
    databaseManager: DatabaseManager
) {


    databaseManager.suspendedTransaction {
        // 1. 统计 PV 和 UV (仅 ENTER 事件)
        val pvCount = UserReadEventTable.userId.count()
        val uvCount = UserReadEventTable.userId.countDistinct()
        val pvUvRows = UserReadEventTable
            .select(
                UserReadEventTable.bookId,
                UserReadEventTable.chapterId,
                pvCount,
                uvCount,
            )
            .where {
                (UserReadEventTable.eventType eq ReportEnum.ENTER) and
                        (UserReadEventTable.eventTime greaterEq startTime) and
                        (UserReadEventTable.eventTime less endTime)
            }
            .groupBy(UserReadEventTable.bookId, UserReadEventTable.chapterId)
            .toList()

        // 初始化统计表映射
        val statisticMap = mutableMapOf<Pair<Int, Int>, MutableMap<String, Number>>()
        for (row in pvUvRows) {
            val key = row[UserReadEventTable.bookId] to row[UserReadEventTable.chapterId]
            statisticMap[key] = mutableMapOf(
                "pv" to row[pvCount],
                "uv" to row[uvCount],
                "totalDuration" to 0
            )
        }

        // 如果没有 ENTER 事件，直接清理本窗口事件并退出
        if (statisticMap.isEmpty()) {
            UserReadEventTable.deleteWhere {
                (UserReadEventTable.eventTime greaterEq startTime) and
                        (UserReadEventTable.eventTime less endTime)
            }
            return@suspendedTransaction
        }

        // 2. 获取本窗口内所有事件（含 ENTER 和其他，按 (user, book, chapter) 分组并排序）
        val events = UserReadEventTable
            .select(
                UserReadEventTable.userId,
                UserReadEventTable.bookId,
                UserReadEventTable.chapterId,
                UserReadEventTable.eventType,
                UserReadEventTable.eventTime
            )
            .where {
                (UserReadEventTable.eventTime greaterEq startTime) and
                        (UserReadEventTable.eventTime less endTime)
            }
            .orderBy(
                UserReadEventTable.userId to SortOrder.ASC,
                UserReadEventTable.bookId to SortOrder.ASC,
                UserReadEventTable.chapterId to SortOrder.ASC,
                UserReadEventTable.eventTime to SortOrder.ASC
            )
            .toList()

        // 按 (bookId, chapterId) 分组，内部按用户分组计算时长
        val userGroups = events.groupBy {
            Triple(
                it[UserReadEventTable.userId],
                it[UserReadEventTable.bookId],
                it[UserReadEventTable.chapterId]
            )
        }
        for ((userBookChapter, userEvents) in userGroups) {
            val (_, bookId, chapterId) = userBookChapter
            var last = startTime
            var totalDuration = 0L // 秒

            for (event in userEvents) {
                val eventType = event[UserReadEventTable.eventType]
                val eventTime = event[UserReadEventTable.eventTime]
                if (eventType == ReportEnum.ENTER) {
                    last = eventTime
                } else {
                    totalDuration += ChronoUnit.SECONDS.between(last, eventTime)
                    last = eventTime
                }
            }
            // 至少1秒
            if (totalDuration == 0L) {
                totalDuration = 1
            }
            val key = bookId to chapterId
            val stats = statisticMap.getOrPut(key) {
                mutableMapOf("pv" to 0, "uv" to 0, "totalDuration" to 0)
            }
            stats["totalDuration"] = stats["totalDuration"] as Int + totalDuration.toInt() // 累加
        }

        // 3. 批量插入聚合结果
        statisticMap.map { (key, value) ->
            ChapterReadStatisticsTable.insert {
                it[bookId] = key.first
                it[chapterId] = key.second
                it[hourStart] = startTime
                it[pageViewCount] = value["pv"]!!.toInt()
                it[uniqueReaderCount] = value["uv"]!!.toInt()
                it[totalDuration] = value["totalDuration"]!!.toFloat()
            }
        }
        // 4. 删除已聚合的原始事件
        UserReadEventTable.deleteWhere {
            (UserReadEventTable.eventTime less endTime)
        }
    }
}

suspend fun publishBook(databaseManager: DatabaseManager, appConfig: AppConfig) {
    var books = emptyList<Book>()
    databaseManager.suspendedTransaction {
        books = BookTable
            .selectAll()
            .where { BookTable.status eq BookStatus.APPROVED }
            .map { it.toBook(appConfig.serverUrl) }

        val (negativeBooks, positiveBooks) = books.partition { it.id < 0 }

        // 处理需要合并的书籍（id < 0 为临时书籍，-id 为目标书籍）
        negativeBooks.forEach { book ->
            BookTable.update({ BookTable.id eq (-book.id) }) {
                it[BookTable.status] = BookStatus.PUBLISHED
                it[BookTable.name] = book.name
                it[BookTable.author] = book.author
                it[BookTable.description] = book.description
                it[BookTable.category] = book.category
                it[BookTable.tags] = book.tags
            }
            AuthorBookTable.deleteWhere { AuthorBookTable.bookId eq book.id }
            AuditBookTable.deleteWhere { AuditBookTable.bookId eq book.id }
            BookTable.deleteWhere { BookTable.id eq book.id }
        }

        // 直接发布的书籍（id > 0）
        if (positiveBooks.isNotEmpty()) {
            BookTable.update({
                BookTable.id inList positiveBooks.map { it.id }
            }) {
                it[BookTable.status] = BookStatus.PUBLISHED
            }
        }

        // ---------- 章节处理 ----------
        val chapters = BookChapterTable
            .selectAll()
            .where { BookChapterTable.status eq BookStatus.APPROVED }
            .map { it.toBookCatalogItem() }

        val negativeChapters = chapters.filter { it.order < 0f }

        if (negativeChapters.isNotEmpty()) {
            // 查询对应的目标章节
            val chapterMap = BookChapterTable
                .selectAll()
                .where {
                    negativeChapters
                        .map { (BookChapterTable.bookId eq it.bookId) and (BookChapterTable.order eq (-it.order)) }
                        .reduce { acc, op -> acc or op }
                }
                .map { it.toBookCatalogItem() }
                .associateBy { it.bookId to it.order }

            negativeChapters.forEach { chapter ->
                val targetId = chapterMap[chapter.bookId to (-chapter.order)]?.id
                    ?: return@forEach // 找不到目标章节则跳过

                ChapterStoreService(
                    name = chapter.bookId.toString(),
                    baseDir = appConfig.contentDir + "/book"
                ).use { store ->
                    store.update(
                        contentId = targetId,
                        content = store.readChapter(chapter.id)
                    )
                    store.delete(chapter.id)
                }
            }
            AuditBookChapterTable.deleteWhere {
                AuditBookChapterTable.bookChapterId inList negativeChapters.map { it.id }
            }
            BookChapterTable.deleteWhere {
                BookChapterTable.id inList negativeChapters.map { it.id }
            }
        }

        val positiveChapters = chapters.filter { it.order > 0f }
        if (positiveChapters.isNotEmpty()) {
            BookChapterTable.update({
                BookChapterTable.id inList positiveChapters.map { it.id }
            }) {
                it[BookChapterTable.status] = BookStatus.PUBLISHED
            }
        }
        val countChapter = BookChapterTable.id.count()
        val bookChapterCount = BookChapterTable
            .select(BookChapterTable.bookId, countChapter)
            .where { BookChapterTable.bookId inList chapters.map { it.bookId } }
            .groupBy(BookChapterTable.bookId)
            .associate { it[BookChapterTable.bookId] to it[countChapter].toInt() }
        bookChapterCount.forEach { (bookId, count) ->
            BookTable.update({ BookTable.id eq bookId }) {
                it[BookTable.totalChapter] = count
            }
        }
    }

    // 文件操作（与事务无关，放在事务外执行）
    withContext(Dispatchers.IO) {
        books.filter { it.id < 0 }.forEach { book ->
            val srcDir = File(appConfig.staticDir, "/book/${book.id}")
            val srcCover = File(srcDir, "cover.webp")
            if (srcCover.exists()) {
                srcCover.copyTo(
                    File(appConfig.staticDir, "/book/${-book.id}/cover.webp"),
                    overwrite = true
                )
                srcDir.deleteRecursively()
            }
        }
    }
}