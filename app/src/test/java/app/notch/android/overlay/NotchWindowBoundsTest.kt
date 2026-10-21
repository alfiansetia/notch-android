package app.notch.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class NotchWindowBoundsTest {
    // AFR-3.3: collapsed window tak boleh > pill + 1px slack
    @Test
    fun collapsedBoundsMatchPill() {
        val density = 3f
        assertEquals((180 * density).toInt() + 1 to (32 * density).toInt() + 1,
            NotchService.maxCollapsedSizePx(1, density))
        assertEquals((160 * density).toInt() + 1 to (30 * density).toInt() + 1,
            NotchService.maxCollapsedSizePx(0, density))
        assertEquals((210 * density).toInt() + 1 to (38 * density).toInt() + 1,
            NotchService.maxCollapsedSizePx(2, density))
    }
}
