package com.qianrenni.models.tables

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object AuthorApplicationTable : IntIdTable(name = "author_application") {
    val userId = integer("userId").references(UserTable.id)
    val reason = varchar("reason", 500)
    val status = varchar("status", 50).default("pending") // pending, approved, rejected
    val handledBy = integer("handledBy").references(UserTable.id).nullable().default(null)
    val rejectReason = varchar("rejectReason", 500).nullable().default(null)
    val handledAt = datetime("handledAt").nullable().default(null)
    val createdAt = datetime(name = "createdAt").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime(name = "updatedAt").clientDefault { LocalDateTime.now() }
}

@Serializable
data class AuthorApplication(
    val id: Int,
    val userId: Int,
    val reason: String,
    val status: String,
    val handledBy: Int? = null,
    val rejectReason: String? = null,
    val handledAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

fun ResultRow.toAuthorApplication() = AuthorApplication(
    id = this[AuthorApplicationTable.id].value,
    userId = this[AuthorApplicationTable.userId],
    reason = this[AuthorApplicationTable.reason],
    status = this[AuthorApplicationTable.status],
    handledBy = this[AuthorApplicationTable.handledBy],
    rejectReason = this[AuthorApplicationTable.rejectReason],
    handledAt = this[AuthorApplicationTable.handledAt]?.toString(),
    createdAt = this[AuthorApplicationTable.createdAt].toString(),
    updatedAt = this[AuthorApplicationTable.updatedAt].toString()
)
