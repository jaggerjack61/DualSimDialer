package com.dualsimdialer.app.util

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

object ColorUtils {
    val presetArgb = listOf(
        0xFF6750A4.toInt(), // purple
        0xFF006A6A.toInt(), // teal
        0xFF0061A4.toInt(), // blue
        0xFF8C1D18.toInt(), // red
        0xFF7A5900.toInt(), // amber
        0xFF386A20.toInt(), // green
    )

    fun foregroundFor(backgroundArgb: Int): Int {
        val red = ((backgroundArgb shr 16) and 0xFF) / 255.0
        val green = ((backgroundArgb shr 8) and 0xFF) / 255.0
        val blue = (backgroundArgb and 0xFF) / 255.0
        fun linear(value: Double) = if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).let { it * it * it }
        val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        return if ((1.05 / (luminance + 0.05)) > ((luminance + 0.05) / 0.05)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }

    fun toColor(argb: Int): Color = Color(argb)

    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val hsv = floatArrayOf(hue, saturation, value)
        val rgb = android.graphics.Color.HSVToColor(hsv)
        return rgb or 0xFF000000.toInt()
    }

    fun argbToHex(argb: Int): String = "#%08X".format(argb)

    fun parseHex(value: String): Int? {
        val cleaned = value.trim().removePrefix("#")
        val full = when (cleaned.length) {
            6 -> "FF$cleaned"
            8 -> cleaned
            else -> return null
        }
        return full.toLongOrNull(16)?.toInt()
    }

    fun argbToHsv(argb: Int): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        return hsv
    }

    fun textColorFor(backgroundArgb: Int): Color = Color(foregroundFor(backgroundArgb))
}
