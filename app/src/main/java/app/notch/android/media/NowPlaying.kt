package app.notch.android.media

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = -1,
    val canNext: Boolean = false,
    val canPrev: Boolean = false,
    val canSeek: Boolean = false
)
