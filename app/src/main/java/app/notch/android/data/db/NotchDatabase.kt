package app.notch.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [InboxItem::class, TodoItem::class, HydrateLog::class], version = 1)
abstract class NotchDatabase : RoomDatabase() {
    abstract fun inbox(): InboxDao
    abstract fun todos(): TodoDao
    abstract fun hydrate(): HydrateDao

    companion object {
        @Volatile private var inst: NotchDatabase? = null
        fun get(context: Context): NotchDatabase =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotchDatabase::class.java, "notch.db"
                ).fallbackToDestructiveMigration().build().also { inst = it }
            }
    }
}
