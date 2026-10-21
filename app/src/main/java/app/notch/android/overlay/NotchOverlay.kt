package app.notch.android.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.notch.android.media.NowPlaying
import app.notch.android.timer.TimerState
import app.notch.android.timer.formatHms

fun pillSizeDp(size: Int): Pair<Dp, Dp> = when (size) {
    0 -> 160.dp to 30.dp
    2 -> 210.dp to 38.dp
    else -> 180.dp to 32.dp
}

@Composable
fun NotchOverlay(
    expanded: Boolean,
    pillSize: Int,
    stats: StatsState,
    nowPlaying: NowPlaying?,
    mediaGranted: Boolean,
    timer: TimerState,
    torchOn: Boolean,
    dndOn: Boolean,
    onPillTap: () -> Unit,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    onMediaGrant: () -> Unit,
    onCountdown: (Long) -> Unit,
    onStopwatch: () -> Unit,
    onTimerClear: () -> Unit,
    onTorch: () -> Unit,
    onDnd: () -> Unit,
    onKeepScreen: () -> Unit
) {
    val (w, h) = pillSizeDp(pillSize)
    val targetW = if (expanded) 320.dp else w
    val targetH = if (expanded) 300.dp else h
    val animW by animateDpAsState(targetW, spring(Spring.DampingRatioMediumBouncy), label = "nw")
    val animH by animateDpAsState(targetH, spring(Spring.DampingRatioMediumBouncy), label = "nh")

    Surface(
        modifier = Modifier.size(animW, animH),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp
    ) {
        if (!expanded) {
            val collapsedText = stats.glimpse
                ?: nowPlaying?.title
                ?: stats.title
                ?: "${stats.ramPct}%  ${stats.clock}"
            Row(
                modifier = Modifier.size(animW, animH)
                    .clickable(onClick = onPillTap)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    collapsedText,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            var tab by remember { mutableIntStateOf(0) }
            Column(Modifier.size(animW, animH).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stats.clock, color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onCollapse) { Text("Notch") }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    listOf("Media", "Focus", "Quick").forEachIndexed { i, t ->
                        Text(
                            t,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(
                                alpha = if (tab == i) 1f else 0.5f
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { tab = i }.padding(4.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> MediaTab(nowPlaying, mediaGranted, onPlayPause, onNext, onPrev, onSeek, onMediaGrant)
                        1 -> FocusTab(timer, onCountdown, onStopwatch, onTimerClear)
                        else -> QuickTab(torchOn, dndOn, onTorch, onDnd, onKeepScreen, stats)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTab(
    np: NowPlaying?, granted: Boolean,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    onSeek: (Float) -> Unit, onGrant: () -> Unit
) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    if (!granted) {
        Text("Media butuh Notification access", color = c, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Button(onClick = onGrant) { Text("Grant") }
        return
    }
    if (np == null || np.title == null) {
        Text("Nothing playing", color = c, style = MaterialTheme.typography.bodySmall)
        return
    }
    Text(np.title, color = c, style = MaterialTheme.typography.titleSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    np.artist?.let { Text(it, color = c.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(6.dp))
    if (np.durationMs > 0) {
        Slider(
            value = np.positionMs.toFloat(),
            onValueChange = onSeek,
            valueRange = 0f..np.durationMs.toFloat()
        )
    }
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        OutlinedButton(onClick = onPrev, enabled = np.canPrev) { Text("|<") }
        Button(onClick = onPlayPause) { Text(if (np.playing) "Pause" else "Play") }
        OutlinedButton(onClick = onNext, enabled = np.canNext) { Text(">|") }
    }
}

@Composable
private fun FocusTab(
    timer: TimerState,
    onCountdown: (Long) -> Unit, onStopwatch: () -> Unit, onClear: () -> Unit
) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    val text = when (timer.mode) {
        "countdown" -> "Countdown ${formatHms(timer.remainingSecs)}"
        "stopwatch" -> "Stopwatch ${formatHms(timer.stopwatchSecs)}"
        else -> "No timer"
    }
    Text(text, color = c, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5L to "5m", 10L to "10m", 25L to "25m").forEach { (m, label) ->
            OutlinedButton(onClick = { onCountdown(m * 60_000L) }) { Text(label) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onStopwatch) { Text("Stopwatch") }
        OutlinedButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun QuickTab(
    torchOn: Boolean, dndOn: Boolean,
    onTorch: () -> Unit, onDnd: () -> Unit, onKeep: () -> Unit,
    stats: StatsState
) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    Text(
        "RAM ${stats.ramPct}% • Batt ${if (stats.batteryPct >= 0) "${stats.batteryPct}%" else "-"} " +
            "• v${formatRate(stats.downBps)}/${formatRate(stats.upBps)}",
        color = c, style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onTorch) { Text(if (torchOn) "Torch off" else "Torch") }
        OutlinedButton(onClick = onDnd) { Text(if (dndOn) "Unmute" else "DND") }
        OutlinedButton(onClick = onKeep) { Text("Keep-on") }
    }
}
