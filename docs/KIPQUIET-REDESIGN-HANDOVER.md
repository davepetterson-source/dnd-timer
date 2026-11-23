# KipQuiet Redesign — Android Build Handover
Date: 23 November 2026

## 1. Scope / safe branch
- Repository: https://github.com/davepetterson-source/dnd-timer
- Work ONLY on branch `kipquiet-redesign`, created from `main`. Do not merge to or edit `main` without owner approval.
- This document is a handover for another AI/developer to integrate the **separately supplied interactive HTML prototype C35** into the Android app.
- The C35 standalone preview is **NOT YET COMMITTED AS THE NATIVE APP INTERFACE**. Ask the owner for the file `KipQuiet-interactive-preview-C35.html` from ChatGPT and place it under project files before integration. Do not assume the HTML is already in this repository.

## 2. Existing project (verified from repository)
- `app/src/main/assets/kipquiet.html`: existing offline interface, rendered in a WebView.
- `app/src/main/java/com/catstorm/dndtimer/MainActivity.java`: WebView host + `window.KipNative` bridge.
- `Dnd.java`: native Do Not Disturb / Silent, alarms, notifications, prior-policy restoration.
- `DndReceiver.java`: scheduled begin/end and reboot handling.
- `docs/INTERFACE-WORKFLOW.md`: original integration/build rules; read this before touching the interface.
- `README.md`: GitHub Actions instructions; artifacts are described as `sleeping-timer-apk`.
- Preserve the Java engine and native bridge; do not replace the app with a browser-only countdown.

## 3. The approved C35 prototype
- Dark navy moonlit lake background, glowing circular dial with outer segmented/tick ring.
- The dial uses hours/minutes for setting time. While running, it switches to remaining time (minutes:seconds for short timers; hours added for long timers) and animates the outer ring's progress. No green status banner overlapping the dial.
- Four 2×2 quick presets: **Calm** (15 min, DND, music allowed), **Kip** (1 hour, DND, music off, favourite callers desired), **Sleep** (8 hours, Silent, music off), **Focus** (25 min, DND, music off, favourite callers desired).
- Calm is the new label for the former Meditation preset; retaining the meditation illustration is deliberate.
- Quick-preset cards are soft 3D raised coloured rectangles; main icon/name/duration occupy upper-left, with phone / speaker / music indicators grouped at bottom-right.
- Icon meanings: starred handset = favourite callers only; slashed handset = calls silenced; slashed speaker = muted notifications; double-note = media allowed. Indicators bright when applicable and grey when inactive. Their real semantics must match Android's actual policy.
- Main drawings are images (PNG artwork), not freshly invented SVG graphics. The Focus book+pencil is from the prior approved preset editor. The Meeting choice in Setup is the approved PNG with two seated people facing each other (option C, no table).
- Main button reads **Start Sleep Timer**, switching to Stop Sleep Timer while active; future start choices need scheduled behavior. Separate Starts, Finishes, Repeat, Do Not Disturb, Silent controls.
- Setup should open the preset editor (NOT Repeat). Preset editor: name, icon selection, hours, minutes, quiet mode, allow media, favourite-callers choice. **Save preset closes Setup and returns to home**. Repeat days picker is separate.
- User is precise about icon geometry and dislikes unnecessary restyling or SVG substitutions. Preserve alignment and established visual design.

## 4. Essential difference: browser prototype vs Android app
The standalone C35 HTML simulates a countdown using JavaScript and stores presets with localStorage key `kipquiet-preview-presets-v28`. Its script contains **no `KipNative` calls**. It is an interactive design reference, **not** a drop-in working Android DND integration.

To integrate, adapt the existing app's native bridge in the new interface:
- `window.KipNative.arm(JSON.stringify({...}))` when starting/scheduling, with:
  - `mode`: `"silent"` or `"dnd"`
  - `minutes`: number
  - `music`: boolean
  - `startMs`: 0 to begin now; future epoch millis to schedule
  - `startMin`: -1 for now; minute-of-day for scheduled times
  - `repeat`: array of 7 booleans, Monday–Sunday
- Parse bridge result `{"ok":true}` or handle `{"ok":false,"reason":"access"}` without falsely starting the UI timer.
- `window.KipNative.disarm()` to cancel/end.
- `window.KipNative.state()` for authoritative idle / running / scheduled state, real `endsAt`, and progress. Resynchronise after resume/relaunch; don't trust a browser-only timer as source of truth.
- `window.__kqBack()` must close overlays before Android Back exits.
- The engine's native code is authoritative for alarms, DND settings, notifications, restoration and reboot recovery.
- MainActivity currently derives its repeat mask and converts startMin to 15-minute grid indices; test the preview's selected start times against this conversion. See `docs/INTERFACE-WORKFLOW.md`.

## 5. Important Android functionality checks
- **Favourite callers**: the C35 editor checkbox is a preview preference. The existing `MainActivity.Bridge.arm` does not accept a `faves` field, and Dnd.java DND mode uses Android's **existing priority policy**. Do not claim the app can force starred-only callers until this is implemented and tested through Android policy APIs, with a clear permission/compatibility story. Emergency/system exceptions may differ.
- Silent: native `INTERRUPTION_FILTER_NONE` for full silence (unless allow-music logic changes the priority filter). DND: native priority filter, respecting the configured priority contacts/alarms; media allow works through policy changes.
- Ensure DND access permission request, exact-alarm fallback, scheduled start/repeat, end action, reboot recovery, and restoring the user's previous notification state continue working.
- Check hours/minutes wheel constraints, whether zero-duration is allowed, and scheduled starts through midnight.
- Ensure call/media status icons correspond to actual policy, not simply checkbox assumptions.

## 6. Build and acceptance checklist
1. Make changes only on `kipquiet-redesign`. Read `README.md` and `docs/INTERFACE-WORKFLOW.md`.
2. Bring the user-supplied C35 HTML into the repository as source material. Keep original `app/src/main/assets/kipquiet.html` intact until a native-bridge-integrated replacement is ready.
3. Replace the current WebView asset **only after** adding `KipNative` integration and testing setup, preset editing, quick-start, countdown reels, outer dial ring, Starts, Repeat, Schedule, Stop/Cancel and Android Back.
4. Build with the existing GitHub Actions workflow or Gradle as configured; provide APK artifact location. Do not promise a working APK without build validation.
5. On a real Android device test real policy changes, favourite callers, media playback, notification action, previous-policy restoration, scheduled events and reboot.
6. Record any unavoidable visual differences and seek owner approval. Do not touch `main` or merge without approval.

## 7. What is ready / not ready
Ready: separate `kipquiet-redesign` branch, approved browser HTML C35 (provided separately), this handover, working native engine on original branch.
Not ready: integrated WebView UI, bridge wiring, favourite-caller control, Android APK verification.

## 8. Explicit instruction for receiving AI
**Use the C35 HTML as a visual/interaction specification, not as an Android-ready replacement. Preserve the existing native engine, and port the interface carefully. Ask for the C35 file if it was not provided. Do the work on `kipquiet-redesign` only.**
