package com.example.btcallfix.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.btcallfix.R
import com.example.btcallfix.data.Prefs
import com.example.btcallfix.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

@SuppressLint("MissingPermission")
class MainActivity : Activity() {

    private lateinit var c: Colors
    private var scroll: ScrollView? = null

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { render() } }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { render() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { render() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
    }

    override fun onResume() { super.onResume(); render() }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        render()
    }

    // ---------------------------------------------------------------- state

    private fun permsOk() =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** 0 = not running, 1 = running but not allowed, 2 = running and allowed. */
    private fun shizukuState(): Int = when {
        !Shizuku.pingBinder() -> 0
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> 2
        else -> 1
    }

    private fun bonded(): List<BluetoothDevice> = try {
        getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.toList() ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun test(on: Boolean) {
        val a = Prefs.address(this) ?: return
        ShizukuBridge.setProfile(this, a, on) { m ->
            Prefs.log(this, "TEST $m")
            runOnUiThread { render() }
        }
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (i != null) startActivity(i)
        else Toast.makeText(this, "Shizuku is not installed", Toast.LENGTH_SHORT).show()
    }

    private fun copyLog() {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("BT Call Fix log", Prefs.readLog(this)))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ rendering

    private fun render() {
        c = Colors(isDarkTheme(this))
        val keep = scroll?.scrollY ?: 0
        val sv = buildScreen()
        scroll = sv
        setContentView(sv)
        sv.post { sv.scrollTo(0, keep) }
        applyBars()
    }

    private fun applyBars() {
        val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (c.dark) 0 else flags, flags)
        window.setBackgroundDrawable(ColorDrawable(c.bg))
    }

    private fun buildScreen(): ScrollView {
        val perms = permsOk()
        val sh = shizukuState()
        val addr = Prefs.address(this)
        val ready = perms && sh == 2 && addr != null

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28))
            addView(header())
            addView(heroCard(perms, sh, addr, ready))
            addView(setupCard(perms, sh, addr))
            addView(earbudsCard(perms, addr))
            addView(testCard(ready))
            addView(logCard())
            addView(footer())
        }
        return ScrollView(this).apply {
            setBackgroundColor(c.bg)
            isFillViewport = true
            clipToPadding = false
            addView(col)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
    }

    private fun header(): View {
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(tv("BT Call Fix", 22f, c.text, bold = true))
            addView(tv("Quiet ring, earbud calls", 13f, c.sub))
        }
        val toggle = ImageView(this).apply {
            setImageResource(if (c.dark) R.drawable.ic_sun else R.drawable.ic_moon)
            setColorFilter(c.text)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = RippleDrawable(
                ColorStateList.valueOf(withAlpha(c.accent, 0x33)),
                shape(c.card, dp(24).toFloat(), c.stroke, dp(1)),
                null
            )
            contentDescription = if (c.dark) "Switch to light theme" else "Switch to dark theme"
            setOnClickListener {
                Prefs.setThemeMode(this@MainActivity, if (c.dark) 1 else 2)
                render()
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
            addView(logo)
            addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(toggle, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
    }

    private fun heroCard(perms: Boolean, sh: Int, addr: String?, ready: Boolean): View {
        val (title, sub) = when {
            ready -> "Ready" to "Your earbuds stay silent while the phone rings and switch to call mode when you answer."
            !perms -> "Setup needed" to "Allow the permissions below to get started."
            sh == 0 -> "Shizuku isn't running" to "Start Shizuku (Wireless debugging, then Start), then come back here."
            sh == 1 -> "Allow Shizuku access" to "BT Call Fix needs Shizuku's permission to change the earbuds' call setting."
            else -> "Pick your earbuds" to "Choose which earbuds this should control."
        }
        val tint = if (ready) c.ok else c.warn
        return card(withAlpha(tint, if (c.dark) 0x26 else 0x1A), withAlpha(tint, 0x66)) {
            val dot = View(context).apply {
                background = oval(tint)
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(10) }
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(dot)
                addView(tv(title, 20f, c.text, bold = true))
            }
            addView(row)
            addView(tv(sub, 14f, c.sub).apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun setupCard(perms: Boolean, sh: Int, addr: String?): View = card {
        addView(tv("Setup", 16f, c.text, bold = true))
        addView(
            checkRow(
                perms, "Permissions",
                if (perms) "Phone and Bluetooth allowed" else "Needed to see calls and your earbuds",
                if (perms) null else "Allow"
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.BLUETOOTH_CONNECT), 1
                )
            }
        )
        val shSub = when (sh) {
            0 -> "Not running. Start it, then come back."
            1 -> "Running. Permission needed."
            else -> "Running and allowed"
        }
        val shAction = when (sh) {
            0 -> "Open Shizuku"
            1 -> "Allow"
            else -> null
        }
        addView(
            checkRow(sh == 2, "Shizuku", shSub, shAction) {
                if (sh == 0) openShizuku() else Shizuku.requestPermission(100)
            }
        )
        val name = if (perms) bonded().firstOrNull { it.address == addr }?.name else null
        addView(
            checkRow(addr != null, "Earbuds", if (addr == null) "Not chosen yet" else (name ?: addr), null) {}
        )
    }

    private fun earbudsCard(perms: Boolean, addr: String?): View = card {
        addView(tv("Your earbuds", 16f, c.text, bold = true))
        if (!perms) {
            addView(tv("Allow the Bluetooth permission to list your paired devices.", 13f, c.sub).apply {
                setPadding(0, dp(8), 0, 0)
            })
            return@card
        }
        val devices = bonded()
        if (devices.isEmpty()) {
            addView(tv("No paired devices found. Pair your earbuds in Bluetooth settings first.", 13f, c.sub).apply {
                setPadding(0, dp(8), 0, 0)
            })
            return@card
        }
        devices.forEach { d -> addView(deviceRow(d, d.address == addr)) }
    }

    private fun deviceRow(d: BluetoothDevice, selected: Boolean): View {
        val mark = TextView(this).apply {
            text = if (selected) "✓" else ""
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(c.onAccent)
            background = if (selected) oval(c.accent) else ring(c.sub)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(d.name ?: "Unknown device", 15f, c.text, bold = true))
            addView(tv(d.address, 12f, c.sub))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = RippleDrawable(
                ColorStateList.valueOf(withAlpha(c.accent, 0x22)),
                shape(
                    if (selected) withAlpha(c.accent, 0x1F) else Color.TRANSPARENT,
                    dp(14).toFloat(),
                    if (selected) c.accent else c.stroke,
                    dp(1)
                ),
                null
            )
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
            isClickable = true
            setOnClickListener {
                Prefs.setAddress(this@MainActivity, d.address)
                render()
            }
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(mark, LinearLayout.LayoutParams(dp(24), dp(24)))
        }
    }

    private fun testCard(ready: Boolean): View = card {
        addView(tv("Try it", 16f, c.text, bold = true))
        addView(
            tv(
                "Flip the earbuds' \"Phone calls\" setting by hand to check everything works. " +
                    "The same switch appears in Android's Bluetooth settings.",
                13f, c.sub
            ).apply { setPadding(0, dp(6), 0, dp(12)) }
        )
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            pill("Calls ON", true, ready) { test(true) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) }
        )
        row.addView(
            pill("Calls OFF", false, ready) { test(false) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) }
        )
        addView(row)
        if (!ready) {
            addView(tv("Finish the setup steps above to enable these buttons.", 12f, c.sub).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun logCard(): View = card {
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(tv("Activity log", 16f, c.text, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(pill("Copy", false, small = true) { copyLog() })
        head.addView(
            pill("Clear", false, small = true) {
                Prefs.clearLog(this@MainActivity)
                render()
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) }
        )
        addView(head)
        val log = Prefs.readLog(this@MainActivity).trim()
        val empty = log.isEmpty()
        addView(
            tv(
                if (empty) "Nothing yet. Events appear here when calls come in or you run a test." else log,
                12f,
                if (empty) c.sub else c.text,
                mono = !empty
            ).apply {
                setPadding(0, dp(10), 0, 0)
                setTextIsSelectable(true)
            }
        )
    }

    private fun footer(): View =
        tv(
            "After a phone reboot, start Shizuku again. Set this app's battery usage to " +
                "Unrestricted, and turn off any phone security feature that blocks debugging.",
            12f, c.sub
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(20), dp(8), 0)
        }

    // -------------------------------------------------------------- widgets

    private fun checkRow(ok: Boolean, title: String, sub: String, action: String?, onAction: () -> Unit): View {
        val badge = TextView(this).apply {
            text = if (ok) "✓" else "!"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(c.onStatus)
            background = oval(if (ok) c.ok else c.warn)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(tv(title, 15f, c.text, bold = true))
            addView(tv(sub, 13f, c.sub))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(badge, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (action != null) addView(pill(action, true, small = true) { onAction() })
        }
    }

    private fun card(
        fill: Int = c.card,
        stroke: Int = c.stroke,
        content: LinearLayout.() -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(fill, dp(20).toFloat(), stroke, dp(1))
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) }
        content()
    }

    private fun pill(
        label: String,
        filled: Boolean,
        enabled: Boolean = true,
        small: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = if (small) 13f else 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        isAllCaps = false
        setTextColor(if (filled) c.onAccent else c.accent)
        val padH = dp(if (small) 14 else 18)
        val padV = dp(if (small) 7 else 10)
        setPadding(padH, padV, padH, padV)
        val r = dp(24).toFloat()
        val base = if (filled) shape(c.accent, r) else shape(Color.TRANSPARENT, r, c.accent, dp(1))
        background = RippleDrawable(ColorStateList.valueOf(withAlpha(c.accent, 0x33)), base, null)
        alpha = if (enabled) 1f else 0.4f
        isClickable = enabled
        if (enabled) setOnClickListener { onClick() }
    }

    private fun tv(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        mono: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (mono) typeface = Typeface.MONOSPACE
    }

    private fun shape(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun oval(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun ring(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(dp(2), color)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
