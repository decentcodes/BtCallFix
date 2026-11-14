package com.example.btcallfix.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun address(c: Context): String? = sp(c).getString("addr", null)
    fun setAddress(c: Context, a: String) = sp(c).edit().putString("addr", a).apply()

    @Synchronized
    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val old = sp(c).getString("log", "") ?: ""
        sp(c).edit().putString("log", "$t $msg\n$old".take(5000)).apply()
    }
    fun readLog(c: Context): String = sp(c).getString("log", "") ?: ""
    fun clearLog(c: Context) = sp(c).edit().remove("log").apply()

    /** 0 = follow the phone, 1 = light, 2 = dark. */
    fun themeMode(c: Context): Int = sp(c).getInt("theme", 0)
    fun setThemeMode(c: Context, mode: Int) = sp(c).edit().putInt("theme", mode).apply()
}
