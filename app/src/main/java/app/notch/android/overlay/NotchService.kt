package app.notch.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import app.notch.android.data.NotchSettings
import app.notch.android.data.SettingsRepository
import app.notch.android.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class NotchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: SettingsRepository
    private lateinit var sampler: StatsSampler
    private lateinit var wm: WindowManager

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private val expanded = MutableStateFlow(false)
    private var settings = NotchSettings()
    private var autoCollapseJob: Job? = null
    private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    sampler.start()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        sampler = StatsSampler(applicationContext, scope)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        startForeground(
            NOTIF_ID,
            buildNotif(hidden = false),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        sampler.start()
        scope.launch {
            repo.settings.collect {
                val wasHidden = settings.hidden
                settings = it
                refreshNotification()
                if (it.hidden) removeOverlay() else if (Settings.canDrawOverlays(this@NotchService)) {
                    if (overlayView == null) showOverlay() else relayout()
                }
                if (wasHidden != it.hidden) updateTileRequest()
            }
        }
        scope.launch {
            expanded.collect { isExpanded ->
                relayout()
                if (isExpanded) armAutoCollapse() else autoCollapseJob?.cancel()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> scope.launch { repo.setHidden(true) }
            ACTION_SHOW -> scope.launch { repo.setHidden(false) }
            ACTION_TOGGLE -> scope.launch { repo.setHidden(!settings.hidden) }
            ACTION_COLLAPSE -> expanded.value = false
        }
        if (!Settings.canDrawOverlays(this)) {
            // AFR-4.4: jangan crash/loop, idle di notif+tile
            removeOverlay()
            refreshNotification()
            return START_STICKY
        }
        if (!settings.hidden && overlayView == null) showOverlay()
        refreshNotification()
        return START_STICKY
    }

    // ---- overlay window ----

    private fun showOverlay() {
        if (overlayView != null) return
        val owner = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }
        lifecycleOwner = owner
        val view = ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, owner)
            ViewTreeViewModelStoreOwner.set(this, owner)
            ViewTreeSavedStateRegistryOwner.set(this, owner)
            setContent {
                MaterialTheme {
                    val isExpanded by expanded.collectAsState()
                    val stats by sampler.state.collectAsState()
                    NotchOverlay(
                        expanded = isExpanded,
                        pillSize = settings.pillSize,
                        stats = stats,
                        onPillTap = { expanded.value = true },
                        onCollapse = { expanded.value = false }
                    )
                }
            }
            // Tap-outside collapse (FLAG_WATCH_OUTSIDE_TOUCH -> ACTION_OUTSIDE)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE && expanded.value) {
                    expanded.value = false
                    true
                } else false
            }
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (settings.yOffsetDp * resources.displayMetrics.density).toInt()
        }
        overlayView = view
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            overlayView = null
        }
    }

    private fun relayout() {
        val view = overlayView ?: return
        val p = params ?: return
        p.y = (settings.yOffsetDp * resources.displayMetrics.density).toInt()
        // Back hanya saat expanded: lepas NOT_FOCUSABLE biar KEYCODE_BACK sampai.
        p.flags = if (expanded.value) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        try {
            wm.updateViewLayout(view, p)
        } catch (_: Exception) { }
        if (expanded.value) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                    event.action == android.view.KeyEvent.ACTION_UP
                ) {
                    expanded.value = false
                    true
                } else false
            }
        } else {
            view.setOnKeyListener(null)
        }
        // Re-compose biar pillSize/yOffset live (AFR-7.5)
        view.setContent {
            MaterialTheme {
                val isExpanded by expanded.collectAsState()
                val stats by sampler.state.collectAsState()
                NotchOverlay(
                    expanded = isExpanded,
                    pillSize = settings.pillSize,
                    stats = stats,
                    onPillTap = { expanded.value = true },
                    onCollapse = { expanded.value = false }
                )
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
            overlayView = null
        }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        expanded.value = false
    }

    private fun armAutoCollapse() {
        autoCollapseJob?.cancel()
        val secs = settings.autoCollapseSecs
        if (secs <= 0) return
        autoCollapseJob = scope.launch {
            delay(secs * 1000L)
            expanded.value = false
        }
    }

    // ---- notification (AFR-4.1) ----

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Notch service", NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun svcIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, NotchService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun settingsIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotif(hidden: Boolean): Notification {
        val toggle = if (hidden) {
            NotificationCompat.Action(0, "Show", svcIntent(ACTION_SHOW))
        } else {
            NotificationCompat.Action(0, "Hide", svcIntent(ACTION_HIDE))
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(if (hidden) "Notch hidden" else "Notch is running")
            .setContentText(if (hidden) "Tap Show to restore" else "Tap to show/hide")
            .setContentIntent(settingsIntent())
            .addAction(toggle)
            .addAction(0, "Settings", settingsIntent())
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotif(settings.hidden))
    }

    private fun updateTileRequest() {
        if (Build.VERSION.SDK_INT >= 24) {
            android.service.quicksettings.TileService.requestListeningState(
                this,
                android.content.ComponentName(this, app.notch.android.tile.NotchTileService::class.java)
            )
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL = "notch_service"
        const val ACTION_SHOW = "app.notch.SHOW"
        const val ACTION_HIDE = "app.notch.HIDE"
        const val ACTION_TOGGLE = "app.notch.TOGGLE"
        const val ACTION_COLLAPSE = "app.notch.COLLAPSE"

        // dipakai tes regresi AFR-3.3: window collapsed tak boleh > pill+slack
        fun maxCollapsedSizePx(pillSize: Int, density: Float): Pair<Int, Int> {
            val (w, h) = when (pillSize) {
                0 -> 160 to 30
                2 -> 210 to 38
                else -> 180 to 32
            }
            return ((w * density).toInt() + 1) to ((h * density).toInt() + 1)
        }
    }
}
