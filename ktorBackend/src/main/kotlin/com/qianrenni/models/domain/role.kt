package com.qianrenni.guga.com.qianrenni.models.domain

import com.qianrenni.enums.RoleEnum
import com.qianrenni.guga.com.qianrenni.models.tables.RoleTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class Role(
    val id: Int,
    val name: String,
    val code: RoleEnum,
    val description: String?,
    val createdAt: String,
    val updatedAt: String
)

fun ResultRow.toRole() = Role(
    id = this[RoleTable.id].value,
    name = this[RoleTable.name],
    code = this[RoleTable.code],
    description = this[RoleTable.description],
    createdAt = this[RoleTable.createdAt].toString(),
    updatedAt = this[RoleTable.updatedAt].toString()
)
