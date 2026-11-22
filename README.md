# KipQuiet

Android app that switches on Do Not Disturb for a chosen time and puts your previous
settings back when the time is up.

Modes: Silent (total silence) and Do Not Disturb (your own priority settings), each with an
optional "Keep music playing" switch that lets music/media through.
Duration: set with hours/minutes wheels, or start straight from the Meditation / Kip / Sleep tiles.
While running, a notification lets you jump back to the app or press End.

The APK is built automatically by GitHub Actions: open the Actions tab, pick the latest
run and download the `sleeping-timer-apk` artifact.

First run: press START, then switch on "KipQuiet" under Do Not Disturb access.

## Project layout

- `app/src/main/assets/kipquiet.html` – the whole interface (offline web page shown in a WebView).
- `app/src/main/java/com/catstorm/dndtimer/` – the engine: `Dnd.java` (modes, alarms, notification, restore),
  `DndReceiver.java` (alarms and reboot), `MainActivity.java` (WebView host and the bridge to the page).
- `docs/INTERFACE-WORKFLOW.md` – how to change the interface safely (written for ChatGPT or any AI).
