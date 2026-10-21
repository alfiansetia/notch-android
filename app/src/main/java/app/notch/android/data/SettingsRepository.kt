package app.notch.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("notch_settings")

private val HIDDEN = booleanPreferencesKey("hidden")
private val START_AT_BOOT = booleanPreferencesKey("start_at_boot")
private val PILL_SIZE = intPreferencesKey("pill_size")
private val Y_OFFSET = intPreferencesKey("y_offset_dp")
private val AUTO_COLLAPSE = intPreferencesKey("auto_collapse_secs")

class SettingsRepository(private val context: Context) {

    val settings: Flow<NotchSettings> = context.dataStore.data.map { p ->
        NotchSettings(
            hidden = p[HIDDEN] ?: false,
            startAtBoot = p[START_AT_BOOT] ?: false,
            pillSize = (p[PILL_SIZE] ?: 1).coerceIn(0, 2),
            yOffsetDp = (p[Y_OFFSET] ?: 0).coerceIn(0, 24),
            autoCollapseSecs = (p[AUTO_COLLAPSE] ?: 8).coerceIn(0, 60)
        )
    }

    suspend fun setHidden(hidden: Boolean) {
        context.dataStore.edit { it[HIDDEN] = hidden }
    }

    suspend fun setStartAtBoot(enabled: Boolean) {
        context.dataStore.edit { it[START_AT_BOOT] = enabled }
    }

    suspend fun setPillSize(size: Int) {
        context.dataStore.edit { it[PILL_SIZE] = size.coerceIn(0, 2) }
    }

    suspend fun setYOffset(dp: Int) {
        context.dataStore.edit { it[Y_OFFSET] = dp.coerceIn(0, 24) }
    }

    suspend fun setAutoCollapse(secs: Int) {
        context.dataStore.edit { it[AUTO_COLLAPSE] = secs.coerceIn(0, 60) }
    }
}
