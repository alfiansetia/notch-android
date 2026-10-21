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
    val eyeCadenceMins: Int = 20
)
