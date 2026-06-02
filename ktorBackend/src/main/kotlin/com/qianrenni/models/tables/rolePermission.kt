package com.qianrenni.guga.com.qianrenni.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object RolePermissionTable : Table(name = "role_permission") {
    val roleId = integer("roleId").references(RoleTable.id)
    val permissionId = integer("permissionId").references(PermissionTable.id)
    val createdAt = datetime(name = "createdAt").clientDefault { LocalDateTime.now() }
}
