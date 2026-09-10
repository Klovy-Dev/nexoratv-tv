package fr.nexoratv.tv.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Dates {
    /** Ex. `14 juin 2026`. minSdk 23 → `SimpleDateFormat` (pas de java.time). */
    fun frenchDate(epochMs: Long): String =
        SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(Date(epochMs))

    /** Jours entiers restants avant `epochMs` (négatif si dépassé). */
    fun daysUntil(epochMs: Long): Int =
        ((epochMs - System.currentTimeMillis()) / 86_400_000L).toInt()
}
