package app.ios.messages.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateFmt {
    private val shortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Conversation-list label: time / Yesterday / weekday / date. */
    fun list(ts: Long): String {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(ts).atZone(zone)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now(zone))
        return when {
            days <= 0L -> shortTime.format(d)
            days == 1L -> "Yesterday"
            days in 2L..6L -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> DateTimeFormatter.ofPattern("M/d/yy").format(d)
        }
    }

    /** Chat separator: bold part + regular part (like "Today" + "9:41 AM"). */
    fun chat(ts: Long): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(ts).atZone(zone)
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), today)
        val time = shortTime.format(d)
        return when {
            days <= 0L -> "Today" to time
            days == 1L -> "Yesterday" to time
            days in 2L..6L -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) to time
            else -> {
                val f = if (d.year == today.year) "MMM d" else "MMM d, yyyy"
                DateTimeFormatter.ofPattern(f).format(d) to "at $time"
            }
        }
    }
}
