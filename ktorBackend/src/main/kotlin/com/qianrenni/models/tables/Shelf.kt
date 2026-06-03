package com.qianrenni.guga.com.qianrenni.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object ShelfTable : Table(name = "shelf") {
    val userId = integer("userId").references(UserTable.id)
    val bookId = integer("bookId").references(BookTable.id)
    val createdAt = datetime("createdAt")
}
