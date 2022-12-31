package me.stageguard.aruku.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField

/**
 * Created by LoliBall on 2022/12/31 16:24.
 * https://github.com/WhichWho
 */

private val patternHHmm by lazy {
    DateTimeFormatterBuilder()
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .toFormatter()
}

fun LocalDateTime.formatHHmm() = patternHHmm.format(this)