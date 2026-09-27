package com.ctos.client

import com.ctos.client.screens.formatBytes
import com.ctos.client.screens.formatTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun fileSizesAreReadableByPeople() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("999 B", formatBytes(999))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("96 KB", formatBytes(96 * 1024))
        assertEquals("1.4 MB", formatBytes((1.4 * 1024 * 1024).toLong() + 1000))
        assertEquals("8.2 MB", formatBytes((8.2 * 1024 * 1024).toLong() + 1000))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private val now = millis("2026-09-27T12:00:00Z")

    @Test
    fun todayAndYesterdayAreNamed() {
        assertEquals("Today, 09:05", formatTime(millis("2026-09-27T09:05:00Z"), now, TimeZone.UTC))
        assertEquals("Yesterday, 23:59", formatTime(millis("2026-09-26T23:59:00Z"), now, TimeZone.UTC))
    }

    @Test
    fun olderDaysShowTheDateAndAnotherYearShowsTheYear() {
        assertEquals("3 Sep, 14:30", formatTime(millis("2026-09-03T14:30:00Z"), now, TimeZone.UTC))
        assertEquals("31 Dec 2025, 08:00", formatTime(millis("2025-12-31T08:00:00Z"), now, TimeZone.UTC))
    }

    @Test
    fun theTimeZoneDecidesWhichDayItIs() {
        // 23:30 UTC on the 26th is already the 27th in Nairobi (UTC+3)
        assertEquals("Today, 02:30", formatTime(millis("2026-09-26T23:30:00Z"), now, TimeZone.of("Africa/Nairobi")))
    }
}
