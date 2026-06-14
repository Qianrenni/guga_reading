package com.qianrenni.com.qianrenni.workers

import com.qianrenni.database.databaseManager
import com.qianrenni.workers.aggregateUserReadStatistics
import io.ktor.client.request.*
import io.ktor.server.testing.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test


class TestTask {
    @Test
    fun testAggregateHourlyStatistics() = testApplication {
        configure()
        client.get("/")
        aggregateUserReadStatistics(
            endTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
            databaseManager = application.databaseManager
        )
    }
}