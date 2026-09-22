package com.perlerbeads.generator.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PaletteDataAssetsTest {

    private val assetsDir = File("src/main/assets")

    private fun loadJsonArray(relPath: String): JSONArray {
        val f = File(assetsDir, relPath)
        assertTrue("Asset file must exist: ${f.absolutePath}", f.exists())
        return JSONArray(f.readText(Charsets.UTF_8))
    }

    private fun loadJsonObject(relPath: String): JSONObject {
        val f = File(assetsDir, relPath)
        assertTrue("Asset file must exist: ${f.absolutePath}", f.exists())
        return JSONObject(f.readText(Charsets.UTF_8))
    }

    private fun validateColorItem(item: JSONObject) {
        val code = item.getString("code")
        assertFalse("Code cannot be empty", code.isBlank())

        val hex = item.getString("hex")
        assertTrue("Hex must start with # and be 7 chars ($hex)", hex.startsWith("#") && hex.length == 7)

        val rgbArr = item.getJSONArray("rgb")
        assertEquals(3, rgbArr.length())
        for (i in 0 until 3) {
            val v = rgbArr.getInt(i)
            assertTrue("RGB component must be in 0..255 ($v)", v in 0..255)
        }
    }

    @Test
    fun testArtkalSPalette_199ColorsValid() {
        val arr = loadJsonArray("palettes/artkal_s.json")
        assertEquals(199, arr.length())

        val codes = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            validateColorItem(obj)
            val code = obj.getString("code")
            assertTrue("Artkal S code must start with S ($code)", code.startsWith("S"))
            assertTrue("Duplicate code found: $code", codes.add(code))
        }
    }

    @Test
    fun testArtkalCPalette_174ColorsValid() {
        val arr = loadJsonArray("palettes/artkal_c.json")
        assertEquals(174, arr.length())

        val codes = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            validateColorItem(obj)
            val code = obj.getString("code")
            assertTrue("Artkal C code must start with C ($code)", code.startsWith("C"))
            assertTrue("Duplicate code found: $code", codes.add(code))
        }
    }

    @Test
    fun testArtkalAPalette_145ColorsValid() {
        val arr = loadJsonArray("palettes/artkal_a.json")
        assertEquals(145, arr.length())

        val codes = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            validateColorItem(obj)
            val code = obj.getString("code")
            assertTrue("Artkal A code must start with A ($code)", code.startsWith("A"))
            assertTrue("Duplicate code found: $code", codes.add(code))
        }
    }

    @Test
    fun testPerlerPalette_103ColorsValid() {
        val arr = loadJsonArray("palettes/perler.json")
        assertEquals(103, arr.length())

        val codes = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            validateColorItem(obj)
            val code = obj.getString("code")
            val displayCode = obj.getString("displayCode")
            assertTrue("Perler raw code starts with 80- ($code)", code.startsWith("80-"))
            assertTrue("Perler display code starts with P ($displayCode)", displayCode.startsWith("P"))
            assertTrue("Duplicate code found: $code", codes.add(code))
        }
    }

    @Test
    fun testHamaPalette_92ColorsValid() {
        val arr = loadJsonArray("palettes/hama.json")
        assertEquals(92, arr.length())

        val codes = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            validateColorItem(obj)
            val code = obj.getString("code")
            assertTrue("Hama code starts with H ($code)", code.startsWith("H"))
            assertTrue("Duplicate code found: $code", codes.add(code))
        }
    }

    @Test
    fun testMardPalette_291ColorsValid() {
        val obj = loadJsonObject("color_system_mapping.json")
        assertEquals(291, obj.length())

        val keys = obj.keys()
        while (keys.hasNext()) {
            val hex = keys.next()
            assertTrue(hex.startsWith("#") && hex.length == 7)
            val mapping = obj.getJSONObject(hex)
            assertTrue(mapping.has("MARD"))
            assertTrue(mapping.has("COCO"))
            assertTrue(mapping.has("漫漫"))
            assertTrue(mapping.has("盼盼"))
            assertTrue(mapping.has("咪小窝"))
        }
    }
}
