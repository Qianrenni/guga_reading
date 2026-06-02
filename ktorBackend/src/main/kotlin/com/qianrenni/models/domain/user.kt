package com.qianrenni.guga.com.qianrenni.models.domain

import com.qianrenni.guga.com.qianrenni.models.tables.UserTable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class FullUser(
    val id:Int,
    @SerialName("username") val userName:String,
    val email:String,
    val isActive:Boolean,
    val avatar:String,
    @SerialName("created_at") val createdAt:String,
    @SerialName("updated_at") val updatedAt:String,
    val right:List<Int>
)

fun ResultRow.toFullUser() = FullUser(
    id = this[UserTable.id].value,
    userName = this[UserTable.userName],
    email = this[UserTable.email],
    isActive = this[UserTable.isActive],
    avatar = this[UserTable.avatar],
    createdAt = this[UserTable.createdAt].toString(),
    updatedAt = this[UserTable.updatedAt].toString(),
    right = emptyList()
)