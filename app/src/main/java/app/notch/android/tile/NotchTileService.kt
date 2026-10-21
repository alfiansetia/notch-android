package app.notch.android.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.notch.android.overlay.NotchService

class NotchTileService : TileService() {
    override fun onClick() {
        super.onClick()
        // TODO: toggle show/hide via DataStore flag; update qsTile.subtitle (On/Off/Paused)
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
        startForegroundService(Intent(this, NotchService::class.java))
    }
}
