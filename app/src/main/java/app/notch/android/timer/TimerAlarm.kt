package app.notch.android.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object TimerAlarm {
    const val ACTION_FINISH = "app.notch.TIMER_FINISH"

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    }

    fun exactGrantIntent(): Intent = if (Build.VERSION.SDK_INT >= 31) {
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    } else Intent()

    fun schedule(context: Context, deadlineMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        try {
            if (canExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMs, pi)
            } else {
                // Jujur: inexact, UI wajib nyebut approximasi (AFR-11.2)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMs, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMs, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 2001,
            Intent(context, TimerFinishReceiver::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
