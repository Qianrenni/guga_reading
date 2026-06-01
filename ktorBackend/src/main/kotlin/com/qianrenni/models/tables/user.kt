package com.qianrenni.guga.com.qianrenni.models.tables

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime


object UserTable: IntIdTable(name = "user") {
    val userName = varchar("userName", 15)
    val password = varchar("password", 25)
    val email = varchar("email", 255)
    val isActive = bool("isActive")
    val avatar = varchar("avatar", 255)
    val createdAt = datetime(name = "createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime(name = "updatedAt").clientDefault { LocalDateTime.now() }
}

class UserDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserDao>(UserTable)
    val userName by UserTable.userName
    val password by UserTable.password
    val email by UserTable.email
    val isActive by UserTable.isActive
    val avatar by UserTable.avatar
    val createdAt by UserTable.createdAt
    val updatedAt by UserTable.updatedAt
}