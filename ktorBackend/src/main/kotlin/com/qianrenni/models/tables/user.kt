package com.qianrenni.guga.com.qianrenni.models.tables

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