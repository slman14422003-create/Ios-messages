package app.ios.messages.ui

import android.content.Context
import app.ios.messages.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateFmt {
    private fun shortTime() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

    fun time(ts: Long): String =
        shortTime().format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

    fun list(ctx: Context, ts: Long): String {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(ts).atZone(zone)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now(zone))
        return when {
            days <= 0L -> shortTime().format(d)
            days == 1L -> ctx.getString(R.string.yesterday)
            days in 2L..6L -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(d)
        }
    }

    fun chat(ctx: Context, ts: Long): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(ts).atZone(zone)
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), today)
        val time = shortTime().format(d)
        return when {
            days <= 0L -> ctx.getString(R.string.today) to time
            days == 1L -> ctx.getString(R.string.yesterday) to time
            days in 2L..6L -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) to time
            else -> {
                val f = if (d.year == today.year) "MMM d" else "MMM d, yyyy"
                DateTimeFormatter.ofPattern(f, Locale.getDefault()).format(d) to ctx.getString(R.string.at_time, time)
            }
        }
    }
}
