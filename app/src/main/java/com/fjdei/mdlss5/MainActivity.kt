package com.fjdei.mdlss5

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.widget.*
import kotlin.math.min

class MainActivity : Activity() {
    private external fun nativeVulkanInfo(): String
    companion object {
        private const val PICK_IMAGE = 501
        init { System.loadLibrary("mdlss5") }
    }
    private var sourceBitmap: Bitmap? = null
    private var resultBitmap: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var scaleLabel: TextView
    private lateinit var strength: SeekBar
    private var scaleFactor = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshDeviceReport()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 34, 24, 36)
            setBackgroundColor(Color.rgb(9, 13, 24))
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 10, 0, 10)
            }
        fun button(value: String, action: () -> Unit): Button = Button(this).apply {
            text = value; isAllCaps = false; setOnClickListener { action() }
        }
        val white = Color.WHITE
        val muted = Color.rgb(174, 188, 210)
        page.addView(label("MDLSS5", 32f, white, true))
        page.addView(label("MOBILE IMAGE ENHANCER · V1.0", 12f, Color.rgb(90, 190, 255), true))
        page.addView(label("Enhance an image", 21f, white, true))
        page.addView(label("Select a photo, upscale it, and apply adjustable sharpening. Processing runs locally on your phone.", 14f, muted))
        page.addView(button("Choose image from phone") { chooseImage() })
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(20, 27, 42))
            contentDescription = "Selected or enhanced image preview"
        }
        page.addView(preview, LinearLayout.LayoutParams(-1, 520).apply { topMargin = 12; bottomMargin = 12 })
        scaleLabel = label("Output size: 2×", 15f, white, true)
        page.addView(scaleLabel)
        val scaleChoices = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("2×", "3×", "4×").forEachIndexed { index, value ->
            scaleChoices.addView(RadioButton(this).apply {
                text = value; textSize = 14f; setTextColor(white); id = View.generateViewId()
                isChecked = index == 0
                setOnClickListener { scaleFactor = index + 2; scaleLabel.text = "Output size: ${scaleFactor}×" }
            })
        }
        page.addView(scaleChoices)
        page.addView(label("Sharpening strength", 15f, white, true))
        strength = SeekBar(this).apply { max = 100; progress = 35; contentDescription = "Sharpening strength" }
        page.addView(strength)
        page.addView(button("Enhance image") { enhance() })
        page.addView(button("Save enhanced image") { saveResult() })
        status = label("Choose an image to begin.", 14f, muted)
        page.addView(status)
        page.addView(label("Device graphics diagnostics", 20f, white, true))
        page.addView(label("Vulkan is checked separately from image processing. This version uses a local CPU image pipeline; it does not claim NVIDIA DLSS or GPU frame generation.", 13f, muted))
        page.addView(button("Refresh Vulkan report") { refreshDeviceReport() })
        scroll.addView(page)
        setContentView(scroll)
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE)
    }

    @Deprecated("Handled for compatibility with the minimal no-dependency project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri).use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream) ?: error("Unsupported image")
                    sourceBitmap = decoded.copy(Bitmap.Config.ARGB_8888, false)
                    resultBitmap = null
                    preview.setImageBitmap(sourceBitmap)
                    status.text = "Image loaded: ${decoded.width} × ${decoded.height}. Choose Enhance image."
                }
            } catch (e: Exception) { status.text = "Could not open image: ${e.message}" }
        }
    }

    private fun enhance() {
        val input = sourceBitmap ?: run { status.text = "Choose an image first."; return }
        try {
            val outW = min(input.width * scaleFactor, 4096)
            val outH = min(input.height * scaleFactor, 4096)
            val scaled = Bitmap.createScaledBitmap(input, outW, outH, true).copy(Bitmap.Config.ARGB_8888, true)
            val amount = strength.progress / 100f * 1.4f
            if (amount > 0f && outW > 2 && outH > 2) sharpen(scaled, amount)
            resultBitmap = scaled
            preview.setImageBitmap(scaled)
            status.text = "Enhanced: ${outW} × ${outH}. Save the result to your gallery."
        } catch (e: OutOfMemoryError) {
            status.text = "Image is too large for available memory. Try a smaller source image."
        } catch (e: Exception) { status.text = "Enhancement failed: ${e.message}" }
    }

    private fun sharpen(bitmap: Bitmap, amount: Float) {
        val w = bitmap.width; val h = bitmap.height
        val original = IntArray(w * h); bitmap.getPixels(original, 0, w, 0, 0, w, h)
        val output = original.copyOf()
        val shifts = intArrayOf(16, 8, 0)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val c = original[i]
            for (shift in shifts) {
                val center = (c shr shift) and 255
                val blur = (((original[i-w] shr shift) and 255) + ((original[i+w] shr shift) and 255) +
                    ((original[i-1] shr shift) and 255) + ((original[i+1] shr shift) and 255)) / 4f
                val v = (center + (center - blur) * amount).toInt().coerceIn(0, 255)
                output[i] = (output[i] and (255 shl shift).inv()) or (v shl shift)
            }
        }
        bitmap.setPixels(output, 0, w, 0, 0, w, h)
    }

    private fun saveResult() {
        val bitmap = resultBitmap ?: run { status.text = "Enhance an image before saving."; return }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "MDLSS5_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MDLSS5")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gallery could not create file")
            contentResolver.openOutputStream(uri).use { out ->
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) error("Could not write image")
            }
            status.text = "Saved enhanced image to Pictures/MDLSS5."
        } catch (e: Exception) { status.text = "Save failed: ${e.message}" }
    }

    private fun refreshDeviceReport() {
        val pm = packageManager
        val hasVersion = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        val hasLevel = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val report = try { nativeVulkanInfo() } catch (e: Exception) { "Native Vulkan check unavailable: ${e.message}" }
        if (::status.isInitialized) status.text = "Android Vulkan feature flags — version: ${if (hasVersion) "reported" else "not reported"}; level: ${if (hasLevel) "reported" else "not reported"}\n${report}"
    }
}
