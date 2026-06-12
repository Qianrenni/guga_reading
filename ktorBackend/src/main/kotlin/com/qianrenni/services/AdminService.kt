package com.qianrenni.services

import io.ktor.server.application.*
import io.ktor.util.*

class AdminService(private val application: Application) {


}

private val AdminServiceAttributeKey = AttributeKey<AdminService>("AdminService")

val Application.adminService: AdminService
    get() = attributes[AdminServiceAttributeKey]

fun Application.registerAdminService() {
    attributes[AdminServiceAttributeKey] = AdminService(this)
}
