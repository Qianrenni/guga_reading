package com.qianrenni.workers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class CronFlowTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cronFlow with every-second cron should emit at least once`() = runTest {
        val flow = cronFlow("* * * * * ?")
        val emitted = mutableListOf<ZonedDateTime>()
        val collectJob = launch {
            flow.collect { emitted.add(it) }
        }
        advanceTimeBy(2000.milliseconds)
        collectJob.cancel()
        assertTrue(emitted.isNotEmpty(), "Should emit at least one time")
    }

    @Test
    fun `cronFlow with invalid expression should throw IllegalArgumentException`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            cronFlow("invalid-cron").first()
        }
    }

    @Test
    fun `cronFlow should support cancellation`() = runTest {
        val collectJob = launch {
            cronFlow("* * * * * ?").collect()
        }
        yield()
        collectJob.cancelAndJoin()
        assertTrue(collectJob.isCancelled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cronFlow with custom timezone should use correct zone`() = runTest {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val flow = cronFlow("* * * * * ?", tokyoZone)
        val emitted = mutableListOf<ZonedDateTime>()
        val collectJob = launch {
            flow.collect { emitted.add(it) }
        }
        advanceTimeBy(1000.milliseconds)
        collectJob.cancel()
        assertTrue(emitted.isNotEmpty())
        assertEquals(tokyoZone, emitted.first().zone)
    }
}
