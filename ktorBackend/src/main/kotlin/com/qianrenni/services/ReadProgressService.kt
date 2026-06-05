package com.qianrenni.guga.com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.guga.com.qianrenni.models.domain.UserReadingProgress
import com.qianrenni.guga.com.qianrenni.models.domain.toUserReadingProgress
import com.qianrenni.guga.com.qianrenni.models.tables.UserReadingProgressTable
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ReadProgressService(private val application: Application) {
    suspend fun get(userId: Int): List<UserReadingProgress> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            UserReadingProgressTable.selectAll().where { UserReadingProgressTable.userId eq userId }
                .map { it.toUserReadingProgress() }
        }
    }

    suspend fun add(userId: Int, bookId: Int, lastChapterId: Int, lastPosition: Int) {
        application.databaseManager.suspendedTransaction() {
            UserReadingProgressTable.selectAll()
                .where((UserReadingProgressTable.userId eq userId) and (UserReadingProgressTable.bookId eq bookId))
                .firstOrNull()?.let {
                    UserReadingProgressTable.update({ (UserReadingProgressTable.userId eq userId) and (UserReadingProgressTable.bookId eq bookId) }) {
                        it[UserReadingProgressTable.lastChapterId] = lastChapterId
                        it[UserReadingProgressTable.lastPosition] = lastPosition
                    }
                    return@suspendedTransaction
                }
            UserReadingProgressTable.insert {
                it[UserReadingProgressTable.userId] = userId
                it[UserReadingProgressTable.bookId] = bookId
                it[UserReadingProgressTable.lastChapterId] = lastChapterId
                it[UserReadingProgressTable.lastPosition] = lastPosition
            }
        }
    }

    suspend fun delete(userId: Int, bookId: Int) {
        application.databaseManager.suspendedTransaction() {
            UserReadingProgressTable.deleteWhere { (UserReadingProgressTable.userId eq userId) and (UserReadingProgressTable.bookId eq bookId) }
        }
    }
}

private val ReadProgressServiceAttributeKey = AttributeKey<ReadProgressService>("ReadProgressService")

val Application.readProgressService: ReadProgressService
    get() = attributes[ReadProgressServiceAttributeKey]

fun Application.registerReadProgressService() {
    attributes.put(ReadProgressServiceAttributeKey, ReadProgressService(this))
}