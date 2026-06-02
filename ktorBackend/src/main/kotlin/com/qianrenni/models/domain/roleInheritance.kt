package com.qianrenni.guga.com.qianrenni.models.domain

import com.qianrenni.guga.com.qianrenni.models.tables.RoleInheritanceTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class RoleInheritance(
    val childId: Int,
    val parentId: Int,
    val createdAt: String
)

fun ResultRow.toRoleInheritance() = RoleInheritance(
    childId = this[RoleInheritanceTable.childId],
    parentId = this[RoleInheritanceTable.parentId],
    createdAt = this[RoleInheritanceTable.createdAt].toString()
)
