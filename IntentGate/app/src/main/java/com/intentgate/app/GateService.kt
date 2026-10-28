package com.intentgate.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.*

class GateService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var overlay: android.view.View? = null
    private var foreground: String? = null
    private var allowedPackage: String? = null
    private var deadline = 0L
    private var lastPackage: String? = null
    private val tick = object : Runnable {
        override fun run() {
            if (foreground != null && isProtected(foreground!!) && overlay == null &&
                (allowedPackage != foreground || SystemClock.elapsedRealtime() >= deadline)) showGate(foreground!!)
            handler.postDelayed(this, 500)
        }
    }
    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.removeCallbacks(tick)
        handler.post(tick)
    }
    private fun isProtected(pkg: String): Boolean {
        val prefs = getSharedPreferences("MainActivity", MODE_PRIVATE)
        return (pkg == "org.telegram.messenger" && prefs.getBoolean("telegram", true)) ||
            (pkg == "com.instagram.android" && prefs.getBoolean("instagram", false))
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (pkg != lastPackage) {
            lastPackage = pkg
            // A new visit always requires a new intention, even if a previous visit had time left.
            if (pkg != allowedPackage) { allowedPackage = null; deadline = 0L }
        }
        foreground = pkg
        if (!isProtected(pkg)) {
            // Invalidate the session on departure; the next entry must ask again.
            allowedPackage = null
            deadline = 0L
            dismissGate()
        } else if (overlay == null && (allowedPackage != pkg || SystemClock.elapsedRealtime() >= deadline)) showGate(pkg)
    }
    private fun showGate(pkg: String) {
        if (overlay != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.rgb(248, 249, 252))
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply { text = "Why are you opening this app?"; textSize = 24f; setTextColor(android.graphics.Color.BLACK) }
        val purpose = EditText(this).apply { hint = "Your purpose (required)"; isSingleLine = false; minLines = 2 }
        val minutes = Spinner(this)
        val choices = listOf(5, 10, 15, 20, 30, 45, 60)
        minutes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { "$it minutes" })
        val button = Button(this).apply { text = "Continue to app"; isAllCaps = false }
        val leave = Button(this).apply { text = "Leave app"; isAllCaps = false }
        root.addView(title)
        root.addView(purpose, LinearLayout.LayoutParams(-1, -2))
        root.addView(minutes, LinearLayout.LayoutParams(-1, -2))
        root.addView(button, LinearLayout.LayoutParams(-1, -2))
        root.addView(leave, LinearLayout.LayoutParams(-1, -2))
        button.setOnClickListener {
            if (purpose.text.toString().isBlank()) { purpose.error = "Enter a purpose"; return@setOnClickListener }
            val selected = choices[minutes.selectedItemPosition]
            allowedPackage = pkg
            deadline = SystemClock.elapsedRealtime() + selected * 60_000L
            getSharedPreferences("sessions", MODE_PRIVATE).edit().putString("last_purpose", purpose.text.toString().trim()).putInt("last_minutes", selected).apply()
            dismissGate()
        }
        leave.setOnClickListener { dismissGate(); performGlobalAction(GLOBAL_ACTION_HOME) }
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        try { wm.addView(root, params); overlay = root } catch (_: Exception) { overlay = null }
    }
    private fun dismissGate() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }
    override fun onInterrupt() {}
    override fun onDestroy() { handler.removeCallbacks(tick); dismissGate(); super.onDestroy() }
}
