package com.qianrenni.services

import io.ktor.server.application.*
import io.ktor.util.*

class AuthorBookService(private val application: Application) {

}

private val AuthorBookServiceAttributeKey = AttributeKey<AuthorBookService>("AuthorBookService")

val Application.authorBookService: AuthorBookService
    get() = attributes[AuthorBookServiceAttributeKey]

fun Application.registerAuthorBookService() {
    attributes[AuthorBookServiceAttributeKey] = AuthorBookService(this)
}
