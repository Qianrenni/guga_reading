package com.qianrenni.guga.com.qianrenni.models.domain

import com.qianrenni.guga.com.qianrenni.models.tables.UserRoleTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class UserRole(
    val userId: Int,
    val roleId: Int,
    val grantedBy: Int,
    val grantedAt: String
)

fun ResultRow.toUserRole() = UserRole(
    userId = this[UserRoleTable.userId],
    roleId = this[UserRoleTable.roleId],
    grantedBy = this[UserRoleTable.grantedBy],
    grantedAt = this[UserRoleTable.grantedAt].toString()
)
