package com.qianrenni.guga.com.qianrenni.models.tables

import com.qianrenni.enums.RoleEnum
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object RoleTable : IntIdTable(name = "role") {
    val name = varchar("name", 50)
    val code = enumerationByName<RoleEnum>("code", 25)
    val description = varchar("description", 255).nullable()
    val createdAt = datetime(name = "createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime(name = "updatedAt").clientDefault { LocalDateTime.now() }
}

class RoleDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RoleDao>(RoleTable)

    var name by RoleTable.name
    var code by RoleTable.code
    var description by RoleTable.description
    val createdAt by RoleTable.createdAt
    var updatedAt by RoleTable.updatedAt
}
