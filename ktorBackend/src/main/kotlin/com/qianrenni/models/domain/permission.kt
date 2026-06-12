package com.qianrenni.models.domain

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.models.tables.PermissionTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class Permission(
    val id: Int,
    val name: String,
    val resourceType: ResourceTypeEnum,
    val action: ActionEnum,
    val scope: ScopeEnum,
    val bitPosition: Int,
    val createdAt: String,
    val updatedAt: String
)

fun ResultRow.toPermission() = Permission(
    id = this[PermissionTable.id].value,
    name = this[PermissionTable.name],
    resourceType = this[PermissionTable.resourceType],
    action = this[PermissionTable.action],
    scope = this[PermissionTable.scope],
    bitPosition = this[PermissionTable.bitPosition],
    createdAt = this[PermissionTable.createdAt].toString(),
    updatedAt = this[PermissionTable.updatedAt].toString()
)
