package app.notch.android.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ---- Inbox (AFR-12): text inline, image path ke app-private storage ----
@Entity(tableName = "inbox")
data class InboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = "text", // text | link | image
    val text: String? = null,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_ITEMS = 100
    }
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox ORDER BY createdAt DESC")
    fun observe(): Flow<List<InboxItem>>

    @Query("SELECT COUNT(*) FROM inbox")
    suspend fun count(): Int

    @Query("SELECT * FROM inbox ORDER BY createdAt DESC LIMIT 100")
    suspend fun latest(): List<InboxItem>

    @Insert
    suspend fun insert(item: InboxItem): Long

    @Delete
    suspend fun delete(item: InboxItem)

    @Query("DELETE FROM inbox")
    suspend fun clearAll()

    @Query("DELETE FROM inbox WHERE createdAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}

// ---- Todo (AFR-14.1) ----
@Entity(tableName = "todos")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val dueAt: Long? = null,
    val notified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY done ASC, createdAt DESC")
    fun observe(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todos WHERE done = 0 AND dueAt IS NOT NULL AND dueAt <= :now AND notified = 0")
    suspend fun dueUnnotified(now: Long = System.currentTimeMillis()): List<TodoItem>

    @Insert
    suspend fun insert(item: TodoItem): Long

    @Query("UPDATE todos SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE todos SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long)

    @Query("DELETE FROM todos WHERE done = 1")
    suspend fun clearDone()

    @Delete
    suspend fun delete(item: TodoItem)
}

// ---- Hydrate log (AFR-14.2): ml per hari ----
@Entity(tableName = "hydrate")
data class HydrateLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String, // yyyy-MM-dd
    val ml: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface HydrateDao {
    @Query("SELECT COALESCE(SUM(ml),0) FROM hydrate WHERE dayKey = :day")
    fun observeDayTotal(day: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(ml),0) FROM hydrate WHERE dayKey = :day")
    suspend fun dayTotal(day: String): Int

    @Insert
    suspend fun insert(log: HydrateLog)

    @Query("SELECT COUNT(DISTINCT dayKey) FROM hydrate WHERE ml > 0")
    suspend fun activeDays(): Int
}
