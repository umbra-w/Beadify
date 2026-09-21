package com.perlerbeads.generator.data

import com.perlerbeads.generator.model.CircleGeometry
import com.perlerbeads.generator.model.GridData
import com.perlerbeads.generator.model.GridShape
import com.perlerbeads.generator.model.MappedPixel
import com.perlerbeads.generator.model.PixelationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectCodecTest {

    private fun cells(): Array<Array<MappedPixel>> = arrayOf(
        arrayOf(MappedPixel("A01", "#000000", false), MappedPixel("M15", "#808080", false)),
        arrayOf(MappedPixel("T01", "#FFFFFF", false), MappedPixel("", "", isExternal = true))
    )

    @Test
    fun roundTripSquare() {
        val p = SavedProject("测试项目", GridShape.SQUARE, null, 50, PixelationMode.AVERAGE, true, "COCO", cells())
        val decoded = ProjectCodec.decode(ProjectCodec.encode(p))!!
        assertEquals("测试项目", decoded.name)
        assertEquals(GridShape.SQUARE, decoded.shape)
        assertEquals(null, decoded.circle)
        assertEquals(50, decoded.granularity)
        assertEquals(PixelationMode.AVERAGE, decoded.mode)
        assertEquals(true, decoded.dithering)
        assertEquals("COCO", decoded.colorSystemKey)
        assertEquals(2, decoded.n)
        assertEquals(2, decoded.m)
        assertEquals("A01", decoded.cells[0][0].key)
        assertEquals("#000000", decoded.cells[0][0].colorHex)
        assertEquals("M15", decoded.cells[0][1].key)
        assertEquals(TRANSPARENT_IS_EXTERNAL, decoded.cells[1][1].isExternal)
    }

    @Test
    fun roundTripCircle() {
        val circle = CircleGeometry(45.5f, 67.25f, 45f)
        val p = SavedProject("圆", GridShape.CIRCLE, circle, 90, PixelationMode.DOMINANT, false, "MARD", cells())
        val decoded = ProjectCodec.decode(ProjectCodec.encode(p))!!
        assertEquals(GridShape.CIRCLE, decoded.shape)
        assertEquals(45.5f, decoded.circle!!.centerX, 1e-4f)
        assertEquals(67.25f, decoded.circle!!.centerY, 1e-4f)
        assertEquals(45f, decoded.circle!!.radius, 1e-4f)
    }

    @Test
    fun nameWithSpacesAndUnderscoresSurvives() {
        val p = SavedProject("我的 项目_2", GridShape.SQUARE, null, 50, PixelationMode.DOMINANT, false, "MARD", cells())
        val decoded = ProjectCodec.decode(ProjectCodec.encode(p))!!
        assertEquals("我的 项目_2", decoded.name)
    }

    @Test
    fun corruptedReturnsNull() {
        assertNull(ProjectCodec.decode("不是项目文件"))
        assertNull(ProjectCodec.decode(""))
        // SIZE 与行数不符
        val broken = "PERLER_PROJECT 1\nNAME x\nSIZE 2 5\nSHAPE SQUARE\nSETTINGS 50 DOMINANT false MARD\nROWS\nA01|#000000,M15|#808080\n"
        assertNull(ProjectCodec.decode(broken))
        // 列数不符
        val broken2 = "PERLER_PROJECT 1\nNAME x\nSIZE 3 1\nSHAPE SQUARE\nSETTINGS 50 DOMINANT false MARD\nROWS\nA01|#000000,M15|#808080\n"
        assertNull(ProjectCodec.decode(broken2))
        // 非法格子 token
        val broken3 = "PERLER_PROJECT 1\nNAME x\nSIZE 2 1\nSHAPE SQUARE\nSETTINGS 50 DOMINANT false MARD\nROWS\nA01#000000,E\n"
        assertNull(ProjectCodec.decode(broken3))
    }

    @Test
    fun gridDataRoundTripThroughCells() {
        val p = SavedProject("g", GridShape.SQUARE, null, 50, PixelationMode.DOMINANT, false, "MARD", cells())
        val decoded = ProjectCodec.decode(ProjectCodec.encode(p))!!
        val grid = GridData(decoded.n, decoded.m, decoded.cells, emptySet(), decoded.shape)
        assertEquals(2, grid.n)
        assertEquals(2, grid.m)
        assertNotNull(grid.cells[0][0])
    }

    companion object {
        private const val TRANSPARENT_IS_EXTERNAL = true
    }
}
