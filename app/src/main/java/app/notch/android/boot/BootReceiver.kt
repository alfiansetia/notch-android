package app.notch.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.notch.android.data.SettingsRepository
import app.notch.android.overlay.NotchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED
            )
        ) return
        // Direct-boot-unaware: jalan setelah unlock. Hormati toggle (default off).
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = SettingsRepository(context.applicationContext).settings.first()
                if (s.startAtBoot && !s.hidden) {
                    context.startForegroundService(Intent(context, NotchService::class.java))
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
