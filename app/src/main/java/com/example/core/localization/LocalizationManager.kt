package com.example.core.localization

import java.util.Calendar
import java.util.TimeZone

enum class AppLanguage(val code: String, val displayNameEn: String, val displayNameFa: String, val isRtl: Boolean) {
    SYSTEM("system", "System Default", "پیش‌فرض سیستم", false),
    ENGLISH("en", "English", "انگلیسی (English)", false),
    PERSIAN("fa", "Persian (فارسی)", "فارسی (Persian)", true)
}

enum class CalendarSystem(val id: String, val labelEn: String, val labelFa: String) {
    GREGORIAN("gregorian", "Gregorian", "میلادی"),
    JALALI("jalali", "Solar Hijri (Jalali)", "خورشیدی (جلالی)")
}

/**
 * Normalizes Persian and English text for consistent OCR, tagging, and semantic search.
 * Handles Arabic Yeh/Kaf variants, ZWNJ (نیم‌فاصله), diacritics, and Eastern/Western digits.
 */
object PersianNormalizer {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun normalizeForSearch(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input.trim().lowercase()) {
            when (ch) {
                'ي', 'ئ', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ؤ' -> sb.append('و')
                'إ', 'أ', 'آ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                '\u200C', '\u200B', '\u200D', '\uFEFF' -> sb.append(' ') // ZWNJ to space
                in '\u064B'..'\u065F' -> { /* strip Arabic/Persian harakat diacritics */ }
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun formatDigits(input: String, usePersianDigits: Boolean): String {
        if (!usePersianDigits) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(persianDigits[ch.code - '0'.code])
                in '٠'..'٩' -> sb.append(persianDigits[ch.code - '٠'.code])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(value: Int, usePersianDigits: Boolean): String =
        formatDigits(value.toString(), usePersianDigits)
}

/**
 * Accurate Gregorian to Solar Hijri (Jalali / Shamsi) Calendar converter.
 */
object JalaliCalendar {
    private val persianMonthsFa = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    private val persianMonthsEn = arrayOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    )
    private val gregorianMonthsEn = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val gregorianMonthsFa = arrayOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }

    fun formatTimestamp(
        epochSeconds: Long,
        calendarSystem: CalendarSystem,
        isPersianLang: Boolean,
        mode: String = "DAY"
    ): String {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = epochSeconds * 1000L
        }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)

        val raw = if (calendarSystem == CalendarSystem.JALALI) {
            val j = gregorianToJalali(gy, gm, gd)
            val mName = if (isPersianLang) persianMonthsFa[j.month - 1] else persianMonthsEn[j.month - 1]
            when (mode) {
                "YEAR" -> "${j.year}"
                "MONTH" -> "$mName ${j.year}"
                else -> "${j.day} $mName ${j.year}"
            }
        } else {
            val mName = if (isPersianLang) gregorianMonthsFa[gm - 1] else gregorianMonthsEn[gm - 1]
            when (mode) {
                "YEAR" -> "$gy"
                "MONTH" -> "$mName $gy"
                else -> "$mName $gd, $gy"
            }
        }
        return PersianNormalizer.formatDigits(raw, isPersianLang)
    }
}

/**
 * Complete bilingual string helper so both Persian (fa) and English (en) switch instantaneously
 * across every screen, dialog, filter, AI tag, and tool without requiring an Activity restart.
 */
data class BilingualStrings(val isFa: Boolean) {
    fun tr(en: String, fa: String): String = if (isFa) fa else en

    val appTitle get() = tr("takZ-Gallery", "takZ-Gallery")
    val appSubtitle get() = tr("Photo & Video Gallery", "گالری عکس و ویدیو")
    val tabPhotos get() = tr("Gallery", "گالری")
    val tabAlbums get() = tr("Albums", "آلبوم‌ها")
    val tabSearch get() = tr("Search", "جستجو")
    val tabStudio get() = tr("Cleanup", "پاک‌سازی")
    val tabVault get() = tr("Vault", "گاوصندوق")
    val tabSettings get() = tr("Settings", "تنظیمات")

    val aiReady get() = tr("Ready", "آماده")
    val aiIndexing get() = tr("Indexing…", "در حال بررسی…")
    val aiPaused get() = tr("Paused", "متوقف")
    val searchHint get() = tr(
        "Search anything (tags, objects, text)…",
        "هر چیزی را جستجو کنید (تگ، سوژه، متن)…"
    )
}
