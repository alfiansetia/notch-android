package app.notch.android.data

import android.content.Context
import app.notch.android.data.db.HydrateLog
import app.notch.android.data.db.NotchDatabase
import app.notch.android.data.db.TodoItem
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodoRepository(context: Context) {
    private val db = NotchDatabase.get(context)
    fun observe(): Flow<List<TodoItem>> = db.todos().observe()
    suspend fun add(title: String, dueAt: Long? = null): Long =
        db.todos().insert(TodoItem(title = title, dueAt = dueAt))
    suspend fun toggle(item: TodoItem) = db.todos().setDone(item.id, !item.done)
    suspend fun clearDone() = db.todos().clearDone()
    suspend fun remove(item: TodoItem) = db.todos().delete(item)
    suspend fun dueUnnotified() = db.todos().dueUnnotified()
    suspend fun markNotified(id: Long) = db.todos().markNotified(id)
}

class HydrateRepository(context: Context) {
    private val db = NotchDatabase.get(context)
    companion object {
        const val DAILY_GOAL_ML = 2000
        fun todayKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun observeToday(): Flow<Int> = db.hydrate().observeDayTotal(todayKey())
    suspend fun add(ml: Int) {
        db.hydrate().insert(HydrateLog(dayKey = todayKey(), ml = ml))
    }
    suspend fun todayTotal(): Int = db.hydrate().dayTotal(todayKey())
}
