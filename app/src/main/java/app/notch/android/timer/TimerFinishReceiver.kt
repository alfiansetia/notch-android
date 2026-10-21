package app.notch.android.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class TimerFinishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TimerAlarm.ACTION_FINISH) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Notch reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.notify(
            3001,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Timer finished")
                .setContentText("Countdown selesai")
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val CHANNEL = "notch_reminders"
    }
}
