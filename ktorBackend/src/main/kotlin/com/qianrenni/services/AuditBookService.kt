package com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.enums.RoleEnum
import com.qianrenni.models.domain.toUserRole
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll


class AuditBookService(private val application: Application) {
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
}

private val AuditBookServiceAttributeKey = AttributeKey<AuditBookService>("AuditBookService")
val Application.auditBookService: AuditBookService
    get() = attributes[AuditBookServiceAttributeKey]

fun Application.registerAuditBookService() {
    attributes[AuditBookServiceAttributeKey] = AuditBookService(this)
}