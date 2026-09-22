package com.perlerbeads.generator.data

import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PaletteColor
import com.perlerbeads.generator.model.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {

    private val samplePalette = listOf(
        PaletteColor("A01", "#FF0000", RgbColor(255, 0, 0)),
        PaletteColor("A02", "#0000FF", RgbColor(0, 0, 255)),
        PaletteColor("W01", "#FFFFFF", RgbColor(255, 255, 255))
    )

    @Test
    fun roundTripExportAndImport() {
        val red = MappedPixel("A01", "#FF0000", false)
        val blue = MappedPixel("A02", "#0000FF", false)
        val trans = com.perlerbeads.generator.model.transparentColorData
        val cells = arrayOf(
            arrayOf(red, trans),
            arrayOf(trans, blue)
        )
        val original = GridData(2, 2, cells, setOf("#FF0000", "#0000FF"), GridShape.SQUARE)

        val csv = CsvCodec.exportPatternCsv(original)
        assertEquals("#FF0000,TRANSPARENT\nTRANSPARENT,#0000FF\n", csv)

        val imported = CsvCodec.importPatternCsv(csv, samplePalette)
        assertEquals(2, imported.n)
        assertEquals(2, imported.m)
        assertEquals("A01", imported.cells[0][0].key)
        assertEquals("#FF0000", imported.cells[0][0].colorHex)
        assertFalse(imported.cells[0][0].isExternal)

        assertTrue(imported.cells[0][1].isExternal)
        assertTrue(imported.cells[1][0].isExternal)

        assertEquals("A02", imported.cells[1][1].key)
        assertEquals("#0000FF", imported.cells[1][1].colorHex)
        assertFalse(imported.cells[1][1].isExternal)
    }

    @Test
    fun importToleratesBomAndCase() {
        val csvWithBom = "\uFEFF#ff0000, transparent , \n erase , #0000ff , transparent "
        val imported = CsvCodec.importPatternCsv(csvWithBom, samplePalette)
        assertEquals(3, imported.n)
        assertEquals(2, imported.m)
        assertEquals("A01", imported.cells[0][0].key)
        assertTrue(imported.cells[0][1].isExternal)
        assertTrue(imported.cells[0][2].isExternal)
        assertTrue(imported.cells[1][0].isExternal)
        assertEquals("A02", imported.cells[1][1].key)
        assertTrue(imported.cells[1][2].isExternal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shoppingListCsvIsRejected() {
        val shoppingCsv = "色号,hex,数量\nA01,#FF0000,10\n合计,,10\n"
        CsvCodec.importPatternCsv(shoppingCsv, samplePalette)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedColumnsRejected() {
        val badCsv = "#FF0000,#0000FF\n#FF0000"
        CsvCodec.importPatternCsv(badCsv, samplePalette)
    }
}
