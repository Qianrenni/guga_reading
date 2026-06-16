package com.qianrenni.models.domain

import com.qianrenni.models.tables.UserTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class FullUser(
    val id: Int = -1,
     val userName: String = "",
    val email: String = "",
     val isActive: Boolean = true,
    val avatar: String = "",
    val password: String = "",
     val createdAt: String = "",
     val updatedAt: String = "",
    val right: List<Int> = emptyList(),
)

fun ResultRow.toFullUser() = FullUser(
    id = this[UserTable.id].value,
    userName = this[UserTable.userName],
    email = this[UserTable.email],
    isActive = this[UserTable.isActive],
    avatar = this[UserTable.avatar],
    createdAt = this[UserTable.createdAt].toString(),
    updatedAt = this[UserTable.updatedAt].toString(),
)