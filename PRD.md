# Notch for Android — Product Requirements Document (PRD)

> **Status:** Draft v1.0
> **Product type:** Native Android overlay utility (Kotlin)
> **Min SDK:** 29 (Android 10). **Target SDK:** current.
> **Primary stack:** Kotlin, Jetpack Compose (Material3 base + custom Notch theme), Coroutines/StateFlow, Room, DataStore, WorkManager
> **Product principle:** Invisible when idle, instant when needed, never in the way — same as desktop, adapted to touch and the Android sandbox
> **Parent spec:** `../notch/NOTCH_PRD.md` (desktop). This document carries over its concepts and design language, not its code. Anything the OS forbids is cut, not emulated.

---

## 1. Product Summary

Notch for Android is a system-wide overlay pill pinned to the top center of the
screen, in the spirit of the desktop notch and the Dynamic Island. A foreground
service owns a small `TYPE_APPLICATION_OVERLAY` window: collapsed it is a
glanceable pill (clock, stats, now-playing title); tapping it expands a panel
with media controls, timers, reminders, and a share inbox.

The defining desktop constraint — **selective click-through** — becomes simpler
on Android: the overlay window is *sized* to the visible pill/panel, so touches
outside it reach the app underneath by construction. There is no cursor, no
hit-test thread, no transparent-rectangle problem.

### Product vision

The "missing status bar" for Android: one glanceable surface at the top of the
screen that shows what matters right now, without opening a single app.

### Product principles (inherited)

1. **Zero-friction:** Present after boot (if enabled), no launch ceremony.
2. **Non-blocking:** The overlay never steals touches meant for other apps.
3. **Glanceable:** Collapsed state conveys status in under one second.
4. **Quiet:** No looping animations, no wakelocks while idle, samplers stop
   when the screen is off.
5. **Local-only:** No network calls. No telemetry, no accounts.
6. **Cheap:** Small APK, low idle RAM, negligible battery impact.
7. **Honest:** A control that cannot work is not shipped (the desktop DND
   shortcut is gone for exactly this reason; on Android DND *does* have a
   public API, so it returns as a real toggle — §5.11).

---

## 2. Goals and Non-Goals

### Goals

- Render a system-wide pill at the top center, above other apps, from a
  foreground service.
- Tap to expand, tap-outside / back / timeout to collapse, with spring motion.
- Guarantee touches outside the pill reach the underlying app.
- Show useful system status (RAM, battery, network rates, clock) without
  opening an app.
- Media awareness and control for the active session.
- Countdown + stopwatch that survive the panel collapsing.
- Todo, water, and eye-break reminders via notifications.
- A share inbox: anything the user shares to Notch lands in the panel.
- Survive rotation, cutouts, foldables, multi-window, doze, and reboot.
- Recovery paths when the overlay is hidden or its permission is revoked.

### Non-goals for v1

- No per-app volume mixer (Android exposes no API for it). Cut entirely.
- No CPU metric (per-core stats are restricted for third-party apps since
  Android 8; app-own CPU is not what the pill promises). Cut entirely.
- No audio spectrum visualizer in v1 (needs `RECORD_AUDIO` + an audio
  session; reconsider in v2 behind an explicit opt-in).
- No file drag-and-drop (no desktop-style OLE on Android). The Shelf concept
  returns as a share inbox instead (§5.12).
- No lock-screen replacement, no launcher, no notification-history clone.
- No clipboard watching.
- No cloud sync, accounts, or telemetry.
- No iOS (different sandbox, different surfaces — Live Activities, not overlays).

---

## 3. Target Users

### Primary users

- Android power users who monitor battery/data pressure and control media
  without switching apps.
- Users of the desktop Notch who want the same glanceable surface on their phone.
- Users with tall phones where the top of the screen is otherwise dead space.

### User needs

- "I want to see what's playing without switching apps."
- "I want to know RAM/battery/data pressure before my phone slows down."
- "I want a quick timer that doesn't take over my screen."
- "It must never eat my touches or my battery."

---

## 4. Product Scope and Priorities

| Phase | Theme | Status |
|---|---|---|
| **P1** | Foundation: overlay service, pill, tap expand/collapse, touch-through by window sizing, notification + QS-tile recovery | Planned |
| **P2** | System awareness: boot autostart, cutout/foldable/rotation handling, settings screen, auto-collapse | Planned |
| **P3** | Media: Now Playing via NotificationListener + MediaController; stats samplers; countdown + stopwatch; quick actions (flashlight, DND toggle) | Planned |
| **P4** | Inbox: share-target stash with open/copy/remove; Todo; Hydrate; eye-breaks (WorkManager) | Planned |
| **P4.1** | Styles: Dynamic, Bulat, Poni silhouettes + relaxed-eyes mode | Planned |
| **P5** | Hardening, battery audit, Play listing, release | Planned |

Scope rule: a feature is accepted only if it adds no wakelock while idle and
no background work while the screen is off (§7.4).

---

## 5. Functional Requirements

### 5.1 Overlay Window (P1)

- **AFR-1.1** A foreground service (`NotchService`) owns exactly one overlay
  window via `WindowManager`, type `TYPE_APPLICATION_OVERLAY`, added only
  after `Settings.canDrawOverlays()` is granted.
- **AFR-1.2** Flags: `FLAG_NOT_FOCUSABLE` (never steals input focus or opens
  the keyboard path), `FLAG_NOT_TOUCH_MODAL` + `FLAG_WATCH_OUTSIDE_TOUCH`
  (tap-outside collapses). No `FLAG_NOT_TOUCHABLE` — the pill itself must
  receive touches.
- **AFR-1.3** The window is *sized to the content*: collapsed
  `WRAP_CONTENT` around the pill, expanded around the panel. Touch-through
  outside the pill holds by construction — there is no hit-test code.
- **AFR-1.4** Gravity top-center with a user-adjustable y offset (0–24 dp,
  default 0). On resize (expand/collapse/rotation) the window updates via
  `updateViewLayout`, never remove/add.
- **AFR-1.5** The service runs in the foreground with a low-priority
  persistent notification ("Notch is running — tap to show/hide"), channel
  `notch_service`, `IMPORTANCE_MIN`. Killing the service removes the overlay.

Default geometry (dp): collapsed pill 180×32 (regular; compact 160×30, large
210×38); expanded panel max 380 wide × 300 tall, capped to 90% of screen width.

### 5.2 Expand / Collapse (P1)

- **AFR-2.1** Tapping the collapsed pill expands the panel. There is no hover
  on touchscreens; long-press does nothing (reserved).
- **AFR-2.2** Collapse triggers: tap outside the panel (`ACTION_OUTSIDE`),
  system Back (the overlay takes a transient focus grab only while expanded,
  released on collapse), or the auto-collapse timeout (§5.18).
- **AFR-2.3** Expansion uses spring easing (`Spring.DampingRatioMediumBouncy`
  equivalent); content fades in with a ~60 ms delay. Respects the system
  animator-duration scale and "Remove animations" accessibility setting.
- **AFR-2.4** The window relayouts synchronously with the animation so the
  touchable area always matches the visible shape.
- **AFR-2.5** Panel state (active view, timer text) survives collapse;
  nothing in the panel survives process death except persisted stores
  (todos, inbox, settings).

### 5.3 Touch-Through Guarantee (P1)

The core invariant, desktop FR-3 translated:

- **AFR-3.1** Outside the window bounds, touches always reach the underlying
  app — guaranteed by window sizing (§5.1), not by code that can regress.
- **AFR-3.2** Inside the pill/panel, touches are consumed normally (buttons,
  sliders, scroll).
- **AFR-3.3** Regression test: automated check that collapsed
  `LayoutParams.width/height` never exceed pill size + 1 px slack, and
  expanded never exceed panel size + slack.

### 5.4 Recovery: Notification, QS Tile, Launcher (P1)

The tray-icon concept becomes three Android-native paths:

- **AFR-4.1** The persistent service notification has actions: Show/Hide
  notch, Open settings.
- **AFR-4.2** A Quick Settings `TileService` ("Notch") toggles overlay
  visibility; its subtitle reflects state (On / Off / Paused). This is the
  fastest way back and the analogue of `Ctrl+Shift+N`.
- **AFR-4.3** The launcher icon opens Settings; a "Notch is hidden" banner
  there offers one-tap restore.
- **AFR-4.4** If overlay permission is revoked while running, the service
  stops the window, posts a high-visibility notification pointing at
  Settings, and waits — it never crashes, never loops re-adding the view.

### 5.5 Autostart (P2)

- **AFR-5.1** "Start at boot" toggle in settings, default off.
- **AFR-5.2** Implemented via `RECEIVE_BOOT_COMPLETED` + `LOCKED_BOOT_COMPLETED`
  *direct-boot-unaware* receiver (start only after user unlock). Respects
  battery-optimization: if the service is killed, the notification + tile are
  the way back; the app never fights the OS.
- **AFR-5.3** Autostart failure surfaces as a non-blocking warning in settings.

### 5.6 Cutout, Foldables, Rotation, Multi-Window (P2)

- **AFR-6.1** The pill avoids the display cutout: read `WindowInsets`
  display-cutout bounds and offset below them, unless the Poni style is
  selected — Poni is *designed* to hang from the cutout edge like laptop
  hardware (§5.13).
- **AFR-6.2** Fold/unfold and rotation re-measure and re-pin top-center on the
  next layout pass; no restart, no lost timer state.
- **AFR-6.3** In multi-window/split-screen the overlay spans the full display
  width (overlays are per-display, not per-activity) and stays pinned to the
  display top-center.
- **AFR-6.4** Edge-to-edge enforcement (API 35+): the overlay draws in its
  own window and is unaffected by activity insets; the *settings activity*
  itself is edge-to-edge with a transparent status bar.

### 5.7 Settings (P2)

- **AFR-7.1** Persisted with Proto/DataStore DataStore (single file, typed
  schema, defaults on read error). No database for settings.
- **AFR-7.2** Controls in v1: overlay permission state + rationale, start at
  boot, collapsed-pill size, per-slot stats (§5.10), notch style
  (Dynamic/Bulat/Poni), relaxed-eyes mode (off/auto/always), y offset, panel
  opacity, auto-collapse timeout, reduce-motion follow-system, Todo/Hydra­te/
  eye-break enables + cadences, inbox retention, notification channel toggles.
- **AFR-7.3** The settings surface is a normal in-app activity (Compose
  Navigation), not a separate window. Every screen fits without horizontal
  scroll on a 360 dp-wide phone.
- **AFR-7.4** Corrupt or unknown values fall back per-field to defaults,
  mirroring desktop FR-8.3.
- **AFR-7.5** Changes apply live through a `StateFlow<Settings>`; the service
  collects it and re-layouts without restart.

### 5.8 Notification Channels (P2)

- **AFR-8.1** Three channels, all user-mutable: `notch_service` (MIN,
  persistent), `notch_reminders` (DEFAULT — timers, water, eye-breaks,
  todos), `notch_status` (LOW — charging/data glimpses worth pushing).
- **AFR-8.2** Every notification carries a content intent (opens settings or
  the relevant view) and never duplicates what the pill already shows while
  the overlay is visible.

### 5.9 Now Playing (P3)

- **AFR-9.1** Collapsed pill shows title (and artist when space allows) of the
  active session; expanded Media view shows title/artist, play-pause,
  next/previous, seek bar, and — capability-gated — shuffle, repeat, rate.
- **AFR-9.2** Source: `NotificationListenerService` → active
  `MediaSession` tokens → `MediaController`. Requires the user to grant
  Notification access; without it the Media view shows a one-tap grant prompt,
  never an empty player (same honesty rule as desktop FR-9.6).
- **AFR-9.3** Transport callbacks drive updates; a 1 s fallback poll covers
  missed callbacks. Never poll faster than 1 s.
- **AFR-9.4** Album art via `MediaMetadata` bitmap, downscaled to ≤ 128 px and
  cached in memory keyed by session + track. Never written to disk.
- **AFR-9.5** If no session is active, the collapsed slot shows its fallback
  (clock) — no empty media placeholder.

### 5.10 System Stats (P3)

Per-slot stats, same concept as desktop after the per-slot refactor — with the
Android-feasible set:

- **AFR-10.1** Each collapsed slot picks its own stat: **RAM** (used % via
  `ActivityManager.MemoryInfo`), **battery** (`BatteryManager` sticky
  broadcast + level/charging), **DOWN / UP** (per-second rates via
  `TrafficStats` deltas on the same 2 s sampler), **clock**, **title**,
  **spectrum-placeholder (v2)**, **eyes**, or **empty**. No CPU — cut per §2.
  Defaults: RAM / clock / title.
- **AFR-10.2** Sampling interval 2 s on a service coroutine; paused while the
  screen is off (`ACTION_SCREEN_OFF` suspends, `ACTION_SCREEN_ON` resumes).
- **AFR-10.3** No shelling out, no `/proc` parsing, no packet capture.
- **AFR-10.4** Charging edges glimpse "Charging" / "Unplugged" for 4 s in the
  collapsed pill; full-at-100 while on charger glimpses "Fully charged".
  Tabular numerals; fixed-width slots; no layout shift.
- **AFR-10.5** A device without a battery readout (rare, e.g. some TV boxes)
  keeps the slot empty rather than showing 0%.

### 5.11 Timers and Quick Actions (P3)

- **AFR-11.1** Countdown presets (5/10/25 min + custom) and a stopwatch mode;
  starting one replaces the other. State survives collapse; survives process
  death via a persisted deadline (unlike desktop — phones kill services).
- **AFR-11.2** On finish: pill pulses once, a `notch_reminders` notification
  fires (uses `SCHEDULE_EXACT_ALARM` where granted; otherwise
  `setAndAllowWhileIdle` with best-effort timing, stated honestly in the UI),
  no modal.
- **AFR-11.3** Quick actions in the expanded header, all real toggles/actions:
  **flashlight** (`CameraManager` torch, exposed with on/off state),
  **DND toggle** (`NotificationManager.setInterruptionFilter` behind the
  Policy-Access grant — the toggle desktop could never have), and
  **keep-screen-on** while the panel is open (`FLAG_KEEP_SCREEN_ON`,
  session-only, the adapted keep-awake). Each is one tap, no confirmation.
- **AFR-11.4** Where a grant is missing (policy access, exact alarms), the
  button opens the exact system page with an inline explanation — the honest
  fallback, used only when the toggle truly cannot work yet.

### 5.12 Share Inbox (P4 — the Shelf, adapted)

Files cannot be dragged onto a phone overlay, so the Shelf returns as a share
target:

- **AFR-12.1** The app registers `ACTION_SEND` / `ACTION_SEND_MULTIPLE` for
  text, links, and images. Sharing to Notch stashes the item and flashes the
  pill ("Saved to inbox").
- **AFR-12.2** The Inbox view lists items (newest first, max 100); tap opens
  via intent, long-press offers Copy / Share onward / Remove. Images are
  stored once in app-private storage; text inline in Room.
- **AFR-12.3** A "Clear all" behind confirmation removes references only.
- **AFR-12.4** Nothing is ever watched or auto-collected: only what the user
  shares lands in the inbox.

### 5.13 Styles (P4.1)

Dynamic, Bulat, and Poni — same three silhouettes, same contract (the window
is sized to the silhouette; transparent corners are untouchable because the
window simply isn't there):

- **AFR-13.1** Settings > Appearance radio group, default Dynamic, live
  preview, persisted.
- **AFR-13.2** Dynamic: rounded capsule collapsed; rounded panel expanded.
- **AFR-13.3** Bulat: circular collapsed notch showing the selected clock
  (analog, like desktop); expands to the normal panel.
- **AFR-13.4** Poni: tab hanging from the top edge/cutout with a camera dot;
  square top corners, small bottom radius.
- **AFR-13.5** Relaxed-eyes mode (off/auto/always): auto shows the eyes when
  nothing plays, no timer runs, and the user hasn't touched the pill for
  60 s; always forces them; a low battery (≤ 20%) swaps in a ring + percent.
  Same doze (20 s) and greeting behavior as desktop.

### 5.14 Todo, Hydrate, Eye-Breaks (P4)

- **AFR-14.1** Todo: add/toggle/clear-done, due times fire one reminder
  notification each; persisted in Room; overdue highlighted in-panel.
- **AFR-14.2** Hydrate: daily 2000 ml goal, +250/+500 chips, streak; reminders
  on the settings cadence (15–240 min, default 60) via WorkManager +
  notification; first nudge one full interval after enabling.
- **AFR-14.3** Eye-breaks (20-20-20): pill glimpse + notification on the
  settings cadence (5–120 min, default 20). No dedicated view.
- **AFR-14.4** All reminders are battery-safe: `WorkManager` with
  constraints (no exact timing promises beyond timers), coalesced where the
  OS allows.

### 5.15 Panel Views (P4)

Media, Focus (timers), Inbox, Todo, Hydrate — horizontally swipable in the
panel (pager), with a dots/view-switcher row mirroring desktop. Mixer does
not exist (§2). Disabled views (inbox off, hydrate off) drop out of the pager.

### 5.16 Autohide → Auto-Collapse + Hide (P2/P4.6 analogue)

There is no cursor, so autohide becomes two settings:

- **AFR-16.1** Auto-collapse timeout (default 8 s, 0 = off): an expanded panel
  with no interaction collapses by itself.
- **AFR-16.2** "Hide pill" switch (default off): hides the whole overlay; the
  QS tile subtitle flips to "Paused — tap to resume", the notification offers
  Show, and the launcher banner offers restore. The header eye toggle (§5.11)
  flips this same flag.

---

## 6. UX and Navigation

### Surfaces

1. **Collapsed pill** — three slots (defaults RAM / clock / title).
2. **Expanded panel** — header (date, toggles, settings gear) + pager views.
3. **Notification** — persistent service row + reminder channel.
4. **QS tile** — fastest show/hide.
5. **Settings activity** — Appearance / Behavior / Inbox / Reminders / About.
6. **Share target** — system sheet entry "Save to Notch inbox".

### UX principles (inherited, touch-translated)

- **Never move under the finger.** Expansion grows downward from the pill;
  the touch point stays inside the panel.
- **No focus theft.** `FLAG_NOT_FOCUSABLE` always; Back handling only while
  expanded and released on collapse.
- **One motion at a time.** No competing animations.
- **Respect reduced motion.** System animator scale / Remove-animations is
  honored; spring becomes instant.
- **Nothing disappears silently.** Revoked permission explains itself; a full
  inbox refuses with a message.
- **Back always collapses.** No trap states.
- **48 dp targets.** All header icons and chips meet touch-target minimums
  (desktop used 24 px mouse targets — this is the key metric change).

### Interaction map

| Input | Result |
|---|---|
| Tap pill | Expand |
| Tap outside / Back / timeout | Collapse |
| Swipe left/right in panel | Previous/next view |
| Tap eye toggle | Hide or restore overlay |
| QS tile tap | Toggle overlay visibility |
| Share → Notch | Stash + pill flash |
| Timer finish | Pulse + notification, no modal |

---

## 7. Technical Architecture

### 7.1 Stack

| Layer | Choice | Rationale |
|---|---|---|
| Language | Kotlin | First-class Android APIs, coroutines |
| UI (overlay + settings) | Jetpack Compose | Declarative, shared theme, `ComposeView` in overlay window |
| State | `StateFlow` + ViewModel (settings activity); service-owned flows (overlay) | Single source of truth per surface; no polling across surfaces |
| Persistence | DataStore (settings), Room (todos, inbox, hydrate log) | Typed, async, crash-safe |
| Background | Foreground service + WorkManager | Overlay ownership + battery-safe reminders |
| Media | NotificationListener → MediaController | Only supported route to other apps' sessions |
| Min/target | 29 / current | Covers ~95%+ devices; overlay + exact-alarm APIs stable |

### 7.2 Process and data flow

```
┌──────────────────────────────┐
│ NotchService (foreground)    │
│  WindowManager overlay view  │
│  ├─ ComposeView (pill/panel) │◄── StateFlow<OverlayUiState>
│  ├─ StatsSampler (2 s,       │
│  │   screen-on only)         │
│  ├─ MediaObserver (callbacks │
│  │   + 1 s fallback)         │
│  └─ TimerOwner (deadline,    │
│      persisted)              │
└──────────────────────────────┘
        ▲ collects            │ posts
┌───────┴──────────────────────┴───┐
│ SettingsActivity (Compose Nav)   │── DataStore ──► same StateFlow
│ ShareReceiverActivity            │── Room (inbox)
│ QSTileService                    │── show/hide commands
│ BootReceiver                     │── start if enabled
│ ReminderWorker (WorkManager)     │── notifications
└──────────────────────────────────┘
```

**Authority rule:** the service owns the overlay window and all samplers. UI
surfaces render state and send intents; nothing else touches `WindowManager`.

### 7.3 Permissions (declared × requested)

| Permission | Type | Used for | If denied |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Special access | The overlay itself | Onboarding blocks with rationale; service idles on notification+tile only |
| `FOREGROUND_SERVICE` + `SPECIAL_USE` | Install/normal | Keeping the pill alive | Core feature unavailable; app is settings-only |
| `POST_NOTIFICATIONS` | Runtime (33+) | Timer/reminder/service notifications | Reminders fall back to pill glimpses only |
| `RECEIVE_BOOT_COMPLETED` | Normal | Autostart | Boot toggle hidden |
| `SCHEDULE_EXACT_ALARM` | Special access | On-time timer finish | Inexact alarms; UI states the approximation |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Special access | Now Playing sessions | Media view shows grant prompt |
| `ACCESS_NOTIFICATION_POLICY` | Special access | DND toggle | Button degrades to settings-page shortcut with explanation |
| Camera (torch) | Runtime | Flashlight action | Action hidden |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special access | **Not requested.** The app never asks to be whitelisted; it accepts Doze kills and restores via boot/notification/tile. | — |

No location, contacts, microphone (v1), or storage permissions. No ad SDKs.

### 7.4 Performance budgets

| Metric | Budget | Notes |
|---|---|---|
| APK (release, universal) | ≤ 15 MB | No webview, no media SDKs |
| Idle RAM (collapsed, screen on) | ≤ 80 MB | Service + ComposeView; profile on a low-end device |
| Idle RAM (screen off) | ~0 growth | Samplers suspended; service foreground-idle |
| Battery | No wakelock while idle; < 1%/h delta vs baseline in testing | Partial wake only around timer finish + reminder windows |
| Sampler cadence | 2 s stats; 1 s media fallback; 0 while screen off | Matches desktop budgets |
| Cold start to pill visible (post-boot) | ≤ 3 s after unlock | Receiver → service → first layout |
| Inbox share → visible | ≤ 500 ms | Room insert + pill flash |

Any PR exceeding a budget justifies it in the description.

### 7.5 Known platform gotchas

- **OEM battery killers** (notably some vendors) murder foreground services
  despite the rules. Detect the manufacturer, show a one-time vendor-specific
  guidance card, never programmatic workarounds.
- **Notification-listener disabled by updates:** re-check on service start;
  the Media view degrades to its grant prompt.
- **`canDrawOverlays` revocation** is silent — re-check in `onStartCommand`
  and before every `addView`.
- **Exact-alarm denial (API 31+):** `canScheduleExactAlarms()` gates the
  promise; the UI must never claim precision it doesn't have.
- **Doze vs countdown:** a 25-min countdown across Doze needs either the
  exact alarm or a visible notification; prefer the notification + inexact
  path unless the user granted exact alarms.
- **Cutout + rotation races:** re-pin after `onConfigurationChanged`, not
  during; debounce 200 ms.

---

## 8. Testing and Acceptance

| Area | Check |
|---|---|
| Overlay | Fresh install → onboarding explains overlay grant → pill appears top-center |
| Touch-through | With pill collapsed, tap app icons directly beneath/around it; all land |
| Expand/collapse | Tap expands; tap-outside, Back, and timeout collapse; window bounds track the animation |
| Kill/revoke | Revoke overlay permission → graceful notification, no crash; re-grant → pill returns |
| Reboot | With autostart on, pill returns ≤ 3 s after unlock; timers with future deadlines re-arm |
| Rotation/fold | Rotate and fold/unfold; pill re-pins, panel state kept |
| Media | Play Spotify/YouTube → title appears; pause/play/next/seek work; revoke listener → grant prompt |
| Stats | RAM/battery/DOWN/UP render; airplane mode → `0 B/s`, honest; charging edge glimpses |
| Timers | 5-min countdown finishes with pulse + notification even with panel collapsed and screen off (within alarm-grant limits); stopwatch counts up |
| Quick actions | Torch toggles; DND toggles with policy grant; without grant, explained shortcut |
| Inbox | Share text/link/image → pill flash + item visible; open/copy/remove work; 100-item cap refuses politely |
| Reminders | Todo due, water, eye-break all notify on cadence; disabling stops them |
| Styles | Dynamic/Bulat/Poni switch live, survive restart; relaxed-eyes auto/always/off behave |
| Battery | Overnight idle drain within budget; no wakelock held (verify via `dumpsys`) |
| Accessibility | TalkBack names match visible slots; 48 dp targets; animations off honored |

---

## 9. Open Questions

1. **Exact-alarm UX:** if the user denies exact alarms, is a ±5 min timer
   finish acceptable, or should countdowns be disabled with an explanation?
2. **Poni vs cutout:** on phones with a centered punch-hole camera, should
   Poni align to it (hardware illusion) or stay centered? Needs device testing.
3. **Inbox scope:** keep it text/link/image-only in v1, or accept any
   `ACTION_SEND` stream (APKs, zips) as opaque entries?
4. **Monetization:** none planned (matches desktop: local-only, no accounts).
   Play listing will be a paid app or free — decide before P5.
5. **Package name / signing:** reserve `app.notch.*` namespace early.
