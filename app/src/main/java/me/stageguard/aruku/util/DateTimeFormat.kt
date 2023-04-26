package me.stageguard.aruku.util

import androidx.compose.runtime.Composable
import me.stageguard.aruku.R
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Created by LoliBall on 2022/12/31 16:24.
 * https://github.com/WhichWho
 */

private val zoneOffset = ZoneOffset.ofHours(+8)

private val patternHHmm by lazy {
    DateTimeFormatterBuilder()
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .toFormatter()
}

private val patternMMdd by lazy {
    DateTimeFormatterBuilder()
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .toFormatter()
}

fun Long.toFormattedTime(): String = patternHHmm.format(
    LocalDateTime.ofEpochSecond(this@toFormattedTime / 1000, 0, zoneOffset)
)

@Composable
fun Long.toFormattedDateTime() = buildString {
    val time = LocalDateTime.ofEpochSecond(this@toFormattedDateTime, 0, zoneOffset)
    val current = LocalDateTime.now()

    if (current.dayOfYear == time.dayOfYear && time.year == current.year) {
        // today
    } else if (
        (current.dayOfYear - time.dayOfYear == 1 && time.year == current.year) ||
        (current.year - time.year == 1 && time.dayOfYear == current.minusDays(1).dayOfYear)
    ) { // yesterday
        append(R.string.time_yesterday.stringResC)
        append(' ')
    } else {
        append(patternMMdd.format(time))
        append(' ')
    }
    append(patternHHmm.format(time))
}