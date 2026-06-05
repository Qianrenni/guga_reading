package com.qianrenni.guga.com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.enums.ReportEnum
import com.qianrenni.guga.com.qianrenni.models.tables.UserReadEventTable
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.insert

class StatisticsService(private val application: Application) {
    suspend fun addUserReadEvent(userId: Int, bookId: Int, chapterId: Int, eventType: ReportEnum) {
        application.databaseManager.suspendedTransaction {
            UserReadEventTable.insert {
                it[UserReadEventTable.userId] = userId
                it[UserReadEventTable.bookId] = bookId
                it[UserReadEventTable.chapterId] = chapterId
                it[UserReadEventTable.eventType] = eventType
            }
        }
    }
}

private val StatisticsServiceAttributeKey = AttributeKey<StatisticsService>("statisticsService")

val Application.statisticsService: StatisticsService
    get() = attributes[StatisticsServiceAttributeKey]

fun Application.registerStatisticsService() {
    attributes.put(StatisticsServiceAttributeKey, StatisticsService(this))
}