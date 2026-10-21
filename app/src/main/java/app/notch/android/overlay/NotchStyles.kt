package app.notch.android.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

fun styleName(style: Int): String = when (style) {
    1 -> "Bulat"
    2 -> "Poni"
    else -> "Dynamic"
}

fun eyesModeName(mode: Int): String = when (mode) {
    1 -> "Auto"
    2 -> "Always"
    else -> "Off"
}

// Bulat: jam analog kecil di lingkaran (AFR-13.3)
@Composable
fun AnalogClock(modifier: Modifier = Modifier, color: Color = Color.White) {
    val cal = Calendar.getInstance()
    val h = (cal.get(Calendar.HOUR) % 12) + cal.get(Calendar.MINUTE) / 60f
    val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    Canvas(modifier = modifier.size(22.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        drawCircle(color, r, c)
        val bg = Color.Black
        fun hand(angleDeg: Float, len: Float, width: Float) {
            val a = Math.toRadians((angleDeg - 90).toDouble())
            drawLine(bg, c, c + Offset(cos(a).toFloat() * len, sin(a).toFloat() * len),
                strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(h / 12f * 360f, r * 0.45f, 3f)
        hand(m / 60f * 360f, r * 0.7f, 2f)
        drawCircle(bg, 2f, c)
    }
}

// Relaxed-eyes (AFR-13.5): dua mata, doze = setengah tertutup via alpha.
@Composable
fun RelaxedEyes(dozing: Boolean, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(48.dp, 24.dp)) {
        val eyeR = size.height * 0.32f
        val ly = Offset(size.width * 0.3f, size.height / 2)
        val ry = Offset(size.width * 0.7f, size.height / 2)
        drawCircle(color.copy(alpha = if (dozing) 0.35f else 1f), eyeR, ly)
        drawCircle(color.copy(alpha = if (dozing) 0.35f else 1f), eyeR, ry)
        drawCircle(Color.Black, eyeR * 0.4f, ly)
        drawCircle(Color.Black, eyeR * 0.4f, ry)
    }
}

// Low battery ≤20%: ring + persen (AFR-13.5)
@Composable
fun BatteryRing(pct: Int, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawArc(color.copy(alpha = 0.3f), 0f, 360f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        drawArc(color, -90f, pct / 100f * 360f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(3f, cap = StrokeCap.Round))
    }
}

// Poni camera dot
@Composable
fun CameraDot() {
    Box(modifier = Modifier.size(8.dp)) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(Color.Black, radius = size.minDimension / 2)
            drawCircle(Color(0xFF1A2A4A), radius = size.minDimension / 4)
        }
    }
}
