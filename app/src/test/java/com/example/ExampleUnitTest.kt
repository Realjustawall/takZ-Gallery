package com.example

import com.example.core.localization.JalaliCalendar
import com.example.core.localization.PersianNormalizer
import com.example.core.media.PhotoEditPipelineState
import com.example.core.security.VaultCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `persian normalizer canonicalizes arabic variants and eastern digits`() {
        val raw = "ماشين قرمز كنار درخت ۸۴۹۲۰۱"
        val normalized = PersianNormalizer.normalizeForSearch(raw)
        assertEquals("ماشین قرمز کنار درخت 849201", normalized)

        val persianFormatted = PersianNormalizer.formatDigits("Code: 849201", usePersianDigits = true)
        assertEquals("Code: ۸۴۹۲۰۱", persianFormatted)
    }

    @Test
    fun `jalali solar hijri calendar conversion is accurate`() {
        // 2026-03-21 is 1 Farvardin 1405
        val nowruz = JalaliCalendar.gregorianToJalali(2026, 3, 21)
        assertEquals(1405, nowruz.year)
        assertEquals(1, nowruz.month)
        assertEquals(1, nowruz.day)
    }

    @Test
    fun `non-destructive photo edit pipeline state serializes and deserializes cleanly`() {
        val original = PhotoEditPipelineState(
            brightness = 0.25f,
            exposure = -0.15f,
            contrast = 0.4f,
            saturation = 0.3f,
            warmthTemp = 0.2f,
            rotationDegrees = 90,
            flipHorizontal = true,
            cropAspectRatio = "16:9",
            filterPreset = "PERSIAN_TURQUOISE",
            overlayText = "TakZ Studio",
            curveMaster = listOf(0f, 0.2f, 0.55f, 0.8f, 1f)
        )
        assertFalse(original.isIdentity())
        val serialized = original.serialize()
        val restored = PhotoEditPipelineState.deserialize(serialized)
        assertEquals(original.brightness, restored.brightness, 0.001f)
        assertEquals(original.exposure, restored.exposure, 0.001f)
        assertEquals(original.rotationDegrees, restored.rotationDegrees)
        assertEquals(original.flipHorizontal, restored.flipHorizontal)
        assertEquals(original.cropAspectRatio, restored.cropAspectRatio)
        assertEquals(original.filterPreset, restored.filterPreset)
        assertEquals(original.overlayText, restored.overlayText)
        assertEquals(original.curveMaster, restored.curveMaster)
    }

    @Test
    fun `sha256 cryptographic hash produces deterministic 64-char hex`() {
        val data1 = "TakZ Gallery Offline Content".toByteArray()
        val data2 = "TakZ Gallery Offline Content".toByteArray()
        val data3 = "Different Content".toByteArray()
        val h1 = VaultCryptoEngine.sha256Hex(data1)
        val h2 = VaultCryptoEngine.sha256Hex(data2)
        val h3 = VaultCryptoEngine.sha256Hex(data3)
        assertEquals(64, h1.length)
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
    }
}
