# KipQuiet – how to change the interface (for ChatGPT or any AI)

KipQuiet is an Android app in two halves:

1. **The interface** – one offline web page: `app/src/main/assets/kipquiet.html`.
   Everything you see (preset cards, dial, countdown reels, cards, Settings, colours, text, animation)
   is in that single hand-written file (HTML + CSS + plain JavaScript, images embedded).
2. **The engine** – Java in `app/src/main/java/com/catstorm/dndtimer/`.
   It switches Silent / Do Not Disturb on and off, sets alarms, shows the notification, puts the
   phone's previous state back, and recovers after a reboot. **Do not change these for interface work.**

The page is shown inside a WebView (`MainActivity.java`). Interface changes = edit `kipquiet.html` only.

## Rules for interface changes

- Work on a new branch, never directly on `main`. Push the branch; Dave then brings it to Claude to build.
- Edit only `app/src/main/assets/kipquiet.html`. Keep it one self-contained file, no internet/CDN links.
- The page is designed on a 390 x 790 stage that is scaled to fit the phone (`fit()` at the end of the script). Keep that.
- Preview-only wording ("NOT THE ANDROID APP", "preview settings only") must stay hidden.
- Keep the connection to the engine. Search the script for `KipNative` and `bridge`:
  - `bridge.arm({mode, minutes, music, startMs, startMin, repeat})` calls `window.KipNative.arm(json)`.
    `mode` is "silent" or "dnd"; `startMs` 0 = start now; `startMin` is minutes after midnight (-1 = now);
    `repeat` is seven Mon..Sun booleans; `faves` is optional (see below). It returns `{"ok":true}` or `{"ok":false,"reason":"access"}`.
  - `bridge.disarm()` calls `window.KipNative.disarm()` (Stop / Cancel).
  - `bridge.state()` calls `window.KipNative.state()`. This is the real state (idle / running / scheduled).
    `render()` runs on a 250 ms timer and on resume, and the dial shows exactly what the engine says.
  - `window.__kqBack()` is called by the phone's back button; it must return true if it closed something.
- Without `window.KipNative` (desktop browser) the page uses a pretend engine so it can be previewed.
- Preset cards (Calm, Kip, Sleep, Focus) start straight away. The big green button schedules for the
  Starts time (starts now if that time is within a minute). Presets are kept in localStorage key `kipquiet-preview-presets-v28`.
- `faves` (boolean, sent with preset starts) controls calls in Do Not Disturb: true = starred contacts may ring, false = no calls. The engine also allows alarms always and media only when `music` is true. If `faves` is missing (the big Start button) the engine uses the phone's own Do Not Disturb settings.

## Previewing without a phone

Open `kipquiet.html` in a desktop browser. Without `window.KipNative` the page runs a pretend
countdown on screen. (A fake `KipNative` can be added in the browser console to test the real flow.)

## Building

Pushing a branch makes GitHub Actions build the APK (Actions tab -> latest run -> `sleeping-timer-apk`,
or the `build-<number>` release). Dave normally asks Claude to build and send it.
Real Do Not Disturb, alarms and reboot behaviour can only be tested on a phone.

`docs/original-chatgpt-handover.md` is ChatGPT's first handover note from when the interface was designed.
