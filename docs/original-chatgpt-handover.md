# KipQuiet — Android APK handover
**22 November 2026 | Approved web preview V27**

## 1. Assignment for Claude

Integrate the approved KipQuiet V27 interface into the existing native Android application, then build a real, installable APK and provide the owner with the direct GitHub Actions download location. Dave Petterson owns the project, judges the UI by eye and has no Android development experience. Keep communication short and actionable. Do the engineering work rather than repeatedly reporting the plan. Do not redesign or change unrelated functionality.

### Inputs and authority
- Android source: https://github.com/davepetterson-source/dnd-timer (default branch `main`). This is the real phone application and the authority for native Android integration.
- `KipQuiet-preview-v27.html` (supplied alongside this document): the approved visual and interactive reference. Open it in a browser, inspect its embedded CSS, modules, SVG paths and image data. It is a standalone web preview using a compatibility layer, not an APK or complete native implementation.
- This document records the owner's decisions. Preserve these if an implementation detail in the preview or old app differs. Earlier generated concept images are not the approved interface; use the working V27 HTML instead.

## 2. Protect the existing project and build the APK

1. Inspect the repository, existing Gradle configuration, activities, timer/alarm receiver, persistence, permissions and GitHub Actions workflow. Save the baseline commit. Create a separate branch (e.g. `kipquiet-v27-android`) from `main`; do not overwrite or merge `main` without approval.
2. Port the interface to the existing Android architecture. Reuse exact assets and geometry from V27. Choose native rendering or a carefully integrated UI layer as appropriate, but keep the real device operations in the native backend. A standalone WebView wrapping the simulated preview is not an acceptable finished app.
3. Preserve package identity, existing signing strategy, permission handling, notifications, exact alarms, reboot recovery and restoration of previous sound/DND settings. Inspect actual implementations, rather than assuming every feature in the manifest works. Add any missing logic required by the approved UI. Avoid unrelated permissions and broad refactors.
4. Run the existing Gradle build and relevant tests; use GitHub Actions on the new branch to produce the APK. The repository README says the workflow publishes `sleeping-timer-apk`. Check the actual workflow and trigger configuration; do not assume the branch will build automatically. Fix build errors within the branch. Verify the artifact is from the new commit, not an old build.
5. Give Dave the successful Actions run URL, artifact name and simple instructions to download, unzip and install the APK. Explain any signing conflict with an existing installation before asking him to uninstall anything (uninstalling may lose settings). A debug APK is fine for phone testing if installable; do not claim it is a production-signed release.

Current status: ChatGPT connected to GitHub and verified the repository and README/manifest. The manifest includes notification-policy access, exact-alarm permissions, boot-completed and notification permissions, MainActivity and DndReceiver. No confirmed Android source changes, branch build or new APK were completed in this conversation. Do not assume V27 is already on GitHub.

## 3. Approved visual specification

The V27 appearance is the target, not a new design brief. Match the original moon/lake background, navy/pastel palette, exact guitar-pick outlines, spacing and placement. The preview uses a nominal 390 × 740 stage; adapt to real phone dimensions and system insets without distortion or clipping. Use the native status bar rather than duplicating the preview's simulated one. No lone gear in the status bar.

### Four guitar-pick tiles
- Top left Meditation; top right Setup; lower left Kip; lower right Sleep. Keep the exact `TILES` path geometry from the embedded `@/lib/tiles` module. Do not redraw outlines.
- Keep icons, names, duration and mute/music symbols centred within the respective shapes. Keep the Kip and Sleep Zs inside their icon areas. Setup reads **Setup / Presets**, not Repeat or “& more”.
- Preserve the approved shallow raised rim (4 px at preview scale), soft bevel, gentle shadows and tactile pressed effect. The tiles should look raised, not dished, but not overly deep. Keep the established colours.

### Central duration and countdown dial
- Duration: two close, independent odometer-style vertical swipe rollers, hours on the left and minutes on the right, minutes 00–55 in five-minute increments. V27 offers hours 0–10, normalises zero duration to five minutes, and has no separate duration pills. Reconcile bounds with native storage and scheduling. See known checks below.
- Numbers follow finger motion, coast gently and settle/snap smoothly with visible adjacent digits; no abrupt jumps. Use V5/V27 subdued dark casing, off-white digits, muted labels and gentle rounded shading. Do not return to the rejected bright lavender V4 styling.
- When armed, use display-only animated hours/minutes/seconds rollers with the same graphical language. Centrally align them with equal top and bottom clearance inside the circular surround. Scheduled: “Countdown” above; “until it starts” below, larger and legible. For >=24 hours, show whole days above the rollers and remainder HH:MM:SS (e.g. 119:16:13 becomes 4 days + 23:16:13). Hide days below 24 hours. Running: follow V27's running display; Zs rise from above the middle of the digits, not from the right. Do not allow countdown rollers to alter duration.

### Start / finish and weekday cards
- Start At: one compact matching odometer-style wheel, containing Now and all 48 half-hour times 00:00–23:30 (24-hour clock). Smooth rolling. No separate hours/minutes wheels here. Preserve selection even after redraw.
- Finishes is read-only. Match the Start card's horizontal bars, widths, shading and typography. Put Today/Tomorrow or the appropriate weekday above the time, not underneath. When scheduled, Starts also shows the actual day above the actual start time and the same display treatment. Keep both cards aligned.
- Use the final embedded small blue-silver bevelled analogue clock before Start At/Starts and the matching waving flag with full visible flagpole before Finishes. Apply consistently across every state; do not replace with generic emoji, crescent, sunrise or outline-only icons. Inspect the actual SVG/image assets in V27.
- Seven day selectors on home only, Monday–Sunday; selected violet subdued, not neon-bright. Days must affect the actual next scheduled date (not just the display).

### Main buttons
- Start/Schedule Sleep Timer: approved soft green raised button; Cancel Sleep Timer: subdued rose-red, never aggressive bright red. Preserve labels/state changes from V27.
- Do Not Disturb: distinctly blue, not grey-blue; Silent: muted pink-purple. Both have graphical mode icons, matching raised edges, drop shadows and pressed effect. Selected mode must be clearly more prominent than unselected (brighter edge, stronger glow and lit indicator), while keeping both base colours. Use the intermediate V12 depth, not the shallower V10 or overly deep V11. Carry the same design into settings and preset editor.

## 4. Functional behaviour

- Green main button uses the Start At selection, duration, selected days, mode and music setting. Now starts immediately regardless of weekday selection.
- Meditation/Kip/Sleep tiles start their respective timer immediately using the saved preset's duration, mode and music; ignore the Start At and weekday choices for this action. Setup opens Settings only, never starts a timer.
- For a future scheduled time, find the next occurrence on the selected weekday(s) in local time, including later in the week. If none selected, find next occurrence of the chosen time. Match preview's near-now handling deliberately; V27 tolerates a time up to 20 seconds past and treats a start <60 seconds away as imminent. Use actual native timestamps and validate day boundaries, DST and clock changes. Calculate finish as start plus duration. Show the correct day labels and multi-day countdown.
- While scheduled/running, prevent editing duration, start, mode and days where V27 locks them. Cancel scheduled timers cleanly without applying DND. Start real native DND/Silent at the intended time; restore previous settings at expiry or cancellation. Preserve native notification, music/media behaviour and permission flows, including when the screen is off or process is recreated. Do not confuse Silent with DND priority settings.
- V27 models idle, scheduled and running states, saving them to browser localStorage. Android must instead use reliable native state and persistence; UI must reflect true alarm/session state rather than relying on a browser tick. The preview's weekday array selects the next occurrence; it does not by itself implement automatic recurring sessions after completion. Preserve existing native recurrence semantics if present; do not silently add indefinite recurrence. Clarify only if a genuine product conflict remains.

## 5. Settings and Quick Presets

- No Repeat Days card. Day controls are on home only. Settings section order requested: Default Mode, Keep Music Playing, Help, About (retain Quick Presets section in its V27 placement).
- Quick preset rows are not action buttons and do not jump home or start timers. Three-dot menu opens the editor. Use the two odometer duration wheels in the editor too, not pills. Match graphical DND/Silent buttons and selection glow. Wrap long preset detail lines so Music ON/OFF is fully visible.
- Edit name, icon, duration, mode and music independently. Save changes and persist across launches. Changing the name must not implicitly change icon. Changes to built-in presets must immediately update the corresponding home tile, keeping its shape/position. Retain existing custom preset management, without turning the home into an additional grid of tiles.
- The V27 selector provides five icons: Meditation (`med`, sitting figure), Kip (`bed`, chair and Zs), Sleep (`sleep`, bed and Zs), Meeting (`meeting`, people at table), Focus (`focus`, book/pencil). Reuse the original assets and new icons embedded in V27; the internal `custom` fallback is also accepted by its persistence model. Preserve existing preset data during migration.
- V27 built-in initial values (only for fresh/default settings): Meditation 15 min, Silent, music on; Kip 60 min, DND, music off; Sleep 480 min, Silent, music off. Do not reset existing user preferences to these defaults when updating.

## 6. Implementation notes and acceptance checks

The single HTML contains embedded CSS plus compiled React-style modules and a lightweight compatibility runtime, including `@/components/home`, `@/components/settings`, `@/components/drum`, `@/components/icons`, `@/components/kip-app`, `@/lib/model`, `@/lib/store`, `@/lib/tiles`. These are inspectable references for extracting assets, sizing, animation, paths and UI behaviour; they are not drop-in native source files. The moon background and graphics are embedded as data URIs and/or SVG. Extract and package locally so the phone app works offline.

Known things to verify rather than blindly copy: the native README documents an older 1-minute–6-hour/until-off duration model, whereas V27 has 0–10 hour wheels and 8-hour Sleep default. V27 `joinDuration` permits 10h55m but its preset sanitizer clamps to 600 minutes; reconcile this inconsistency, ensuring the supported duration range is consistent across selection, storage, native alarms and restoration. Do not silently remove old supported behaviour or clamp an approved preset. V27's timer is simulated and the final preview was not independently visually or end-to-end tested by ChatGPT. The owner approved its appearance through iterative browser checks. Native integration and phone testing remain necessary.

Before handing over the APK, check:
- Home and Settings fit Android screens with no clipped graphics, overflowing text or duplicated status bar. Compare screenshots against V27 at equivalent states. Check full flagpole, icon centring, equal card bars, 3D edges and selected/unselected mode contrast.
- Both duration wheels and compact Start At wheel animate and settle properly on touch; check Now, 00:00, 09:30, 23:30, 5-minute durations and 8-hour Sleep.
- Schedule a selected day several days ahead; verify correct whole days, remainder hours, date labels and finish time (including midnight rollover). Test no selected days, today after chosen time, multiple selected days and Now. Test schedule cancellation and actual alarm start.
- Start all three presets with a future Start At selected: each must start immediately. Open Setup: no timer. Edit each preset and select Meeting/Focus; verify saved icon and name after restart and correct home tile. Settings rows do not start timers or leave Settings.
- Test DND and Silent with music on/off, denied/granted permissions, notification End, cancel and natural expiry, app backgrounding, screen off, process restart and reboot as supported. Check device settings are restored correctly.
- Verify successful new-branch APK build and artifact. Report tests actually performed versus those needing Dave's physical phone. Do not claim an APK is complete unless a successful build produced it.

**Deliverable:** an installable APK from the new Android build, its download link and brief installation instructions. Keep the existing main branch safe. No further design changes unless Dave explicitly requests them.
