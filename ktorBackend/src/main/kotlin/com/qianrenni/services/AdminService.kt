package com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.models.tables.FullUser
import com.qianrenni.models.tables.UserRole
import com.qianrenni.models.tables.UserTable
import com.qianrenni.models.tables.toFullUser
import com.qianrenni.schemas.PageResult
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

class AdminService(private val application: Application) {
    companion object {
        val attributeKey = AttributeKey<AdminService>("AdminService")
    }

    /**
     * 分页获取用户列表
     */
    suspend fun getUsers(page: Int, size: Int, keyword: String? = null): PageResult<AdminUserResponse> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            val offset = (page - 1) * size

            val query = UserTable.selectAll().let { q ->
                if (!keyword.isNullOrBlank()) {
                    q.andWhere {
                        (UserTable.userName like "%$keyword%") or
                                (UserTable.email like "%$keyword%")
                    }
                } else q
            }
            val total = query.count().toInt()
            val users = query
                .orderBy(UserTable.createdAt, SortOrder.DESC)
                .offset(offset.toLong())
                .limit(size)
                .map { it.toFullUser() }
            val p = application.rightService.getUserRoles(users.map { it.id })
            PageResult(
                items = users.map { AdminUserResponse(user = it, roles = p[it.id] ?: emptyList()) },
                total = total,
                page = page,
                size = size
            )
        }
    }

    /**
     * 获取用户详情（含角色）
     */
    suspend fun getUserDetail(userId: Int): AdminUserResponse {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            val user = UserTable.selectAll().where { UserTable.id eq userId }.firstOrNull()?.toFullUser()
                ?: throw IllegalStateException("用户不存在")
            val userRole = application.rightService.getUserRoles(listOf(userId))[userId] ?: emptyList()
            AdminUserResponse(
                user = user,
                roles = userRole
            )
        }
    }

    /**
     * 更新用户状态（激活/禁用）
     */
    suspend fun updateUserStatus(userId: Int, isActive: Boolean) {
        application.databaseManager.suspendedTransaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.isActive] = isActive
            }
        }
    }
}

@Serializable
data class AdminUserResponse(
    val user: FullUser,
    val roles: List<UserRole>,
)
val Application.adminService: AdminService
    get() = attributes[AdminService.attributeKey]

fun Application.registerAdminService() {
    attributes[AdminService.attributeKey] = AdminService(this)
}
