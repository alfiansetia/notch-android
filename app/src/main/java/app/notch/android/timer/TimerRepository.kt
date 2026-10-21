package app.notch.android.timer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.timerStore by preferencesDataStore("notch_timer")
private val MODE = stringPreferencesKey("mode")
private val DEADLINE = longPreferencesKey("deadline_ms")
private val DURATION = longPreferencesKey("duration_ms")
private val SW_BASE = longPreferencesKey("sw_base_ms")

class TimerRepository(private val context: Context) {
    val persisted: Flow<PersistedTimer> = context.timerStore.data.map { p ->
        PersistedTimer(
            mode = p[MODE] ?: "none",
            deadlineMs = p[DEADLINE] ?: 0,
            durationMs = p[DURATION] ?: 0,
            swBaseMs = p[SW_BASE] ?: 0
        )
    }

    suspend fun startCountdown(durationMs: Long) {
        val deadline = System.currentTimeMillis() + durationMs
        context.timerStore.edit {
            it[MODE] = "countdown"
            it[DEADLINE] = deadline
            it[DURATION] = durationMs
        }
        TimerAlarm.schedule(context, deadline)
    }

    suspend fun startStopwatch() {
        context.timerStore.edit {
            it[MODE] = "stopwatch"
            it[SW_BASE] = System.currentTimeMillis()
        }
        TimerAlarm.cancel(context)
    }

    suspend fun clear() {
        context.timerStore.edit {
            it[MODE] = "none"
            it[DEADLINE] = 0
        }
        TimerAlarm.cancel(context)
    }
}

data class PersistedTimer(
    val mode: String,
    val deadlineMs: Long,
    val durationMs: Long,
    val swBaseMs: Long
)
