package app.notch.android.tile

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.notch.android.data.SettingsRepository
import app.notch.android.overlay.NotchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotchTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val s = SettingsRepository(applicationContext).settings.first()
            qsTile.state = if (s.hidden) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= 30) {
                qsTile.subtitle = if (s.hidden) "Paused — tap to resume" else "On"
            }
            qsTile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val repo = SettingsRepository(applicationContext)
            val cur = repo.settings.first()
            repo.setHidden(!cur.hidden)
            if (cur.hidden) {
                startForegroundService(Intent(applicationContext, NotchService::class.java))
            } else {
                startService(Intent(applicationContext, NotchService::class.java).setAction(NotchService.ACTION_HIDE))
            }
            unlockAndRun { }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestUpdate(ctx: android.content.Context) {
            if (Build.VERSION.SDK_INT >= 24) {
                requestListeningState(ctx, ComponentName(ctx, NotchTileService::class.java))
            }
        }
    }
}
