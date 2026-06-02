package com.qianrenni.guga.com.qianrenni.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object RoleInheritanceTable : Table(name = "role_inheritance") {
    val childId = integer("childId").references(RoleTable.id)
    val parentId = integer("parentId").references(RoleTable.id)
    val createdAt = datetime(name = "createdAt").clientDefault { LocalDateTime.now() }
}