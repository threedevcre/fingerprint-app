package com.posfingerprint.app

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.View
import java.util.Calendar

/** Small display helpers shared by the screens. */
object Ui {
    private val avatarColors = intArrayOf(
        0xFF2F5BFF.toInt(), 0xFF0E9F8E.toInt(), 0xFF7C3AED.toInt(),
        0xFFD97706.toInt(), 0xFFDB2777.toInt(), 0xFF0284C7.toInt(),
    )

    fun ago(time: Long): String {
        if (time <= 0L) return "never"
        val diff = System.currentTimeMillis() - time
        if (diff < DateUtils.MINUTE_IN_MILLIS) return "just now"
        return DateUtils.getRelativeTimeSpanString(
            time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    fun initials(name: String): String =
        name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "#" }

    fun avatarColor(key: String): Int = avatarColors[(key.hashCode() and 0x7fffffff) % avatarColors.size]

    fun tint(view: View, color: Int) {
        view.backgroundTintList = ColorStateList.valueOf(color)
    }

    fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()
}
