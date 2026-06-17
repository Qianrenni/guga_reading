package com.qianrenni.services

import io.ktor.server.application.*
import io.ktor.util.*

class AdminService(private val application: Application) {
    companion object {
        val attributeKey = AttributeKey<AdminService>("AdminService")
    }

}

val Application.adminService: AdminService
    get() = attributes[AdminService.attributeKey]

fun Application.registerAdminService() {
    attributes[AdminService.attributeKey] = AdminService(this)
}
