package com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.models.tables.BookTable
import com.qianrenni.models.tables.Shelf
import com.qianrenni.models.tables.ShelfTable
import com.qianrenni.models.tables.toShelf
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq


class ShelfService(private val application: Application) {
    companion object {
        val attributeKey = AttributeKey<ShelfService>("ShelfService")
    }
    suspend fun get(userId: Int): List<Shelf> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            ShelfTable
                .innerJoin(BookTable, { ShelfTable.bookId }, { BookTable.id })
                .selectAll()
                .where { (ShelfTable.userId eq userId) and (BookTable.isActive eq true) }
                .map { it.toShelf() }
        }
    }

    suspend fun add(bookId: Int, userId: Int) {
        application.databaseManager.suspendedTransaction {
            ShelfTable.insert {
                it[ShelfTable.bookId] = bookId
                it[ShelfTable.userId] = userId
            }
        }
    }

    suspend fun delete(bookId: Int, userId: Int) {
        application.databaseManager.suspendedTransaction {
            ShelfTable.deleteWhere { (ShelfTable.bookId eq bookId) and (ShelfTable.userId eq userId) }
        }
    }
}


val Application.shelfService: ShelfService
    get() = attributes[ShelfService.attributeKey]

fun Application.registerShelfService() {
    attributes.put(ShelfService.attributeKey, ShelfService(this))
}