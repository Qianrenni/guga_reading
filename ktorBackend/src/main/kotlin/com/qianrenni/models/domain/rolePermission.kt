package com.qianrenni.models.domain

import com.qianrenni.models.tables.RolePermissionTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class RolePermission(
    val roleId: Int,
    val permissionId: Int,
    val createdAt: String
)

fun ResultRow.toRolePermission() = RolePermission(
    roleId = this[RolePermissionTable.roleId],
    permissionId = this[RolePermissionTable.permissionId],
    createdAt = this[RolePermissionTable.createdAt].toString()
)
