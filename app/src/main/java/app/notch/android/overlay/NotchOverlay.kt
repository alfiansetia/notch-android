package app.notch.android.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.notch.android.data.HydrateRepository
import app.notch.android.data.db.InboxItem
import app.notch.android.data.db.TodoItem
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
    inbox: List<InboxItem> = emptyList(),
    todos: List<TodoItem> = emptyList(),
    hydrateMl: Int = 0,
    showInbox: Boolean = true,
    showTodo: Boolean = true,
    showHydrate: Boolean = true,
    style: Int = 0,
    showEyes: Boolean = false,
    eyesDozing: Boolean = false,
    lowBattery: Boolean = false,
    reduceMotion: Boolean = false,
    onPillTap: () -> Unit,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onMediaGrant: () -> Unit = {},
    onCountdown: (Long) -> Unit = {},
    onStopwatch: () -> Unit = {},
    onTimerClear: () -> Unit = {},
    onTorch: () -> Unit = {},
    onDnd: () -> Unit = {},
    onKeepScreen: () -> Unit = {},
    onInboxOpen: (InboxItem) -> Unit = {},
    onInboxCopy: (InboxItem) -> Unit = {},
    onInboxRemove: (InboxItem) -> Unit = {},
    onInboxClear: () -> Unit = {},
    onTodoAdd: (String) -> Unit = {},
    onTodoToggle: (TodoItem) -> Unit = {},
    onTodoClearDone: () -> Unit = {},
    onHydrateAdd: (Int) -> Unit = {}
) {
    val (w, h) = pillSizeDp(pillSize)
    val targetW = if (expanded) 320.dp else if (style == 1) 44.dp else w
    val targetH = if (expanded) 300.dp else if (style == 1) 44.dp else h
    val springSpec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy)
    val animW by animateDpAsState(
        targetW,
        animationSpec = if (reduceMotion) snap() else springSpec,
        label = "nw"
    )
    val animH by animateDpAsState(
        targetH,
        animationSpec = if (reduceMotion) snap() else springSpec,
        label = "nh"
    )
    val shape = when {
        expanded -> MaterialTheme.shapes.extraLarge
        style == 1 -> androidx.compose.foundation.shape.CircleShape
        style == 2 -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp
        )
        else -> MaterialTheme.shapes.extraLarge
    }

    Surface(
        modifier = Modifier.size(animW, animH).semantics {
            contentDescription = if (expanded) "Notch panel" else "Notch pill: $w"
        },
        shape = shape,
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp
    ) {
        if (!expanded) {
            val collapsedText = stats.glimpse
                ?: nowPlaying?.title
                ?: stats.title
                ?: "${stats.ramPct}%  ${stats.clock}"
            val inv = MaterialTheme.colorScheme.inverseOnSurface
            when {
                lowBattery -> Row(
                    modifier = Modifier.size(animW, animH)
                        .clickable(onClick = onPillTap)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    BatteryRing(stats.batteryPct.coerceAtLeast(0), inv)
                    Text("${stats.batteryPct}%", color = inv,
                        style = MaterialTheme.typography.labelSmall)
                }
                showEyes -> androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(animW, animH).clickable(onClick = onPillTap),
                    contentAlignment = Alignment.Center
                ) { RelaxedEyes(eyesDozing, inv) }
                style == 1 -> androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(animW, animH).clickable(onClick = onPillTap),
                    contentAlignment = Alignment.Center
                ) { AnalogClock(color = inv) }
                style == 2 -> Row(
                    modifier = Modifier.size(animW, animH)
                        .clickable(onClick = onPillTap)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    CameraDot()
                    Text(
                        collapsedText,
                        color = inv,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                else -> Row(
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
            val tabs = buildList {
                add("Media"); add("Focus")
                if (showInbox) add("Inbox")
                if (showTodo) add("Todo")
                if (showHydrate) add("Hydrate")
                add("Quick")
            }
            var tab by remember { mutableIntStateOf(0) }
            val safeTab = tab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
            Column(Modifier.size(animW, animH).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stats.clock, color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onCollapse) { Text("Notch") }
                }
                Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    Arrangement.spacedBy(2.dp)) {
                    tabs.forEachIndexed { i, t ->
                        Text(
                            t,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(
                                alpha = if (safeTab == i) 1f else 0.5f
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable { tab = i }.padding(4.dp)
                        )
                    }
                }
                // dots pager indicator (AFR-15)
                Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                    tabs.forEachIndexed { i, _ ->
                        Text(
                            if (i == safeTab) "● " else "○ ",
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { tab = i }
                        )
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (tabs.getOrNull(safeTab)) {
                        "Media" -> MediaTab(nowPlaying, mediaGranted, onPlayPause, onNext, onPrev, onSeek, onMediaGrant)
                        "Focus" -> FocusTab(timer, onCountdown, onStopwatch, onTimerClear)
                        "Inbox" -> InboxTab(inbox, onInboxOpen, onInboxCopy, onInboxRemove, onInboxClear)
                        "Todo" -> TodoTab(todos, onTodoAdd, onTodoToggle, onTodoClearDone)
                        "Hydrate" -> HydrateTab(hydrateMl, onHydrateAdd)
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
private fun InboxTab(
    items: List<InboxItem>,
    onOpen: (InboxItem) -> Unit, onCopy: (InboxItem) -> Unit,
    onRemove: (InboxItem) -> Unit, onClear: () -> Unit
) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("Inbox (${items.size}/100)", color = c, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onClear) { Text("Clear") }
    }
    if (items.isEmpty()) {
        Text("Kosong. Share text/link/image ke Notch.", color = c, style = MaterialTheme.typography.bodySmall)
        return
    }
    items.take(20).forEach { item ->
        val label = when (item.kind) {
            "image" -> "[img] ${item.imagePath?.substringAfterLast("/") ?: ""}"
            else -> (item.text ?: "").take(60)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp)
                .combinedClickable(onClick = { onOpen(item) }, onLongClick = { onCopy(item) }),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text(label, color = c, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("✕", color = c, modifier = Modifier.clickable { onRemove(item) }.padding(4.dp))
        }
    }
}

@Composable
private fun TodoTab(
    items: List<TodoItem>,
    onAdd: (String) -> Unit, onToggle: (TodoItem) -> Unit, onClearDone: () -> Unit
) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    var draft by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            label = { Text("New todo") }, singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { if (draft.isNotBlank()) { onAdd(draft.trim()); draft = "" } }) { Text("+") }
    }
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("Todos (${items.count { !it.done }}/${items.size})", color = c,
            style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onClearDone) { Text("Clear done") }
    }
    val now = System.currentTimeMillis()
    items.take(20).forEach { t ->
        val overdue = !t.done && t.dueAt != null && t.dueAt < now
        Row(
            Modifier.fillMaxWidth().clickable { onToggle(t) }.padding(vertical = 2.dp),
            Arrangement.SpaceBetween
        ) {
            Text(
                (if (t.done) "✓ " else "○ ") + t.title,
                color = if (overdue) MaterialTheme.colorScheme.errorContainer else c,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HydrateTab(ml: Int, onAdd: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    val goal = HydrateRepository.DAILY_GOAL_ML
    Text("$ml / $goal ml", color = c, style = MaterialTheme.typography.titleSmall)
    if (goal > 0) Slider(value = ml.toFloat(), onValueChange = {}, valueRange = 0f..goal.toFloat(), enabled = false)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { onAdd(250) }) { Text("+250") }
        OutlinedButton(onClick = { onAdd(500) }) { Text("+500") }
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
