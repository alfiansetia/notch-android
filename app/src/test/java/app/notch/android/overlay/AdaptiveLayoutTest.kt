package app.notch.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun phonePanelFixed() {
        assertEquals(324, AdaptiveLayout.panelWidthDp(360)) // 90% dari 360
        assertFalse(AdaptiveLayout.isTablet(360))
    }

    @Test
    fun tabletPanelCapped380() {
        assertTrue(AdaptiveLayout.isTablet(800))
        assertEquals(380, AdaptiveLayout.panelWidthDp(800))
    }

    @Test
    fun tabletPillScaled() {
        assertEquals(225, AdaptiveLayout.pillWidthDp(1, 800)) // 180*1.25
        assertEquals(180, AdaptiveLayout.pillWidthDp(1, 360))
    }
}
