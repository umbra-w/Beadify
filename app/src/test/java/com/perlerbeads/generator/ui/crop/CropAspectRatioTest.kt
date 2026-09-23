package com.perlerbeads.generator.ui.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropAspectRatioTest {

    @Test
    fun squareImage_fittedRectMatches1to1() {
        val oldRect = Rect(0.05f, 0.05f, 0.95f, 0.95f)
        val fitted = computeFittedRect(oldRect, targetRatio = 1.0f, imgAspect = 1.0f)
        assertEquals(fitted.width, fitted.height, 0.001f)
        assertTrue(fitted.left >= 0f)
        assertTrue(fitted.right <= 1f)
        assertTrue(fitted.top >= 0f)
        assertTrue(fitted.bottom <= 1f)
    }

    @Test
    fun landscapeImage_fittedRectMatches1to1InPixels() {
        // 16:9 横图 (imgAspect = 16/9)
        val imgAspect = 16f / 9f
        val oldRect = Rect(0.1f, 0.1f, 0.9f, 0.9f)
        val fitted = computeFittedRect(oldRect, targetRatio = 1.0f, imgAspect = imgAspect)

        // 实际像素长宽比 = (fitted.width * 16) / (fitted.height * 9) 应为 1.0
        val pixelAspect = (fitted.width * imgAspect) / fitted.height
        assertEquals(1.0f, pixelAspect, 0.005f)
    }

    @Test
    fun portraitImage_fittedRectMatchesRatio1to2() {
        // 9:16 竖图 (imgAspect = 9/16)
        val imgAspect = 9f / 16f
        val oldRect = Rect(0.05f, 0.05f, 0.95f, 0.95f)
        val fitted = computeFittedRect(oldRect, targetRatio = 0.5f, imgAspect = imgAspect)

        // 像素长宽比 = (fitted.width * imgAspect) / fitted.height 应为 0.5
        val pixelAspect = (fitted.width * imgAspect) / fitted.height
        assertEquals(0.5f, pixelAspect, 0.005f)
    }

    @Test
    fun cornerDrag_preservesAspectRatio() {
        val imgAspect = 4f / 3f
        val targetRatio = 1.0f
        val init = computeFittedRect(Rect(0.1f, 0.1f, 0.9f, 0.9f), targetRatio, imgAspect)

        // 拖动右下角 (mode 5)
        val adjusted = adjustRectWithAspect(
            init, mode = 5, dxn = 0.1f, dyn = 0.05f,
            targetRatio = targetRatio, imgAspect = imgAspect
        )
        val pixelAspect = (adjusted.width * imgAspect) / adjusted.height
        assertEquals(1.0f, pixelAspect, 0.005f)
        assertTrue(adjusted.right <= 1f)
        assertTrue(adjusted.bottom <= 1f)
    }

    @Test
    fun edgeDrag_preservesAspectRatio() {
        val imgAspect = 1.0f
        val targetRatio = 2.0f // 2:1 横板
        val init = computeFittedRect(Rect(0.2f, 0.2f, 0.8f, 0.8f), targetRatio, imgAspect)

        // 拖动上边 (mode 6)
        val adjusted = adjustRectWithAspect(
            init, mode = 6, dxn = 0f, dyn = -0.05f,
            targetRatio = targetRatio, imgAspect = imgAspect
        )
        val pixelAspect = (adjusted.width * imgAspect) / adjusted.height
        assertEquals(2.0f, pixelAspect, 0.005f)
        assertTrue(adjusted.top >= 0f)
    }

    @Test
    fun newSelection_preservesAspectRatio() {
        val imgAspect = 16f / 9f
        val targetRatio = 0.5f // 1:2 竖板
        val anchor = Offset(0.2f, 0.2f)
        val cur = Offset(0.6f, 0.8f)

        val created = createNewSelectionWithAspect(anchor, cur, targetRatio, imgAspect)
        val pixelAspect = (created.width * imgAspect) / created.height
        assertEquals(0.5f, pixelAspect, 0.005f)
    }
}
