package com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.enums.RoleEnum
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.*

class AuthorApplicationService(private val application: Application) {

    /**
     * 用户提交作者申请
     */
    suspend fun apply(userId: Int, reason: String): AuthorApplication {
        // 检查是否已有待处理的申请
        val existing = application.databaseManager.suspendedTransaction(readOnly = true) {
            AuthorApplicationTable.selectAll()
                .where {
                    (AuthorApplicationTable.userId eq userId) and
                            (AuthorApplicationTable.status inList listOf("pending"))
                }
                .firstOrNull()
        }
        if (existing != null) {
            throw IllegalArgumentException("您已提交过申请，请等待审核")
        }

        // 检查是否已经是作者
        val alreadyAuthor = application.databaseManager.suspendedTransaction(readOnly = true) {
            AuthorTable.selectAll().where { AuthorTable.userId eq userId }.firstOrNull()
        }
        if (alreadyAuthor != null) {
            throw IllegalArgumentException("您已经是作者了")
        }

        return application.databaseManager.suspendedTransaction {
            AuthorApplicationTable.insertAndGetId {
                it[AuthorApplicationTable.userId] = userId
                it[AuthorApplicationTable.reason] = reason
                it[AuthorApplicationTable.status] = "pending"
            }.let { id ->
                AuthorApplicationTable.selectAll().where { AuthorApplicationTable.id eq id }.single()
                    .toAuthorApplication()
            }
        }
    }

    /**
     * 获取用户的申请记录
     */
    suspend fun getUserApplication(userId: Int): AuthorApplication? {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            AuthorApplicationTable.selectAll()
                .where { AuthorApplicationTable.userId eq userId }
                .orderBy(AuthorApplicationTable.createdAt, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.toAuthorApplication()
        }
    }

    /**
     * 管理员获取所有申请（按状态筛选）
     */
    suspend fun getApplications(status: String?): List<AuthorApplication> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            val query = AuthorApplicationTable.selectAll()
            if (!status.isNullOrBlank()) {
                query.where { AuthorApplicationTable.status eq status }
            }
            query.orderBy(AuthorApplicationTable.createdAt, SortOrder.DESC)
                .map { it.toAuthorApplication() }
        }
    }

    /**
     * 管理员审核通过申请
     */
    suspend fun approve(adminId: Int, applicationId: Int) {
        application.databaseManager.suspendedTransaction {
            val app = AuthorApplicationTable.selectAll()
                .where { AuthorApplicationTable.id eq applicationId }
                .firstOrNull()
                ?: throw IllegalArgumentException("申请不存在")

            val status = app[AuthorApplicationTable.status]
            if (status != "pending") {
                throw IllegalArgumentException("该申请已处理，无法再次审核")
            }

            val userId = app[AuthorApplicationTable.userId]

            // 更新申请状态
            AuthorApplicationTable.update({ AuthorApplicationTable.id eq applicationId }) {
                it[AuthorApplicationTable.status] = "approved"
                it[AuthorApplicationTable.handledBy] = adminId
                it[AuthorApplicationTable.handledAt] = java.time.LocalDateTime.now()
            }

            // 创建作者记录
            val userRow = UserTable.selectAll().where { UserTable.id eq userId }.single()
            val userName = userRow[UserTable.userName]
            val alreadyAuthor = AuthorTable.selectAll().where { AuthorTable.userId eq userId }.firstOrNull()
            if (alreadyAuthor == null) {
                AuthorTable.insert {
                    it[AuthorTable.userId] = userId
                    it[AuthorTable.name] = userName
                }
            }

            // 添加作者角色
            application.rightService.addUserRole(userId, RoleEnum.AUTHOR)
        }
    }

    /**
     * 管理员驳回申请
     */
    suspend fun reject(adminId: Int, applicationId: Int, rejectReason: String?) {
        application.databaseManager.suspendedTransaction {
            val app = AuthorApplicationTable.selectAll()
                .where { AuthorApplicationTable.id eq applicationId }
                .firstOrNull()
                ?: throw IllegalArgumentException("申请不存在")

            val status = app[AuthorApplicationTable.status]
            if (status != "pending") {
                throw IllegalArgumentException("该申请已处理，无法再次审核")
            }

            AuthorApplicationTable.update({ AuthorApplicationTable.id eq applicationId }) {
                it[AuthorApplicationTable.status] = "rejected"
                it[AuthorApplicationTable.handledBy] = adminId
                it[AuthorApplicationTable.rejectReason] = rejectReason
                it[AuthorApplicationTable.handledAt] = java.time.LocalDateTime.now()
            }
        }
    }
}

private val AuthorApplicationServiceAttributeKey = AttributeKey<AuthorApplicationService>("AuthorApplicationService")

val Application.authorApplicationService: AuthorApplicationService
    get() = attributes[AuthorApplicationServiceAttributeKey]

fun Application.registerAuthorApplicationService() {
    attributes[AuthorApplicationServiceAttributeKey] = AuthorApplicationService(this)
}
