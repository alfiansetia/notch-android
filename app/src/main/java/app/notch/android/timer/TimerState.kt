package app.notch.android.timer

data class TimerState(
    // none | countdown | stopwatch
    val mode: String = "none",
    val remainingSecs: Long = 0,
    val stopwatchSecs: Long = 0,
    val label: String = ""
)

fun formatHms(totalSecs: Long): String {
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
