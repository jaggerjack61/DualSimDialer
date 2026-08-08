package com.dualsimdialer.app

import com.dualsimdialer.app.util.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorUtilsTest {
    @Test fun foregroundMeetsReadableBlackOrWhiteChoice() {
        assertEquals(0xFFFFFFFF.toInt(), ColorUtils.foregroundFor(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), ColorUtils.foregroundFor(0xFFFFFFFF.toInt()))
    }

    @Test fun hexSupportsRgbAndArgb() {
        assertEquals(0xFF6750A4.toInt(), ColorUtils.parseHex("#6750A4"))
        assertEquals(0x806750A4.toInt(), ColorUtils.parseHex("806750A4"))
        assertNotNull(ColorUtils.parseHex("#006A6A"))
        assertTrue(ColorUtils.parseHex("#nope") == null)
    }
}
