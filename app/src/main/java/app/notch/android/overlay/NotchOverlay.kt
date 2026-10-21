package app.notch.android.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    onPillTap: () -> Unit,
    onCollapse: () -> Unit
) {
    val (w, h) = pillSizeDp(pillSize)
    val targetW = if (expanded) 320.dp else w
    val targetH = if (expanded) 300.dp else h
    val animW by animateDpAsState(
        targetW,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "notchW"
    )
    val animH by animateDpAsState(
        targetH,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "notchH"
    )

    Surface(
        modifier = Modifier.size(animW, animH),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier
                    .size(animW, animH)
                    .clickable(onClick = onPillTap)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${stats.ramPct}%",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    stats.clock,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    stats.title ?: "notch",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .size(animW, animH)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stats.clock,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Notch",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onCollapse)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (stats.batteryPct >= 0) "Battery ${stats.batteryPct}%  •  RAM ${stats.ramPct}%"
                    else "RAM ${stats.ramPct}%",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Media • Focus • Inbox • Todo • Hydrate (P3/P4)",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "tap Notch to collapse",
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}
