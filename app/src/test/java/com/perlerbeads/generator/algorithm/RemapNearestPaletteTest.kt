package com.perlerbeads.generator.algorithm

import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import com.perlerbeads.generator.model.TRANSPARENT_KEY
import com.perlerbeads.generator.model.transparentColorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 色号系统快速切换的就近重映射。 */
class RemapNearestPaletteTest {

    private val newPalette = listOf(
        PaletteColor("B02", "#000000", RgbColor(0, 0, 0)),
        PaletteColor("W01", "#FFFFFF", RgbColor(255, 255, 255))
    )

    @Test
    fun hexInNewPaletteKeepsHexAndUpdatesKey() {
        val cells = arrayOf(arrayOf(MappedPixel("A01", "#000000", false)))
        val out = remapGridToNearestPalette(cells, newPalette)
        assertEquals("#000000", out[0][0].colorHex)
        assertEquals("B02", out[0][0].key) // key 已换成新色板体系
    }

    @Test
    fun nearestColorChosenWhenHexMissing() {
        val cells = arrayOf(arrayOf(MappedPixel("A01", "#101010", false)))
        val out = remapGridToNearestPalette(cells, newPalette)
        assertEquals("#000000", out[0][0].colorHex)
    }

    @Test
    fun sameHexMapsConsistently() {
        val cells = Array(3) { arrayOf(MappedPixel("A01", "#FEFEFE", false)) }
        val out = remapGridToNearestPalette(cells, newPalette)
        val hexes = out.map { it[0].colorHex }.toSet()
        assertEquals(1, hexes.size)
    }

    @Test
    fun externalAndTransparentUntouched() {
        val cells = arrayOf(
            arrayOf(transparentColorData, MappedPixel("A01", "#000000", false)),
            arrayOf(MappedPixel("X", "#FFFFFF", isExternal = true), MappedPixel("A01", TRANSPARENT_KEY, isExternal = true))
        )
        val out = remapGridToNearestPalette(cells, newPalette)
        assertEquals(TRANSPARENT_KEY, out[0][0].key)
        assertTrue(out[0][0].isExternal)
        assertTrue(out[1][0].isExternal)
        assertTrue(out[1][1].isExternal)
        assertEquals("#000000", out[0][1].colorHex)
    }

    @Test
    fun emptyPaletteReturnsOriginal() {
        val cells = arrayOf(arrayOf(MappedPixel("A01", "#000000", false)))
        val out = remapGridToNearestPalette(cells, emptyList())
        assertEquals("A01", out[0][0].key)
    }

    @Test
    fun worksWithinGridData() {
        val grid = GridData(2, 1, arrayOf(arrayOf(MappedPixel("A01", "#000000", false), MappedPixel("A02", "#FFFFFF", false))), emptySet(), GridShape.SQUARE)
        val out = remapGridToNearestPalette(grid.cells, newPalette)
        grid.cells = out
        assertEquals("B02", grid.cells[0][0].key)
        assertEquals("W01", grid.cells[0][1].key)
    }
}
