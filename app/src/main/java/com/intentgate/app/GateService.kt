package com.intentgate.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class GateService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var gateView: ScrollView? = null
    private var foregroundProtectedPackage: String? = null

    private val sessionPrefs by lazy { getSharedPreferences("sessions", MODE_PRIVATE) }
    private var visitPackage: String? = null
    private var visitStartMillis: Long = 0L
    private var visitPurpose: String = ""
    private var currentPurpose: String = ""

    // No restricted Settings.Secure keys are read. Common IME packages are ignored by name;
    // while the gate is visible, transient windows (IME, picker, dialogs) cannot dismiss it.
    private val knownKeyboardPackages = setOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.miui.android.inputmethod",
        "com.baidu.input_mi",
        "com.sohu.inputmethod.sogou",
        "com.tencent.qqpinyin",
        "com.iflytek.inputmethod",
        "com.touchtype.swiftkey",
        "com.samsung.android.honeyboard"
    )

    private val tick = object : Runnable {
        override fun run() {
            val pkg = foregroundProtectedPackage
            if (pkg != null && isProtected(pkg) && gateView == null && !hasValidSession(pkg)) {
                showGate(pkg)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun protectedPackages(): Set<String> =
        getSharedPreferences("MainActivity", MODE_PRIVATE)
            .getStringSet("protected_packages", setOf("org.telegram.messenger"))
            ?.toSet() ?: emptySet()

    private fun isProtected(pkg: String): Boolean = pkg in protectedPackages()

    private fun isKeyboardEvent(event: AccessibilityEvent, pkg: String): Boolean {
        if (pkg in knownKeyboardPackages) return true
        val cls = event.className?.toString()?.lowercase().orEmpty()
        return cls.contains("inputmethod") || cls.contains("input_method") ||
            cls.contains("latinime") || cls.contains("keyboard")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Window-state transitions are sufficient for foreground-app detection and reduce noise.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || isKeyboardEvent(event, pkg)) return

        if (isProtected(pkg)) {
            val previous = foregroundProtectedPackage
            if (previous != pkg) {
                finishVisit()
                foregroundProtectedPackage = pkg
                if (hasValidSession(pkg)) beginVisit(pkg)
            }
            if (gateView == null && !hasValidSession(pkg)) showGate(pkg)
            return
        }

        // A transient external window while the mandatory prompt is open (IME/file picker/dialog)
        // must not tear down the prompt. The user can explicitly choose “Leave app”.
        if (gateView != null) return

        finishVisit()
        foregroundProtectedPackage = null
        // Do not erase the per-app deadline: reopening the app within the selected allowance
        // must not ask again, including after a picker or video activity.
        dismissGate()
    }

    private fun sessionKey(pkg: String): String = pkg.replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun hasValidSession(pkg: String): Boolean =
        sessionPrefs.getLong("deadline_${sessionKey(pkg)}", 0L) > System.currentTimeMillis()

    private fun saveSession(pkg: String, minutes: Int, purpose: String) {
        val key = sessionKey(pkg)
        sessionPrefs.edit()
            .putLong("deadline_$key", System.currentTimeMillis() + minutes * 60_000L)
            .putString("last_purpose_$key", purpose)
            .putInt("last_minutes_$key", minutes)
            .apply()
        currentPurpose = purpose
        // A new intention starts a new history segment when the previous allowance expired.
        if (visitPackage == pkg) finishVisit()
        beginVisit(pkg)
    }

    private fun beginVisit(pkg: String) {
        if (visitPackage == pkg) return
        visitPackage = pkg
        visitStartMillis = System.currentTimeMillis()
        visitPurpose = sessionPrefs.getString("last_purpose_${sessionKey(pkg)}", currentPurpose).orEmpty()
    }

    private fun finishVisit() {
        val pkg = visitPackage ?: return
        val end = System.currentTimeMillis()
        val start = visitStartMillis
        if (start > 0L && end >= start) {
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
            UsageHistory.add(this, pkg, appName, visitPurpose, start, end)
        }
        visitPackage = null
        visitStartMillis = 0L
        visitPurpose = ""
    }

    private fun showGate(pkg: String) {
        if (gateView != null) return

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(245, 247, 251))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(44, 36, 44, 36)
        }
        val customPrompt = getSharedPreferences("MainActivity", MODE_PRIVATE)
            .getString("custom_prompt_text", "").orEmpty().trim()
        val title = TextView(this).apply {
            text = if (customPrompt.isNotBlank()) customPrompt else "Why are you opening this app?"
            textSize = 22f
            setTextColor(Color.rgb(25, 35, 52))
            setPadding(0, 0, 0, 22)
        }
        val purpose = EditText(this).apply {
            hint = "Your purpose (required)"
            textSize = 16f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(18, 14, 18, 14)
        }
        val minutes = Spinner(this)
        val choices = listOf(5, 10, 15, 20, 30, 45, 60)
        minutes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { "$it minutes" })
        val continueButton = Button(this).apply { text = "Continue to app"; isAllCaps = false }
        val leaveButton = Button(this).apply { text = "Leave app"; isAllCaps = false }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(purpose, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })
        root.addView(minutes, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        root.addView(continueButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(leaveButton, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(root)

        continueButton.setOnClickListener {
            val reason = purpose.text.toString().trim()
            if (reason.isBlank()) {
                purpose.error = "Enter a purpose"
                purpose.requestFocus()
                return@setOnClickListener
            }
            saveSession(pkg, choices[minutes.selectedItemPosition], reason)
            dismissGate()
        }
        leaveButton.setOnClickListener {
            dismissGate()
            foregroundProtectedPackage = null
            finishVisit()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        try {
            windowManager.addView(scroll, params)
            gateView = scroll
        } catch (_: Exception) {
            gateView = null
        }
    }

    private fun dismissGate() {
        gateView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) { }
        }
        gateView = null
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        finishVisit()
        dismissGate()
        super.onDestroy()
    }
}
