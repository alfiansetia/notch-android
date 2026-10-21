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
private val INBOX_ENABLED = booleanPreferencesKey("inbox_enabled")
private val INBOX_RETENTION = intPreferencesKey("inbox_retention_days")
private val TODO_ENABLED = booleanPreferencesKey("todo_enabled")
private val HYDRATE_ENABLED = booleanPreferencesKey("hydrate_enabled")
private val HYDRATE_CADENCE = intPreferencesKey("hydrate_cadence_mins")
private val EYE_ENABLED = booleanPreferencesKey("eye_enabled")
private val EYE_CADENCE = intPreferencesKey("eye_cadence_mins")

class SettingsRepository(private val context: Context) {

    val settings: Flow<NotchSettings> = context.dataStore.data.map { p ->
        NotchSettings(
            hidden = p[HIDDEN] ?: false,
            startAtBoot = p[START_AT_BOOT] ?: false,
            pillSize = (p[PILL_SIZE] ?: 1).coerceIn(0, 2),
            yOffsetDp = (p[Y_OFFSET] ?: 0).coerceIn(0, 24),
            autoCollapseSecs = (p[AUTO_COLLAPSE] ?: 8).coerceIn(0, 60),
            inboxEnabled = p[INBOX_ENABLED] ?: true,
            inboxRetentionDays = (p[INBOX_RETENTION] ?: 30).coerceIn(1, 365),
            todoEnabled = p[TODO_ENABLED] ?: true,
            hydrateEnabled = p[HYDRATE_ENABLED] ?: false,
            hydrateCadenceMins = (p[HYDRATE_CADENCE] ?: 60).coerceIn(15, 240),
            eyeEnabled = p[EYE_ENABLED] ?: false,
            eyeCadenceMins = (p[EYE_CADENCE] ?: 20).coerceIn(5, 120)
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

    suspend fun setInboxEnabled(v: Boolean) {
        context.dataStore.edit { it[INBOX_ENABLED] = v }
    }

    suspend fun setInboxRetention(days: Int) {
        context.dataStore.edit { it[INBOX_RETENTION] = days.coerceIn(1, 365) }
    }

    suspend fun setTodoEnabled(v: Boolean) {
        context.dataStore.edit { it[TODO_ENABLED] = v }
    }

    suspend fun setHydrateEnabled(v: Boolean) {
        context.dataStore.edit { it[HYDRATE_ENABLED] = v }
    }

    suspend fun setHydrateCadence(mins: Int) {
        context.dataStore.edit { it[HYDRATE_CADENCE] = mins.coerceIn(15, 240) }
    }

    suspend fun setEyeEnabled(v: Boolean) {
        context.dataStore.edit { it[EYE_ENABLED] = v }
    }

    suspend fun setEyeCadence(mins: Int) {
        context.dataStore.edit { it[EYE_CADENCE] = mins.coerceIn(5, 120) }
    }
}
