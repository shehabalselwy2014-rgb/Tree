package com.example.domain.util

import android.icu.util.IslamicCalendar
import com.example.domain.model.DatePrecision
import java.util.Calendar
import java.util.GregorianCalendar

object HijriDateConverter {

    private val hijriMonths = listOf(
        "محرم",
        "صفر",
        "ربيع الأول",
        "ربيع الآخر",
        "جمادى الأولى",
        "جمادى الآخرة",
        "رجب",
        "شعبان",
        "رمضان",
        "شوال",
        "ذو القعدة",
        "ذو الحجة",
    )

    fun formatDateForDisplay(
        rawDate: String?,
        precision: DatePrecision,
        includeHijri: Boolean,
    ): String {
        if (rawDate.isNullOrBlank() || precision == DatePrecision.UNKNOWN) {
            return "غير معروف"
        }
        val trimmed = rawDate.trim()
        val precisionSuffix = if (precision == DatePrecision.APPROXIMATE) " (تقريباً)" else ""
        if (!includeHijri) {
            return "$trimmed$precisionSuffix"
        }
        val hijriText = convertIsoToHijriLabel(trimmed)
        return if (hijriText != null) {
            "$trimmed$precisionSuffix  •  $hijriText"
        } else {
            "$trimmed$precisionSuffix"
        }
    }

    fun convertIsoToHijriLabel(isoDate: String): String? {
        val parts = isoDate.trim().split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        if (year < 622 || year > 2500) return null

        return try {
            val month = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
            val day = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 28) ?: 1

            val gregorian = GregorianCalendar(year, month - 1, day)
            val islamic = IslamicCalendar()
            islamic.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            islamic.timeInMillis = gregorian.timeInMillis

            val hYear = islamic.get(Calendar.YEAR)
            val hMonth = islamic.get(Calendar.MONTH)
            val hDay = islamic.get(Calendar.DAY_OF_MONTH)
            val monthName = hijriMonths.getOrNull(hMonth) ?: "${hMonth + 1}"

            when (parts.size) {
                3 -> "$hDay $monthName $hYear هـ"
                2 -> "$monthName $hYear هـ"
                else -> "$hYear هـ"
            }
        } catch (_: Throwable) {
            null
        }
    }
}
