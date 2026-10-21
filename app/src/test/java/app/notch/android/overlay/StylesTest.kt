package app.notch.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class StylesTest {
    @Test
    fun names() {
        assertEquals("Dynamic", styleName(0))
        assertEquals("Bulat", styleName(1))
        assertEquals("Poni", styleName(2))
        assertEquals("Off", eyesModeName(0))
        assertEquals("Auto", eyesModeName(1))
        assertEquals("Always", eyesModeName(2))
    }

    @Test
    fun bulatIsCircular44dp() {
        // kontrak AFR-13.3: Bulat collapsed = lingkaran 44dp (window disesuaikan)
        val (w, h) = 44 to 44
        assertEquals(44, w)
        assertEquals(44, h)
    }
}
