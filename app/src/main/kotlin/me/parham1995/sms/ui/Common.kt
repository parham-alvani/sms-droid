package me.parham1995.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue

/** A circle with the name's first letter, coloured by the address so a sender keeps one colour. */
@Composable
fun Avatar(
    name: String,
    address: String,
    size: Dp = 44.dp,
) {
    val colors =
        listOf(
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        )
    val (background, foreground) = colors[address.hashCode().absoluteValue % colors.size]
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
    Box(
        Modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = foreground, style = MaterialTheme.typography.titleMedium)
    }
}

/** Today's messages by time, this year's by day and month, older ones by full date. */
fun shortTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        time.toLocalDate() == today -> time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        time.year == today.year -> time.format(DateTimeFormatter.ofPattern("d MMM"))
        else -> time.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
    }
}

fun clockTime(millis: Long): String =
    Instant
        .ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

/** A muted text colour for secondary lines. */
val mutedText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
