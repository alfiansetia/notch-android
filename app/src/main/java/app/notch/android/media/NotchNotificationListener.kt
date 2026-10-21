package app.notch.android.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotchNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { onChanged?.invoke() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { onChanged?.invoke() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { onChanged?.invoke() }

    companion object {
        var onChanged: (() -> Unit)? = null
    }
}
