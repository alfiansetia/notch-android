# Battery audit (PRD 7.4)

Budget: no wakelock idle, <1%/h delta vs baseline, sampler 2s stats / 1s media fallback / 0 saat screen-off.

## Cara ukur

1. Build release, install di HP low-end.
2. Biarkan collapsed semalam, screen-off.
3. Pagi: `adb shell dumpsys batterystats --reset` sebelum tidur, lalu
   `adb shell dumpsys batterystats app.notch.android | grep -i wake`.
4. Pastikan: `Wake lock * : 0`, tidak ada `PARTIAL_WAKE_LOCK` milik Notch
   kecuali sekitar timer finish / reminder window.
5. Idle RAM: `adb shell dumpsys meminfo app.notch.android` — target ≤80 MB collapsed.
6. APK: `ls -lh app-release.apk` — target ≤15 MB universal.

## Kalau jebol

PR wajib menjelaskan justifikasi di deskripsi (PRD 7.4).
