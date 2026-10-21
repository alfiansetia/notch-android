package app.notch.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat

class NotchService : Service() {

    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(
            NOTIF_ID,
            buildNotif(),
            // FOREGROUND_SERVICE_SPECIAL_USE, minSdk 29 -> guard for API 29+
            if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            // PRD AFR-4.4: jangan crash / loop, tunggu user grant via notif/tile/settings
            stopOverlay()
            return START_STICKY
        }
        showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this).apply {
            setContent { NotchPill(expanded = false) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0 // user-adjustable 0-24dp (PRD AFR-1.4)
        }
        overlayView = view
        wm.addView(view, params)
    }

    private fun stopOverlay() {
        overlayView?.let {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("notch_service", "Notch service", NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun buildNotif(): Notification =
        NotificationCompat.Builder(this, "notch_service")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Notch is running")
            .setContentText("Tap to show/hide")
            .setOngoing(true)
            .build()

    companion object {
        const val NOTIF_ID = 1001
    }
}
