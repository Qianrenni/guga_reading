package com.qianrenni.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserRoleTable : Table(name = "user_role") {
    val userId = integer("userId").references(UserTable.id)
    val roleId = integer("roleId").references(RoleTable.id)
    val grantedBy = integer("grantedBy").references(UserTable.id)
    val grantedAt = datetime(name = "grantedAt").clientDefault { LocalDateTime.now() }
}
