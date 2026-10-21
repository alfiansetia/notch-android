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
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import app.notch.android.actions.DndController
import app.notch.android.actions.FlashlightController
import app.notch.android.data.HydrateRepository
import app.notch.android.data.InboxRepository
import app.notch.android.data.NotchSettings
import app.notch.android.data.SettingsRepository
import app.notch.android.data.TodoRepository
import app.notch.android.data.db.InboxItem
import app.notch.android.data.db.TodoItem
import app.notch.android.media.MediaObserver
import app.notch.android.reminders.ReminderScheduler
import app.notch.android.settings.SettingsActivity
import app.notch.android.util.MotionHelper
import app.notch.android.timer.TimerFinishReceiver
import app.notch.android.timer.TimerRepository
import app.notch.android.timer.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NotchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: SettingsRepository
    private lateinit var timerRepo: TimerRepository
    private lateinit var sampler: StatsSampler
    private lateinit var media: MediaObserver
    private lateinit var torch: FlashlightController
    private lateinit var wm: WindowManager

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private val expanded = MutableStateFlow(false)
    private val timerUi = MutableStateFlow(TimerState())
    private val torchOn = MutableStateFlow(false)
    private val inboxList = MutableStateFlow<List<InboxItem>>(emptyList())
    private val todoList = MutableStateFlow<List<TodoItem>>(emptyList())
    private val hydrateMl = MutableStateFlow(0)
    private val lastTouch = MutableStateFlow(System.currentTimeMillis())
    private lateinit var inboxRepo: InboxRepository
    private lateinit var todoRepo: TodoRepository
    private lateinit var hydrateRepo: HydrateRepository
    private var settings = NotchSettings()
    private var autoCollapseJob: Job? = null
    private var keepScreenOn = false
    private var configJob: Job? = null

    // PRD 7.5: re-pin setelah onConfigurationChanged, debounce 200ms (bukan saat rotasi).
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        configJob?.cancel()
        configJob = scope.launch {
            delay(200)
            if (Settings.canDrawOverlays(this@NotchService) && !settings.hidden) {
                if (overlayView == null) showOverlay() else relayout()
                refreshOverlay()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> Unit // sampler loop tetap jalan 2s tapi murah; screen-off pause = skip
                Intent.ACTION_SCREEN_ON -> sampler.start()
            }
        }
    }

    private val eyeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            sampler.glimpse("Eye break: 20-20-20")
            refreshOverlay()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        timerRepo = TimerRepository(applicationContext)
        inboxRepo = InboxRepository(applicationContext)
        todoRepo = TodoRepository(applicationContext)
        hydrateRepo = HydrateRepository(applicationContext)
        sampler = StatsSampler(applicationContext, scope)
        media = MediaObserver(applicationContext, scope)
        torch = FlashlightController(applicationContext)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannels()
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
        registerReceiver(eyeReceiver, IntentFilter("app.notch.EYE_BREAK"),
            Context.RECEIVER_NOT_EXPORTED)
        scope.launch {
            repo.settings.collect {
                val wasHidden = settings.hidden
                settings = it
                refreshNotification()
                ReminderScheduler.apply(
                    applicationContext, it.hydrateEnabled, it.hydrateCadenceMins,
                    it.eyeEnabled, it.eyeCadenceMins
                )
                scope.launch { inboxRepo.prune(it.inboxRetentionDays) }
                if (it.hidden) removeOverlay() else if (Settings.canDrawOverlays(this@NotchService)) {
                    if (overlayView == null) showOverlay() else relayout()
                }
                if (wasHidden != it.hidden) updateTileRequest()
                refreshOverlay()
            }
        }
        scope.launch { inboxRepo.observe().collect { inboxList.value = it; refreshOverlay() } }
        scope.launch { todoRepo.observe().collect { todoList.value = it; refreshOverlay() } }
        scope.launch { hydrateRepo.observeToday().collect { hydrateMl.value = it; refreshOverlay() } }
        // Eyes ticker: refresh tiap 5 dtk agar auto/doze/low-battery live (AFR-13.5)
        scope.launch {
            while (isActive) {
                delay(5000)
                refreshOverlay()
            }
        }
        scope.launch {
            expanded.collect { isExpanded ->
                relayout()
                if (isExpanded) armAutoCollapse() else autoCollapseJob?.cancel()
            }
        }
        // Timer ticker 1s: hitung remaining/stopwatch, finish -> pulse + notif
        scope.launch {
            timerRepo.persisted.collect { p ->
                while (isActive) {
                    val now = System.currentTimeMillis()
                    when (p.mode) {
                        "countdown" -> {
                            val rem = ((p.deadlineMs - now) / 1000).coerceAtLeast(0)
                            timerUi.value = TimerState("countdown", rem)
                            if (rem <= 0) {
                                timerRepo.clear()
                                sampler.glimpse("Timer done")
                                fireTimerNotif()
                                expanded.value = false
                                break
                            }
                        }
                        "stopwatch" -> {
                            timerUi.value = TimerState("stopwatch", 0, (now - p.swBaseMs) / 1000)
                        }
                        else -> timerUi.value = TimerState()
                    }
                    delay(1000)
                }
            }
        }
        // NowPlaying title -> collapsed slot fallback (AFR-9.5)
        scope.launch {
            combine(media.nowPlaying, timerUi) { np, t -> np to t }.collect { (np, t) ->
                val title = when {
                    t.mode == "countdown" -> "Timer ${t.remainingSecs}s"
                    t.mode == "stopwatch" -> "Stopwatch"
                    else -> np?.title
                }
                sampler.setTitle(title)
                refreshOverlay()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // PRD 7.5: listener bisa mati setelah update — re-check tiap start.
        media.refresh()
        when (intent?.action) {
            ACTION_HIDE -> scope.launch { repo.setHidden(true) }
            ACTION_SHOW -> scope.launch { repo.setHidden(false) }
            ACTION_TOGGLE -> scope.launch { repo.setHidden(!settings.hidden) }
            ACTION_COLLAPSE -> expanded.value = false
            ACTION_FLASH_INBOX -> {
                sampler.glimpse("Saved to inbox")
                expanded.value = false
                refreshOverlay()
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay()
            refreshNotification()
            return START_STICKY
        }
        if (!settings.hidden && overlayView == null) showOverlay()
        refreshNotification()
        return START_STICKY
    }

    // ---- overlay window ----

    private fun composeContent(view: ComposeView) {
        view.setContent {
            MaterialTheme {
                val isExpanded by expanded.collectAsState()
                val stats by sampler.state.collectAsState()
                val np by media.nowPlaying.collectAsState()
                val t by timerUi.collectAsState()
                val torchState by torchOn.collectAsState()
                val inbox by inboxList.collectAsState()
                val todos by todoList.collectAsState()
                val hydro by hydrateMl.collectAsState()
                val now = System.currentTimeMillis()
                val touchedAgo = now - lastTouch.collectAsState().value
                val timerIdle = t.mode == "none"
                val showEyes = settings.eyesMode == 2 ||
                    (settings.eyesMode == 1 && np?.title == null && timerIdle && touchedAgo > 60_000)
                val dozing = touchedAgo > 20_000
                val lowBatt = stats.batteryPct in 1..20
                NotchOverlay(
                    expanded = isExpanded,
                    pillSize = settings.pillSize,
                    stats = stats,
                    nowPlaying = np,
                    mediaGranted = MediaObserver.isListenerEnabled(this@NotchService),
                    timer = t,
                    torchOn = torchState,
                    dndOn = DndController.isDnd(this@NotchService),
                    inbox = inbox,
                    todos = todos,
                    hydrateMl = hydro,
                    showInbox = settings.inboxEnabled,
                    showTodo = settings.todoEnabled,
                    showHydrate = settings.hydrateEnabled,
                    style = settings.style,
                    showEyes = showEyes,
                    eyesDozing = dozing,
                    lowBattery = lowBatt,
                    reduceMotion = MotionHelper.isReducedMotion(this@NotchService),
                    onPillTap = { lastTouch.value = System.currentTimeMillis(); expanded.value = true },
                    onCollapse = { lastTouch.value = System.currentTimeMillis(); expanded.value = false },
                    onPlayPause = { media.toggle() },
                    onNext = { media.next() },
                    onPrev = { media.prev() },
                    onSeek = { v -> media.seekTo(v.toLong()) },
                    onMediaGrant = { startActivity(MediaObserver.grantIntent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }) },
                    onCountdown = { ms -> scope.launch { timerRepo.startCountdown(ms) } },
                    onStopwatch = { scope.launch { timerRepo.startStopwatch() } },
                    onTimerClear = { scope.launch { timerRepo.clear() } },
                    onTorch = {
                        val ok = torch.toggle()
                        if (!ok) {
                            // grant kamera missing -> buka app settings (honest fallback AFR-11.4)
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:$packageName")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } else torchOn.value = !torchOn.value
                    },
                    onDnd = {
                        val ok = DndController.toggle(this@NotchService)
                        if (!ok) startActivity(DndController.grantIntent().apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        refreshOverlay()
                    },
                    onKeepScreen = {
                        keepScreenOn = !keepScreenOn
                        overlayView?.keepScreenOn = keepScreenOn && expanded.value
                    },
                    onInboxOpen = { item -> openInboxItem(item) },
                    onInboxCopy = { item -> copyInboxItem(item) },
                    onInboxRemove = { item -> scope.launch { inboxRepo.remove(item) } },
                    onInboxClear = { scope.launch { inboxRepo.clearAll() } },
                    onTodoAdd = { title -> scope.launch { todoRepo.add(title) } },
                    onTodoToggle = { td -> scope.launch { todoRepo.toggle(td) } },
                    onTodoClearDone = { scope.launch { todoRepo.clearDone() } },
                    onHydrateAdd = { ml -> scope.launch { hydrateRepo.add(ml) } }
                )
            }
        }
    }

    private fun windowY(): Int {
        val density = resources.displayMetrics.density
        val user = (settings.yOffsetDp * density).toInt()
        // AFR-6.1: hindari cutout kecuali Poni yang memang gantung di cutout.
        if (settings.style == 2) return user
        val cutout = try {
            overlayView?.rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
        } catch (_: Exception) { 0 }
        return user + cutout
    }

    // Helper ViewTree* dipanggil via refleksi: kelas statisnya tak terlihat kotlinc
    // di environment ini meski AAR-nya valid. Runtime aman — kelasnya pasti ada
    // di APK (dipakai Activity/Compose sendiri). Ekuivalen ViewTree*.set().
    private fun attachTreeOwners(view: View, owner: OverlayLifecycleOwner) {
        try {
            Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
                .getMethod("set", View::class.java, LifecycleOwner::class.java)
                .invoke(null, view, owner)
            Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
                .getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
                .invoke(null, view, owner)
            Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                .getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                .invoke(null, view, owner)
        } catch (e: Exception) {
            android.util.Log.e("NotchService", "attachTreeOwners gagal", e)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val owner = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }
        lifecycleOwner = owner
        val view = ComposeView(this).apply {
            attachTreeOwners(this, owner)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE && expanded.value) {
                    expanded.value = false
                    true
                } else false
            }
        }
        overlayView = view
        composeContent(view)
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
            y = windowY()
        }
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            overlayView = null
        }
    }

    private fun relayout() {
        val view = overlayView ?: return
        val p = params ?: return
        // PRD 7.5: revokasi canDrawOverlays itu silent — cek sebelum tiap update.
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay()
            refreshNotification()
            return
        }
        p.y = windowY()
        p.flags = if (expanded.value) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        try { wm.updateViewLayout(view, p) } catch (_: Exception) { }
        view.keepScreenOn = keepScreenOn && expanded.value
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
    }

    private fun refreshOverlay() {
        overlayView?.let { composeContent(it) }
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

    // ---- notifications ----

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Notch service", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(TimerFinishReceiver.CHANNEL, "Notch reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun fireTimerNotif() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            3001,
            NotificationCompat.Builder(this, TimerFinishReceiver.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Timer finished")
                .setContentText("Countdown selesai")
                .setAutoCancel(true)
                .build()
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

    private fun openInboxItem(item: InboxItem) {
        try {
            val intent = when (item.kind) {
                "link" -> Intent(Intent.ACTION_VIEW, Uri.parse(item.text ?: "")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                "image" -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("file://${item.imagePath}"), "image/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                else -> null
            }
            if (intent != null) startActivity(intent)
            else copyInboxItem(item)
        } catch (_: Exception) {
            copyInboxItem(item)
        }
    }

    private fun copyInboxItem(item: InboxItem) {
        val text = item.text ?: item.imagePath ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Notch inbox", text))
        sampler.glimpse("Copied")
        refreshOverlay()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(eyeReceiver) } catch (_: Exception) { }
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
        const val ACTION_FLASH_INBOX = "app.notch.FLASH_INBOX"

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
