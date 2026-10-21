package app.notch.android.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaObserver(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    private var controllers: List<MediaController> = emptyList()
    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = refresh()
    }

    init {
        NotchNotificationListener.onChanged = { refresh() }
        scope.launch {
            while (isActive) {
                delay(1000) // fallback poll ≤1s (AFR-9.3)
                refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        if (!isListenerEnabled()) {
            _nowPlaying.value = null
            return
        }
        try {
            val msm = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(appContext, NotchNotificationListener::class.java)
            val active = msm.getActiveSessions(component)
            controllers.forEach { try { it.unregisterCallback(cb) } catch (_: Exception) {} }
            controllers = active
            active.forEach { try { it.registerCallback(cb) } catch (_: Exception) {} }
            val c = active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: active.firstOrNull()
            if (c == null) {
                _nowPlaying.value = null
                return
            }
            val md = c.metadata
            val pb = c.playbackState
            val actions = pb?.actions ?: 0
            _nowPlaying.value = NowPlaying(
                title = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
                artist = md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
                playing = pb?.state == PlaybackState.STATE_PLAYING,
                positionMs = pb?.position ?: 0,
                durationMs = md?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: -1,
                canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
                canPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
                canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
            )
        } catch (_: SecurityException) {
            _nowPlaying.value = null
        } catch (_: Exception) {
        }
    }

    fun isListenerEnabled(): Boolean = isListenerEnabled(appContext)

    fun toggle() {
        val c = current() ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = try { current()?.transportControls?.skipToNext() } catch (_: Exception) {}
    fun prev() = try { current()?.transportControls?.skipToPrevious() } catch (_: Exception) {}
    fun seekTo(ms: Long) = try { current()?.transportControls?.seekTo(ms) } catch (_: Exception) {}

    private fun current(): MediaController? = try {
        val msm = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(appContext, NotchNotificationListener::class.java)
        val active = msm.getActiveSessions(component)
        active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: active.firstOrNull()
    } catch (_: Exception) { null }

    companion object {
        fun isListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(context.packageName)
        }

        fun grantIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
