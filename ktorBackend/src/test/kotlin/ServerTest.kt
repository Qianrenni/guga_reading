package com.qianrenni

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

val token =
    "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiIiLCJpc3MiOiIiLCJzdWIiOiJ7XCJpZFwiOjIsXCJ1c2VybmFtZVwiOlwiZ3VnYVwiLFwiZW1haWxcIjpcIjEwOTMxNzE2OTNAcXEuY29tXCIsXCJjcmVhdGVkX2F0XCI6XCIyMDI2LTA2LTAzVDE4OjU2OjQyXCIsXCJ1cGRhdGVkX2F0XCI6XCIyMDI2LTA2LTAzVDE5OjQzOjIzXCIsXCJyaWdodFwiOlsxOTNdfSIsImV4cCI6MTc4MDUzNDk1NX0.e3BVl8Z64P4y0WuS9DbqFQHv-strNfnSQN3iYfUwf5k"
class ServerTest {
    @Test
    fun testPermission() = testApplication {
        configure()
        val response = client.get("/test/permissions")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val response1 = client.get("/test/permissions/book1")
        assertEquals(HttpStatusCode.Forbidden, response1.status)
    }

    @Test
    fun testAuthPermission() = testApplication {
        configure()
        val response = client.get("/test/permissions") {
            header(HttpHeaders.Authorization, token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val response1 = client.get("/test/permissions/book1") {
            header(HttpHeaders.Authorization, token)
        }
        assertEquals(HttpStatusCode.OK, response1.status)
    }
    @Test
    fun testAuthMe() = testApplication {
        configure()
        val response = client.get("/token/auth/me") {
            header(HttpHeaders.Authorization, token)
        }
        application.log.debug(response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
    }

}
