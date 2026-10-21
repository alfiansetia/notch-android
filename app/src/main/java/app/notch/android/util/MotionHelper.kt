package app.notch.android.util

import android.content.Context
import android.provider.Settings

// PRD 5.2/AFR-2.3: hormati animator-duration scale + Remove animations.
object MotionHelper {
    fun isReducedMotion(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
            scale == 0f
        } catch (_: Exception) { false }
    }
}
