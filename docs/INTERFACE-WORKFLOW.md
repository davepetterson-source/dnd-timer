# KipQuiet – how to change the interface (for ChatGPT or any AI)

KipQuiet is an Android app in two halves:

1. **The interface** – one offline web page: `app/src/main/assets/kipquiet.html`.
   Everything you see (tiles, drum wheels, countdown reels, cards, Settings, colours, text, animation)
   is in that single file (React + zustand, compiled, with its CSS inside).
2. **The engine** – Java in `app/src/main/java/com/catstorm/dndtimer/`.
   It switches Silent / Do Not Disturb on and off, sets alarms, shows the notification, puts the
   phone's previous state back, and recovers after a reboot. **Do not change these for interface work.**

The page is shown inside a WebView (`MainActivity.java`). Interface changes = edit `kipquiet.html` only.

## Rules for interface changes

- Work on a new branch, never directly on `main`. Push the branch; Dave then brings it to Claude to build.
- Edit only `app/src/main/assets/kipquiet.html`. Keep it one self-contained file, no internet/CDN links.
- The page is designed on a 390 x 740 stage that is scaled to fit the phone. Keep that.
- Keep the phone fake status bar hidden (`.kq-status{display:none !important}` near the end of the CSS).
- Keep the connection to the engine. These pieces must stay working:
  - `window.KipNative.arm(json)` – called when a timer starts or is scheduled. JSON fields:
    `mode` ("silent" or "dnd"), `minutes`, `music` (bool), `startMs` (0 = now), `startMin`
    (minutes after midnight, -1 = now), `repeat` (array of weekday numbers).
    It returns `{"ok":true}` or `{"ok":false,"reason":"access"}`.
  - `window.KipNative.disarm()` – End / cancel.
  - `window.KipNative.state()` – returns the real state (idle / running / scheduled).
    The page's `syncNative` function reads it on every tick; the countdown shows what the engine says.
  - `window.__kqBack()` – called by the phone's back button; returns true if it closed something.
  Search the file for `KipNative` and `__kqBack` to find them.
- Tiles (Meditation, Kip, Sleep) start straight away; Setup opens Settings. Presets are kept in the
  WebView's localStorage under `kipquiet-v1`.
- Duration wheels: hours 0–10, minutes 00–55 in 5s. Start slots: Now + 48 half-hour slots.

## Previewing without a phone

Open `kipquiet.html` in a desktop browser. Without `window.KipNative` the page runs a pretend
countdown on screen. (A fake `KipNative` can be added in the browser console to test the real flow.)

## Building

Pushing a branch makes GitHub Actions build the APK (Actions tab -> latest run -> `sleeping-timer-apk`,
or the `build-<number>` release). Dave normally asks Claude to build and send it.
Real Do Not Disturb, alarms and reboot behaviour can only be tested on a phone.

`docs/original-chatgpt-handover.md` is ChatGPT's first handover note from when the interface was designed.
