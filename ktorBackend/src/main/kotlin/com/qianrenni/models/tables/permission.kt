package com.qianrenni.guga.com.qianrenni.models.tables

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object PermissionTable : IntIdTable(name = "permission") {
    val name = varchar("name", 100)
    val resourceType = enumerationByName<ResourceTypeEnum>("resourceType", 25)
    val action = enumerationByName<ActionEnum>("action", 25)
    val scope = enumerationByName<ScopeEnum>("scope", 25)
    val bitPosition = integer("bitPosition")
    val createdAt = datetime(name = "createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime(name = "updatedAt").clientDefault { LocalDateTime.now() }
}

class PermissionDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PermissionDao>(PermissionTable)

    var name by PermissionTable.name
    var resourceType by PermissionTable.resourceType
    var action by PermissionTable.action
    var scope by PermissionTable.scope
    var bitPosition by PermissionTable.bitPosition
    val createdAt by PermissionTable.createdAt
    var updatedAt by PermissionTable.updatedAt
}
