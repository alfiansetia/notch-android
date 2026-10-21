package app.notch.android.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.notch.android.data.HydrateRepository
import app.notch.android.data.TodoRepository
import java.util.concurrent.TimeUnit

const val REMINDER_CHANNEL = "notch_reminders"

fun ensureReminderChannel(ctx: Context) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(
        NotificationChannel(REMINDER_CHANNEL, "Notch reminders", NotificationManager.IMPORTANCE_DEFAULT)
    )
}

private fun notify(ctx: Context, id: Int, title: String, text: String) {
    ensureReminderChannel(ctx)
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(
        id, NotificationCompat.Builder(ctx, REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
    )
}

class HydrateWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val total = HydrateRepository(applicationContext).todayTotal()
        if (total < HydrateRepository.DAILY_GOAL_ML) {
            notify(applicationContext, 4001, "Hydrate", "Belum ${HydrateRepository.DAILY_GOAL_ML - total} ml hari ini. Minum dulu.")
        }
        return Result.success()
    }
}

class EyeBreakWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        // Pill glimpse dikirim via broadcast; notif selalu (AFR-14.3)
        applicationContext.sendBroadcast(
            android.content.Intent("app.notch.EYE_BREAK").setPackage(applicationContext.packageName)
        )
        notify(applicationContext, 4002, "Eye break", "20-20-20: lihat 20 kaki selama 20 detik.")
        return Result.success()
    }
}

class TodoDueWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val repo = TodoRepository(applicationContext)
        repo.dueUnnotified().forEach { todo ->
            notify(applicationContext, 5000 + todo.id.toInt(), "Todo due", todo.title)
            repo.markNotified(todo.id)
        }
        return Result.success()
    }
}

object ReminderScheduler {
    fun apply(ctx: Context, hydrateOn: Boolean, hydrateMins: Int, eyeOn: Boolean, eyeMins: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (hydrateOn) {
            wm.enqueueUniquePeriodicWork(
                "hydrate", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<HydrateWorker>(
                    hydrateMins.coerceIn(15, 240).toLong(), TimeUnit.MINUTES
                ).build()
            )
        } else wm.cancelUniqueWork("hydrate")
        if (eyeOn) {
            wm.enqueueUniquePeriodicWork(
                "eye", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<EyeBreakWorker>(
                    eyeMins.coerceIn(5, 120).toLong(), TimeUnit.MINUTES
                ).build()
            )
        } else wm.cancelUniqueWork("eye")
        // Todo due check tiap 15 mnt (baterai-aman, coalesced)
        wm.enqueueUniquePeriodicWork(
            "todo_due", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TodoDueWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
