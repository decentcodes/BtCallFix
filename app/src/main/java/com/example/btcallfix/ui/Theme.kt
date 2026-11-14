package com.example.btcallfix.ui

import android.content.Context
import android.content.res.Configuration
import com.example.btcallfix.data.Prefs

/** Colour palette for the light and dark looks. */
class Colors(val dark: Boolean) {
    val bg = if (dark) 0xFF0E1318.toInt() else 0xFFF2F5F9.toInt()
    val card = if (dark) 0xFF182029.toInt() else 0xFFFFFFFF.toInt()
    val stroke = if (dark) 0xFF26323E.toInt() else 0xFFE1E7EE.toInt()
    val text = if (dark) 0xFFE9EFF5.toInt() else 0xFF0F172A.toInt()
    val sub = if (dark) 0xFF93A4B5.toInt() else 0xFF5B6B7C.toInt()
    val accent = if (dark) 0xFF38BDF8.toInt() else 0xFF0E7490.toInt()
    val onAccent = if (dark) 0xFF04202E.toInt() else 0xFFFFFFFF.toInt()
    val ok = if (dark) 0xFF34D399.toInt() else 0xFF059669.toInt()
    val warn = if (dark) 0xFFFBBF24.toInt() else 0xFFB45309.toInt()
    val onStatus = if (dark) 0xFF06131C.toInt() else 0xFFFFFFFF.toInt()
}

/** True if the app should currently be drawn in the dark look. */
fun isDarkTheme(c: Context): Boolean = when (Prefs.themeMode(c)) {
    1 -> false
    2 -> true
    else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
