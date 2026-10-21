package app.notch.android.overlay

// Tablet adaptif: murni fungsi biar bisa unit-test.
object AdaptiveLayout {
    const val PANEL_MAX_DP = 380
    const val TABLET_BREAKPOINT_DP = 600

    fun isTablet(screenWidthDp: Int): Boolean = screenWidthDp >= TABLET_BREAKPOINT_DP

    // PRD AFR-1.5: panel max 380 wide, capped 90% lebar layar.
    fun panelWidthDp(screenWidthDp: Int): Int =
        minOf(PANEL_MAX_DP, (screenWidthDp * 0.9f).toInt())

    // Pill phone fixed; di tablet skala 1.25x biar tak kekecilan di 10".
    fun pillWidthDp(size: Int, screenWidthDp: Int): Int {
        val base = when (size) {
            0 -> 160
            2 -> 210
            else -> 180
        }
        return if (isTablet(screenWidthDp)) (base * 1.25f).toInt() else base
    }

    fun pillHeightDp(size: Int, screenWidthDp: Int): Int {
        val base = when (size) {
            0 -> 30
            2 -> 38
            else -> 32
        }
        return if (isTablet(screenWidthDp)) (base * 1.25f).toInt() else base
    }
}
