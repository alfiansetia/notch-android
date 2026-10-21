package app.notch.android.overlay

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    val title: String? = null
)

class StatsSampler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state

    private var running = false
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (isActive) {
                _state.value = sample()
                delay(2000) // PRD AFR-10.2: 2s, paused saat screen-off (diatur service)
            }
        }
    }

    private fun sample(): StatsState {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val ramPct = if (mem.totalMem > 0) {
            ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt()
        } else 0

        val batteryIntent: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0) (level * 100 / scale) else -1

        return StatsState(
            ramPct = ramPct,
            clock = timeFmt.format(Date()),
            batteryPct = batteryPct,
            title = _state.value.title
        )
    }

    fun setTitle(title: String?) {
        _state.value = _state.value.copy(title = title)
    }
}
