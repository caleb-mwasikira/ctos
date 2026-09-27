package com.ctos.client.screens

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** "0 B", "12 KB", "1.4 MB": file sizes for people. */
fun formatBytes(size: Long): String {
    if (size < 1024) return "$size B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = size / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rounded = if (value >= 10) value.toLong().toString() else ((value * 10).toLong() / 10.0).toString()
    return "$rounded ${units[unit]}"
}

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Today, 14:05", "Yesterday, 09:30", "27 Sep, 14:05" (with the year when it is not this year). */
fun formatTime(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val moment = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone)
    val clock = "${moment.hour.toString().padStart(2, '0')}:${moment.minute.toString().padStart(2, '0')}"

    val daysAgo = now.date.toEpochDays() - moment.date.toEpochDays()
    return when {
        daysAgo == 0 -> "Today, $clock"
        daysAgo == 1 -> "Yesterday, $clock"
        moment.year == now.year -> "${moment.dayOfMonth} ${months[moment.monthNumber - 1]}, $clock"
        else -> "${moment.dayOfMonth} ${months[moment.monthNumber - 1]} ${moment.year}, $clock"
    }
}
