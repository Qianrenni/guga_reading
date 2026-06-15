package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.BookStatus
import com.qianrenni.enums.RoleEnum
import com.qianrenni.models.domain.*
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.*


class AuditBookService(private val application: Application) {
    suspend fun checkAuditor(userId: Int, bookId: Int) {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            require(
                AuditBookTable
                    .selectAll()
                    .where { (AuditBookTable.bookId eq bookId) and (AuditBookTable.userId eq userId) }
                    .count() > 0
            )
        }
    }
    suspend fun getAuditorCount(): Int {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            UserRoleTable
                .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
                .selectAll()
                .where { RoleTable.code eq RoleEnum.REVIEWER }
                .count()
                .toInt()
        }
    }

    suspend fun checkAuditBook(bookId: Int): Int? {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            AuditBookTable
                .selectAll()
                .where { AuditBookTable.bookId eq bookId }
                .firstOrNull()
                ?.toAuditBook()
                ?.userId
        }

    }

    suspend fun checkAuditChapter(chapterId: Int): Int? {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            AuditBookChapterTable
                .selectAll()
                .where { AuditBookChapterTable.bookChapterId eq chapterId }
                .firstOrNull()
                ?.toAuditBookChapter()
                ?.userId
        }
    }

    suspend fun getAbsentAuditor(): Int {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            UserRoleTable
                .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
                .selectAll()
                .where { RoleTable.code eq RoleEnum.REVIEWER }
                .orderBy(Random())
                .limit(1)
                .firstOrNull()
                ?.toUserRole()
                ?.userId ?: throw IllegalStateException("No auditor found")
        }
    }
    suspend fun getAuditBooks(userId: Int, bookIds: List<Int>): List<Book> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookTable.innerJoin(AuditBookTable, { BookTable.id }, { AuditBookTable.bookId })
                .selectAll()
                .where {
                    if (bookIds.isEmpty()) (AuditBookTable.userId eq userId)
                    else ((AuditBookTable.userId eq userId) and (AuditBookTable.bookId inList bookIds))
                }
                .map { it.toBook(application.appConfig.serverUrl) }
        }
    }

    suspend fun getAuditChapters(userId: Int): List<BookCatalogItem> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable
                .innerJoin(AuditBookChapterTable, { BookChapterTable.id }, { AuditBookChapterTable.bookChapterId })
                .selectAll()
                .where { AuditBookChapterTable.userId eq userId }
                .map { it.toBookCatalogItem() }
        }
    }

    suspend fun getAuditChaptersByOrder(
        userId: Int,
        bookId: Int,
        orders: List<Float>
    ): List<BookCatalogItem> {
        checkAuditor(userId, bookId)
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable
                .selectAll()
                .where { (BookChapterTable.bookId eq bookId) and (BookChapterTable.order inList orders) }
                .map { it.toBookCatalogItem() }
                .sortedBy { it.order }
        }
    }

    suspend fun getAuditContentChapter(
        userId: Int,
        bookId: Int,
        orders: List<Float>
    ): List<String> {
        checkAuditor(userId, bookId)
        var chapterIds = emptyList<Int>()
        application.databaseManager.suspendedTransaction(readOnly = true) {
            BookChapterTable
                .selectAll()
                .where { (BookChapterTable.bookId eq bookId) and (BookChapterTable.order inList orders) }
                .map { it.toBookCatalogItem() }
                .sortedBy { it.order }
                .map { it.id }
                .let {
                    chapterIds = it
                }
        }
        val chapterStoreService =
            ChapterStoreService(bookId = bookId, baseDir = application.appConfig.contentDir + "/book")
        return chapterIds.map {
            chapterStoreService.readChapter(it)
        }
    }

    suspend fun updateBookChapter(
        userId: Int,
        bookId: Int,
        chapterId: Int,
        isPass: Boolean
    ) {
        checkAuditor(userId, bookId)
        application.databaseManager.suspendedTransaction {
            BookChapterTable
                .update({ BookChapterTable.id eq chapterId }) {
                    it[BookChapterTable.status] = if (isPass) BookStatus.APPROVED else BookStatus.REJECTED
                }
        }
    }

    suspend fun updateBook(
        userId: Int,
        bookId: Int,
        isPass: Boolean
    ) {
        checkAuditor(userId, bookId)
        application.databaseManager.suspendedTransaction {
            BookTable
                .update({ BookTable.id eq bookId }) {
                    it[BookTable.status] = if (isPass) BookStatus.APPROVED else BookStatus.REJECTED
                }
        }
    }
}

private val AuditBookServiceAttributeKey = AttributeKey<AuditBookService>("AuditBookService")
val Application.auditBookService: AuditBookService
    get() = attributes[AuditBookServiceAttributeKey]

fun Application.registerAuditBookService() {
    attributes[AuditBookServiceAttributeKey] = AuditBookService(this)
}