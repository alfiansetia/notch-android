package app.notch.android.actions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FlashlightController(private val context: Context) {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on

    fun toggle(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            _on.value = !_on.value
            cm.setTorchMode(id, _on.value)
            true
        } catch (_: Exception) { false }
    }
}

object DndController {
    fun hasAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun grantIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun toggle(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        val dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
        return true
    }

    fun isDnd(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }
}
