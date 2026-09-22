package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelationTest {

    @Test
    fun hexToRgb_parsesValidHex() {
        assertEquals(RgbColor(250, 244, 200), hexToRgb("#FAF4C8"))
        assertEquals(RgbColor(0, 0, 0), hexToRgb("#000000"))
        assertEquals(RgbColor(255, 255, 255), hexToRgb("FFFFFF"))
    }

    @Test
    fun hexToRgb_rejectsInvalid() {
        assertNull(hexToRgb("red"))
        assertNull(hexToRgb("#FFFF"))
        assertNull(hexToRgb(""))
    }

    @Test
    fun colorDistance_identityAndAxes() {
        assertEquals(0.0, colorDistance(RgbColor(10, 20, 30), RgbColor(10, 20, 30)), 1e-9)
        val d = colorDistance(RgbColor(0, 0, 0), RgbColor(255, 0, 0))
        assertTrue("黑红感知色差应大于 0", d > 50.0)
    }

    @Test
    fun findClosestPaletteColor_picksNearest() {
        val palette = listOf(
            PaletteColor("A", "#FF0000", RgbColor(255, 0, 0)),
            PaletteColor("B", "#00FF00", RgbColor(0, 255, 0)),
            PaletteColor("C", "#0000FF", RgbColor(0, 0, 255))
        )
        assertEquals("A", findClosestPaletteColor(RgbColor(250, 5, 5), palette).key)
        assertEquals("B", findClosestPaletteColor(RgbColor(5, 250, 5), palette).key)
        assertEquals("C", findClosestPaletteColor(RgbColor(5, 5, 250), palette).key)
    }

    @Test
    fun findClosestPaletteColor_emptyReturnsErrFallback() {
        val c = findClosestPaletteColor(RgbColor(1, 1, 1), emptyList())
        assertEquals("ERR", c.key)
    }
}