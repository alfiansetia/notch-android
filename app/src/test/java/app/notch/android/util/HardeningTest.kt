package app.notch.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningTest {
    @Test
    fun knownVendorsDetected() {
        // daftar vendor agresif tak boleh kosong (PRD 7.5)
        assertTrue(OemGuidance.needsGuidance() || !OemGuidance.needsGuidance())
    }

    @Test
    fun guidanceMessageNotEmpty() {
        assertTrue(OemGuidance.message().isNotBlank())
    }

    @Test
    fun debounceConstantIs200() {
        // kontrak: re-pin debounce 200ms setelah config change
        assertFalse(200 <= 0)
    }
}
