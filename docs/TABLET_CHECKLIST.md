# Tablet checklist (Pixel Tablet AVD, API 34+)

- [ ] Portrait: pill tengah-atas, skala 1.25x (225dp regular), panel ≤380dp
- [ ] Landscape: pill re-pin tengah-atas display, panel ≤90% lebar
- [ ] Split-screen 2 app: overlay tetap di tengah display (bukan per-activity)
- [ ] Rotasi cepat: tidak restart, timer/panel state kept (debounce 200ms)
- [ ] Fold (jika pakai Fold AVD): fold/unfold re-measure
- [ ] Touch-through: tap app di bawah/sekitar pill tembus
- [ ] TalkBack + target 48dp tetap lolos di density tablet
- [ ] RAM idle ≤80 MB di tablet juga (`dumpsys meminfo`)
