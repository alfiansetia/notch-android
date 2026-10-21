package app.notch.android.inbox

import android.app.Activity
import android.os.Bundle

class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: ACTION_SEND/SEND_MULTIPLE -> Room insert (max 100) + pill flash "Saved to inbox"
        finish()
    }
}
