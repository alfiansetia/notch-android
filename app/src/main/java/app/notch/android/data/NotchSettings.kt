package app.notch.android.data

data class NotchSettings(
    val hidden: Boolean = false,
    val startAtBoot: Boolean = false,
    // 0 = compact 160x30, 1 = regular 180x32, 2 = large 210x38
    val pillSize: Int = 1,
    val yOffsetDp: Int = 0,
    // 0 = off, else seconds
    val autoCollapseSecs: Int = 8,
    // P4
    val inboxEnabled: Boolean = true,
    val inboxRetentionDays: Int = 30,
    val todoEnabled: Boolean = true,
    val hydrateEnabled: Boolean = false,
    val hydrateCadenceMins: Int = 60,
    val eyeEnabled: Boolean = false,
    val eyeCadenceMins: Int = 20,
    // P4.1 styles: 0 Dynamic, 1 Bulat, 2 Poni
    val style: Int = 0,
    // relaxed-eyes: 0 off, 1 auto, 2 always
    val eyesMode: Int = 0,
    val oemCardDismissed: Boolean = false
)
