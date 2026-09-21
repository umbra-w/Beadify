package com.perlerbeads.generator.model

import com.perlerbeads.generator.algorithm.recalculateColorStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆形画板几何：与 GridRenderer 的像素级圆形裁剪语义对齐。
 * offset=0 → 圆覆盖图案左/上部；offset=1 → 右/下部；0.5 → 居中。
 */
class CircleGeometryTest {

    @Test
    fun squareGridCenteredCoversMiddleExcludesCorners() {
        val geo = circleGeometry(4, 4, 0.5f, 0.5f)
        assertEquals(2f, geo.centerX, 1e-6f)
        assertEquals(2f, geo.centerY, 1e-6f)
        assertEquals(2f, geo.radius, 1e-6f)
        assertTrue(geo.contains(1, 1))
        assertTrue(geo.contains(2, 2))
        assertFalse("角落格子中心在圆外", geo.contains(0, 0))
        assertFalse(geo.contains(3, 3))
    }

    @Test
    fun portraitOffsetZeroCoversTop() {
        // n=2, m=4：outCells=2，offsetY=0 → 圆覆盖顶部 2 行
        val geo = circleGeometry(2, 4, 0.5f, 0f)
        assertEquals(1f, geo.centerY, 1e-6f)
        assertTrue(geo.contains(0, 0))
        assertTrue(geo.contains(1, 1))
        assertFalse(geo.contains(2, 0))
        assertFalse(geo.contains(3, 1))
    }

    @Test
    fun portraitOffsetOneCoversBottom() {
        val geo = circleGeometry(2, 4, 0.5f, 1f)
        assertEquals(3f, geo.centerY, 1e-6f)
        assertFalse(geo.contains(0, 0))
        assertFalse(geo.contains(1, 1))
        assertTrue(geo.contains(2, 0))
        assertTrue(geo.contains(3, 1))
    }

    @Test
    fun landscapeOffsetZeroCoversLeft() {
        // n=4, m=2：outCells=2，offsetX=0 → 圆覆盖左侧 2 列
        val geo = circleGeometry(4, 2, 0f, 0.5f)
        assertEquals(1f, geo.centerX, 1e-6f)
        assertTrue(geo.contains(0, 0))
        assertTrue(geo.contains(1, 1))
        assertFalse(geo.contains(0, 2))
        assertFalse(geo.contains(1, 3))
    }

    @Test
    fun landscapeOffsetOneCoversRight() {
        val geo = circleGeometry(4, 2, 1f, 0.5f)
        assertEquals(3f, geo.centerX, 1e-6f)
        assertFalse(geo.contains(0, 0))
        assertTrue(geo.contains(0, 2))
        assertTrue(geo.contains(1, 3))
    }

    @Test
    fun statsFilterCountsOnlyInsideCircle() {
        val red = MappedPixel("A01", "#FF0000", false)
        val cells = Array(2) { Array(2) { red } }
        // 无过滤：4 粒
        assertEquals(4, recalculateColorStats(cells).totalCount)
        // 过滤掉 (0,0)：3 粒
        val filtered = recalculateColorStats(cells) { r, c -> !(r == 0 && c == 0) }
        assertEquals(3, filtered.totalCount)
        assertEquals(3, filtered.counts["#FF0000"])
    }
}
