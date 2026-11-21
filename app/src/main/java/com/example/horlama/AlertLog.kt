package com.example.horlama

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Night(val date: LocalDate, val count: Int, val first: Long, val last: Long)

/** Her uyarının zamanını kalıcı olarak saklar. Gece, öğlen 12'den ertesi gün öğlen 12'ye kadar sayılır. */
object AlertLog {
    private const val PREF = "log"
    private const val KEY = "alerts"

    fun nightOf(t: Long): LocalDate =
        Instant.ofEpochMilli(t).minus(12, ChronoUnit.HOURS)
            .atZone(ZoneId.systemDefault()).toLocalDate()

    @Synchronized
    fun add(ctx: Context, t: Long) {
        val list = read(ctx).takeLast(4999) + t
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, list.joinToString(",")).apply()
    }

    fun read(ctx: Context): List<Long> {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun nights(ctx: Context): List<Night> =
        read(ctx).groupBy { nightOf(it) }
            .map { (d, l) -> Night(d, l.size, l.minOrNull() ?: 0L, l.maxOrNull() ?: 0L) }
            .sortedByDescending { it.date }

    @Synchronized
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun dayLabel(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("tr")))

    fun timeLabel(t: Long): String =
        Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
}
