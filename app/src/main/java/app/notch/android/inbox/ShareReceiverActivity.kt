package app.notch.android.inbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import app.notch.android.data.InboxRepository
import app.notch.android.overlay.NotchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val repo = InboxRepository(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            val result: Result<Long>? = when (intent?.action) {
                Intent.ACTION_SEND -> handleSend(intent, repo)
                Intent.ACTION_SEND_MULTIPLE -> {
                    // v1: ambil item pertama yang didukung (keputusan OQ-3: text/link/image)
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    when {
                        text != null -> repo.addText(text)
                        uris != null -> {
                            var r: Result<Long>? = null
                            for (u in uris) {
                                r = if (contentResolver.getType(u)?.startsWith("image/") == true) {
                                    repo.addImage(u)
                                } else null
                                if (r?.isSuccess == true) break
                            }
                            r
                        }
                        else -> null
                    }
                }
                else -> null
            }
            runOnUiThread {
                if (result?.isSuccess == true) {
                    // pill flash "Saved to inbox" (AFR-12.1)
                    startService(
                        Intent(applicationContext, NotchService::class.java)
                            .setAction(NotchService.ACTION_FLASH_INBOX)
                    )
                    Toast.makeText(this@ShareReceiverActivity, "Saved to inbox", Toast.LENGTH_SHORT).show()
                } else if (result != null) {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        result.exceptionOrNull()?.message ?: "Tidak didukung",
                        Toast.LENGTH_LONG
                    ).show()
                }
                finish()
            }
        }
    }

    private suspend fun handleSend(intent: Intent, repo: InboxRepository): Result<Long>? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val type = intent.type ?: ""
        return when {
            text != null -> repo.addText(text)
            stream != null && type.startsWith("image/") -> repo.addImage(stream)
            stream != null -> Result.failure(IllegalStateException("v1 hanya text/link/image"))
            else -> null
        }
    }
}
