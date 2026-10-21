# Release checklist (P5)

- [ ] `versionCode`/`versionName` naik di `app/build.gradle.kts`
- [ ] Min 29 / target 36, ikon + label final (bukan sym_def)
- [ ] `canDrawOverlays` revoke → notif graceful, tanpa crash (AFR-4.4)
- [ ] Reboot: autostart ≤3 dtk setelah unlock, timer re-arm
- [ ] Rotasi/fold: re-pin, panel state kept (debounce 200ms)
- [ ] Media Spotify/YouTube: title, play/next/seek; revoke → grant prompt
- [ ] Timer 5 mnt selesai dengan panel collapsed + screen off (batas grant)
- [ ] Inbox share ≤500 ms + flash; cap 100 menolak sopan
- [ ] Battery audit `docs/BATTERY_AUDIT.md` lolos
- [ ] TalkBack: pill/panel terbaca; target 48dp; reduced-motion OK
- [ ] Play listing `docs/PLAY_LISTING.md` final; package `app.notch.android`
