package com.fjdei.mdlss5

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private external fun nativeVulkanInfo(): String
    companion object {
        init { System.loadLibrary("mdlss5") }
        private const val GLOBAL_CODM = "com.activision.callofduty.shooter"
        private const val GARENA_CODM = "com.garena.game.codm"
        private const val BG = 0xFF090D18.toInt()
        private const val PANEL = 0xFF151E30.toInt()
        private const val WHITE = Color.WHITE
        private const val MUTED = 0xFFB5C2D8.toInt()
        private const val ACCENT = 0xFF55C8FF.toInt()
    }

    private lateinit var report: TextView
    private lateinit var profileReport: TextView
    private var selectedProfile = "Balanced"
    private var detectedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        refreshDiagnostics()
    }

    private fun text(value: String, size: Float, color: Int = WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 7, 0, 7)
        }

    private fun button(value: String, action: () -> Unit): Button =
        Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun card(parent: LinearLayout, heading: String, body: String): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(PANEL)
        }
        box.addView(text(heading, 17f, WHITE, true))
        val bodyView = text(body, 13f, MUTED)
        box.addView(bodyView)
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        return bodyView
    }

    private fun render() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 32)
            setBackgroundColor(BG)
        }
        page.addView(text("MDLSS5", 32f, WHITE, true))
        page.addView(text("ANDROID GAMING COMPANION · V2.0", 12f, ACCENT, true))
        page.addView(text("Call of Duty: Mobile — first compatibility target", 17f, WHITE, true))
        page.addView(text("Device-aware gaming profiles, GPU diagnostics and a CODM launcher.", 14f, MUTED))
        page.addView(text("Important compatibility note", 17f, ACCENT, true))
        page.addView(text("This app does not inject code into CODM or replace its renderer. Android production security prevents an ordinary app from loading a third-party Vulkan layer into another non-debuggable game's process. These profiles are recommendations to apply inside CODM; they do not silently change the game's graphics settings.", 13f, MUTED))
        page.addView(button("Detect phone and graphics hardware") { refreshDiagnostics() })
        report = card(page, "Device and GPU diagnostics", "Detecting device…")
        page.addView(text("CODM game detection", 19f, WHITE, true))
        val gameStatus = card(page, "Installed game edition", "Checking Call of Duty: Mobile…")
        page.addView(button("Refresh CODM detection") {
            detectedPackage = findCodmPackage()
            gameStatus.text = codmStatus()
        })
        page.addView(button("Launch Call of Duty: Mobile") { launchCodm() })
        page.addView(text("Gaming profile", 19f, WHITE, true))
        page.addView(text("Choose the target behavior. The matching in-game settings are shown below for you to select in CODM.", 13f, MUTED))
        val profiles = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf("Performance", "Balanced", "Quality").forEach { name ->
            val choice = RadioButton(this).apply {
                text = name
                textSize = 15f
                setTextColor(WHITE)
                id = View.generateViewId()
                isChecked = name == selectedProfile
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedProfile = name
                        updateProfile()
                    }
                }
            }
            profiles.addView(choice)
        }
        page.addView(profiles)
        profileReport = card(page, "Recommended CODM settings", "")
        page.addView(text("What MDLSS5 can and cannot control", 18f, WHITE, true))
        page.addView(text("Available here: device/GPU identification, Vulkan capability reporting, memory and thermal status, CODM edition detection, profile recommendations and game launching. Not available to a normal third-party app: changing CODM's internal render resolution, injecting a neural upscaler, generating frames inside CODM, changing its shaders, or forcing its FPS cap.", 13f, MUTED))
        page.addView(text("No anti-cheat bypass or game-file modification is used.", 12f, ACCENT, true))
        scroll.addView(page)
        setContentView(scroll)
        updateProfile()
    }

    private fun findCodmPackage(): String? {
        for (pkg in listOf(GARENA_CODM, GLOBAL_CODM)) {
            try {
                packageManager.getApplicationInfo(pkg, 0)
                return pkg
            } catch (_: Exception) { }
        }
        return null
    }

    private fun codmStatus(): String = when (detectedPackage) {
        GARENA_CODM -> "Garena Call of Duty: Mobile is installed. Package: " + GARENA_CODM
        GLOBAL_CODM -> "Global Call of Duty: Mobile is installed. Package: " + GLOBAL_CODM
        else -> "CODM was not detected. Install the official regional version from Google Play."
    }

    private fun launchCodm() {
        val pkg = detectedPackage ?: findCodmPackage()
        detectedPackage = pkg
        if (pkg == null) {
            Toast.makeText(this, "Call of Duty: Mobile is not installed.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "CODM launch activity is unavailable.", Toast.LENGTH_LONG).show()
        } else startActivity(intent)
    }

    private fun updateProfile() {
        if (!::profileReport.isInitialized) return
        val advice = when (selectedProfile) {
            "Performance" -> "Graphics quality: Low (or the lowest stable option). Frame rate: highest option that remains stable during a full match. Disable optional visual effects if available. Prefer stable frame pacing and lower heat over a short-lived peak FPS."
            "Quality" -> "Graphics quality: High only if CODM offers it for this device and gameplay remains smooth. Frame rate: one step below the maximum if high settings cause heat or stutter. Enable optional effects selectively."
            else -> "Graphics quality: Medium. Frame rate: High or the highest consistently stable option. Keep optional effects moderate. This is the recommended starting point for balancing clarity, heat and responsiveness."
        }
        profileReport.text = selectedProfile + " profile\n" + advice + "\n\nThese are advisory settings only. Set them in CODM's own Audio and Graphics menu; availability varies by game build and device."
    }

    private fun glInfo(): String {
        var display = EGL14.EGL_NO_DISPLAY
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "OpenGL renderer: unavailable"
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "OpenGL renderer: EGL initialization failed"
            val configAttrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4, EGL14.EGL_SURFACE_TYPE, 1, EGL14.EGL_NONE)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0) || count[0] == 0) return "OpenGL renderer: no EGL config"
            val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
            val surfAttrs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttrs, 0)
            if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE ||
                !EGL14.eglMakeCurrent(display, surface, surface, context)) return "OpenGL renderer: context creation failed"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val versionString = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
            return "OpenGL ES: " + versionString + "\nGPU vendor: " + vendor + "\nGPU renderer: " + renderer
        } catch (e: Exception) {
            return "OpenGL renderer unavailable: " + e.message
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun refreshDiagnostics() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val totalRam = mem.totalMem / (1024L * 1024L)
        val freeRam = mem.availMem / (1024L * 1024L)
        val cpu = Runtime.getRuntime().availableProcessors()
        val refreshHz = if (Build.VERSION.SDK_INT >= 30) this.display?.refreshRate
            ?: resources.displayMetrics.refreshRate else windowManager.defaultDisplay.refreshRate
        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempText = if (temp > 0) String.format("%.1f°C", temp / 10f) else "Unavailable"
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            when (getSystemService(PowerManager::class.java).currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                else -> "Unknown"
            }
        } else "Not exposed by Android version"
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
        val abis = Build.SUPPORTED_ABIS.joinToString()
        val vulkan = try { nativeVulkanInfo() } catch (e: Exception) { "Vulkan query failed: " + e.message }
        detectedPackage = findCodmPackage()
        report.text = "Phone: " + Build.MANUFACTURER + " " + Build.MODEL +
            "\nSoC / hardware: " + soc +
            "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
            "\nCPU logical cores: " + cpu +
            "\nRAM: " + totalRam + " MB total · " + freeRam + " MB currently available" +
            "\nScreen refresh rate: " + String.format("%.1f", refreshHz) + " Hz" +
            "\nBattery temperature: " + tempText +
            "\nThermal status: " + thermal +
            "\nSupported ABIs: " + abis +
            "\n\n" + glInfo() + "\n\n" + vulkan +
            "\n\nCODM: " + (detectedPackage ?: "not detected")
        updateProfile()
    }
}
