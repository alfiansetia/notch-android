package app.notch.android.overlay

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatsState(
    val ramPct: Int = 0,
    val clock: String = "--:--",
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val downBps: Long = 0,
    val upBps: Long = 0,
    val glimpse: String? = null,
    val title: String? = null
)

fun formatRate(bps: Long): String = when {
    bps <= 0 -> "0 B/s"
    bps < 1024 -> "$bps B/s"
    bps < 1024 * 1024 -> "${bps / 1024} KB/s"
    else -> "%.1f MB/s".format(bps / 1048576f)
}

class StatsSampler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state

    private var running = false
    private var prevRx = TrafficStats.getTotalRxBytes()
    private var prevTx = TrafficStats.getTotalTxBytes()
    private var lastCharge: Boolean? = null
    private var glimpseUntil = 0L
    private var glimpseText: String? = null
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (isActive) {
                _state.value = sample()
                delay(2000) // AFR-10.2: 2s, 0 saat screen-off (service pause via screenReceiver)
            }
        }
    }

    fun glimpse(text: String, ms: Long = 4000) {
        glimpseText = text
        glimpseUntil = System.currentTimeMillis() + ms
    }

    private fun sample(): StatsState {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val ramPct = if (mem.totalMem > 0) {
            ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt()
        } else 0

        val bi: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0) (level * 100 / scale) else -1
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // Charging edge glimpse 4s (AFR-10.4)
        if (lastCharge != null && lastCharge != charging) {
            glimpse(if (charging) "Charging" else "Unplugged")
        }
        if (batteryPct == 100 && charging) glimpse("Fully charged")
        lastCharge = charging

        // Network rates via TrafficStats deltas (AFR-10.1)
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val down = if (rx < 0 || prevRx < 0) 0 else ((rx - prevRx).coerceAtLeast(0) / 2)
        val up = if (tx < 0 || prevTx < 0) 0 else ((tx - prevTx).coerceAtLeast(0) / 2)
        prevRx = rx
        prevTx = tx

        val g = if (System.currentTimeMillis() < glimpseUntil) glimpseText else null

        return StatsState(
            ramPct = ramPct,
            clock = timeFmt.format(Date()),
            batteryPct = batteryPct,
            charging = charging,
            downBps = down,
            upBps = up,
            glimpse = g,
            title = _state.value.title
        )
    }

    fun setTitle(title: String?) {
        _state.value = _state.value.copy(title = title)
    }
}
