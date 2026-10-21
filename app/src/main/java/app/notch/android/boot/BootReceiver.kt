package app.notch.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.notch.android.overlay.NotchService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED
            )
        ) return
        // TODO: cek DataStore "start at boot" (default off, PRD AFR-5.1)
        // if (enabled) context.startForegroundService(Intent(context, NotchService::class.java))
    }
}
