package com.openweibo.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

object TimeUtils {

    private val fullFormat = DateTimeFormatter.ofPattern(
        "EEE MMM dd HH:mm:ss Z yyyy",
        Locale.ENGLISH
    )
    private val ymdHm = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    private val ymdHmShort = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
    private val isoOffset = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val zone = ZoneId.systemDefault()

    fun parseWeiboTime(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return 0L
        val now = ZonedDateTime.now(zone)

        value.toLongOrNull()?.let { number ->
            return if (number > 10_000_000_000L) number else number * 1000L
        }

        try {
            return ZonedDateTime.parse(value, fullFormat).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Keep trying other shapes.
        }

        try {
            return OffsetDateTime.parse(value, isoOffset).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Keep trying other shapes.
        }

        for (formatter in listOf(ymdHm, ymdHmShort)) {
            try {
                return LocalDateTime.parse(value, formatter).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Next format.
            }
        }

        val minuteAgo = Regex("^(\\d+)\\s*分钟前$").find(value)
        val hourAgo = Regex("^(\\d+)\\s*小时前$").find(value)
        val dayAgo = Regex("^(\\d+)\\s*天前$").find(value)
        when {
            value == "刚刚" || value == "现在" -> return now.toInstant().toEpochMilli()
            minuteAgo != null -> return now.minusMinutes(minuteAgo.groupValues[1].toLong()).toInstant().toEpochMilli()
            hourAgo != null -> return now.minusHours(hourAgo.groupValues[1].toLong()).toInstant().toEpochMilli()
            dayAgo != null -> return now.minusDays(dayAgo.groupValues[1].toLong()).toInstant().toEpochMilli()
        }

        val clock = Regex("(今天|昨天|前天)\\s*(\\d{1,2}):(\\d{2})").find(value)
        if (clock != null) {
            val days = when (clock.groupValues[1]) {
                "昨天" -> 1L
                "前天" -> 2L
                else -> 0L
            }
            val date = now.toLocalDate().minusDays(days)
            return date.atTime(
                LocalTime.of(clock.groupValues[2].toInt(), clock.groupValues[3].toInt())
            ).atZone(zone).toInstant().toEpochMilli()
        }

        val monthDay = Regex("^(\\d{1,2})-(\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?$").find(value)
        if (monthDay != null) {
            val month = monthDay.groupValues[1].toInt()
            val day = monthDay.groupValues[2].toInt()
            val hour = monthDay.groupValues[3].toIntOrNull() ?: 0
            val minute = monthDay.groupValues[4].toIntOrNull() ?: 0
            val date = safeDate(now.year, month, day) ?: return 0L
            return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        }

        val chineseDate = Regex("^(\\d{1,2})月(\\d{1,2})日(?:\\s*(\\d{1,2}):(\\d{2}))?$").find(value)
        if (chineseDate != null) {
            val month = chineseDate.groupValues[1].toInt()
            val day = chineseDate.groupValues[2].toInt()
            val hour = chineseDate.groupValues[3].toIntOrNull() ?: 0
            val minute = chineseDate.groupValues[4].toIntOrNull() ?: 0
            var date = safeDate(now.year, month, day)
            if (date != null && date.isAfter(now.toLocalDate())) {
                date = safeDate(now.year - 1, month, day)
            }
            return date?.atTime(hour, minute)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: 0L
        }

        val timeOnly = Regex("^(\\d{1,2}):(\\d{2})$").find(value)
        if (timeOnly != null) {
            return now.toLocalDate().atTime(
                LocalTime.of(timeOnly.groupValues[1].toInt(), timeOnly.groupValues[2].toInt())
            ).atZone(zone).toInstant().toEpochMilli()
        }

        return 0L
    }

    fun relativeTime(millis: Long): String {
        if (millis <= 0L) return "未知时间"
        val now = Instant.now()
        val then = Instant.ofEpochMilli(millis)
        val seconds = ChronoUnit.SECONDS.between(then, now)
        if (seconds < 0) return "刚刚"
        if (seconds < 60) return "刚刚"
        if (seconds < 3600) return "${seconds / 60}分钟前"

        val thenDate = then.atZone(zone).toLocalDate()
        val nowDate = now.atZone(zone).toLocalDate()
        val time = then.atZone(zone).toLocalTime()
        val hhmm = String.format(Locale.CHINA, "%02d:%02d", time.hour, time.minute)
        return when {
            thenDate == nowDate && seconds < 3600 * 12 -> "${seconds / 3600}小时前"
            thenDate == nowDate -> "今天 $hhmm"
            thenDate == nowDate.minusDays(1) -> "昨天 $hhmm"
            thenDate.year == nowDate.year -> "${thenDate.monthValue}月${thenDate.dayOfMonth}日 $hhmm"
            else -> "${thenDate.year}年${thenDate.monthValue}月${thenDate.dayOfMonth}日"
        }
    }

    fun absoluteTime(millis: Long): String {
        if (millis <= 0L) return "未知时间"
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
    }

    fun formatCount(value: Int): String = formatCount(value.toLong())

    fun formatCount(value: Long): String {
        return when {
            value >= 100_000_000L -> {
                val n = value / 100_000_000.0
                trimDecimal(n) + "亿"
            }
            value >= 10_000L -> {
                val n = value / 10_000.0
                trimDecimal(n) + "万"
            }
            else -> value.toString()
        }
    }

    fun formatHeat(value: Long?): String? {
        if (value == null || value <= 0L) return null
        return formatCount(value) + " 热度"
    }

    fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }

    fun formatPlaybackPosition(millis: Long): String {
        if (millis <= 0L) return "0:00"
        return formatDuration((millis / 1000L).toInt()).ifBlank { "0:00" }
    }

    private fun trimDecimal(value: Double): String {
        val rounded = String.format(Locale.US, "%.1f", value)
        return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    fun looksOlderThanNow(millis: Long): Boolean {
        return millis > 0L && abs(Instant.ofEpochMilli(millis).toEpochMilli() - System.currentTimeMillis()) > 1000L * 60 * 60 * 24 * 365 * 5
    }
}
