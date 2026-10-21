package app.notch.android.util

import android.os.Build

// PRD 7.5: OEM battery killer — deteksi vendor, tampilkan panduan sekali,
// jangan pernah workaround programatik.
object OemGuidance {
    fun vendor(): String = Build.MANUFACTURER.lowercase()

    fun needsGuidance(): Boolean = vendor() in setOf(
        "xiaomi", "redmi", "huawei", "honor", "oppo", "realme",
        "vivo", "oneplus", "samsung", "asus", "nokia"
    )

    fun message(): String = when (vendor()) {
        "xiaomi", "redmi" -> "Xiaomi: Settings → Battery → App battery saver → Notch → No restrictions. " +
            "Aktifkan Autostart untuk Notch."
        "huawei", "honor" -> "Huawei: Settings → Battery → App launch → Notch → Manage manually, " +
            "aktifkan semua (Auto-launch, Secondary launch, Run in background)."
        "oppo", "realme", "oneplus" -> "Oppo/Realme/OnePlus: Settings → Battery → App battery management → " +
            "Notch → Allow background activity."
        "vivo" -> "Vivo: Settings → Battery → Background power consumption → Notch → Allow."
        "samsung" -> "Samsung: Settings → Battery → Background usage limits → Never sleeping apps → + Notch."
        else -> "Vendor ini agresif mematikan foreground service. " +
            "Kecualikan Notch dari battery optimization manual; " +
            "app tidak akan melawan OS (PRD 7.3)."
    }
}
