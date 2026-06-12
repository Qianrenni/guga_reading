package com.qianrenni.models.tables

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
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