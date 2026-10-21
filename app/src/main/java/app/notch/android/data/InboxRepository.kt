package app.notch.android.data

import android.content.Context
import app.notch.android.data.db.InboxItem
import app.notch.android.data.db.NotchDatabase
import kotlinx.coroutines.flow.Flow
import java.io.File

class InboxRepository(context: Context) {
    private val db = NotchDatabase.get(context)
    private val appCtx = context.applicationContext

    fun observe(): Flow<List<InboxItem>> = db.inbox().observe()

    suspend fun addText(text: String): Result<Long> {
        val count = db.inbox().count()
        if (count >= InboxItem.MAX_ITEMS) {
            return Result.failure(IllegalStateException("Inbox penuh (100). Hapus dulu."))
        }
        val kind = if (text.startsWith("http://") || text.startsWith("https://")) "link" else "text"
        return Result.success(db.inbox().insert(InboxItem(kind = kind, text = text)))
    }

    suspend fun addImage(srcUri: android.net.Uri): Result<Long> {
        val count = db.inbox().count()
        if (count >= InboxItem.MAX_ITEMS) {
            return Result.failure(IllegalStateException("Inbox penuh (100). Hapus dulu."))
        }
        return try {
            val dir = File(appCtx.filesDir, "inbox").apply { mkdirs() }
            val dst = File(dir, "img_${System.currentTimeMillis()}.jpg")
            appCtx.contentResolver.openInputStream(srcUri)?.use { ins ->
                dst.outputStream().use { outs -> ins.copyTo(outs) }
            } ?: return Result.failure(IllegalStateException("Gagal baca gambar"))
            Result.success(db.inbox().insert(InboxItem(kind = "image", imagePath = dst.absolutePath)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun remove(item: InboxItem) {
        item.imagePath?.let { try { File(it).delete() } catch (_: Exception) {} }
        db.inbox().delete(item)
    }

    suspend fun clearAll() = db.inbox().clearAll()

    suspend fun prune(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000
        db.inbox().pruneOlderThan(cutoff)
    }
}
