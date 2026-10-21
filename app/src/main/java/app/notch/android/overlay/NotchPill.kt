package app.notch.android.overlay

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholder pill 180x32dp (PRD AFR-1.5). Ganti dengan 3 slot: RAM/clock/title.
@Composable
fun NotchPill(expanded: Boolean) {
    Surface(
        modifier = Modifier.size(180.dp, 32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface
    ) {
        Text(
            text = if (expanded) "expanded" else "notch",
            modifier = Modifier.padding(8.dp),
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}
