# Obelisk

Obelisk is an unofficial native Android client for HentaiVerse. It presents supported game pages in a mobile-first Jetpack Compose interface while using the game's web services for account sessions, page data, and actions.

## Features

- Native navigation for Character, Bazaar, and Battle pages across Persistent and Isekai worlds.
- Mobile layouts for character management, equipment, inventory, shops, market, mail, Monster Lab, and other supported pages.
- A focused battle screen with a scrollable enemy list, action selection, and controls placed for portrait use.
- English and Simplified Chinese interface options.
- Searchable selectors, dialogs, and confirmation prompts for supported forms and consequential actions.
- Page parsing and requests handled with Jsoup and OkHttp; WebView is limited to the optional forum sign-in flow.

Supported actions and page availability depend on the response returned by HentaiVerse. Obelisk is not affiliated with, endorsed by, or supported by E-Hentai or HentaiVerse.

## Requirements

- Android 8.0 (API 26) or later.
- For development: Android Studio, JDK 17 or later, and Android SDK Platform 37.

## Build on Windows

1. Clone this repository and open the project directory in Android Studio, or open a PowerShell terminal in the project directory.
2. Install Android SDK Platform 37 with Android Studio's SDK Manager.
3. Build the debug APK:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

The APK is written to `app/build/outputs/apk/debug/`. To run the local unit tests, use:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Sign In and Use

1. Install and open the debug build on an Android device.
2. Sign in with your credentials, imported cookies, or the forum web login. Obelisk keeps one session on this device. To use a different login, sign out in **Obelisk settings** first.
3. Choose a world, tap a bottom navigation category, then select a page from its list. Dismiss the list to stay on the current page. Battle controls appear when the server returns an active encounter.

During combat, tap **Strategy panel** in the top bar to see the detected weapon style, spell affinity, and the bonuses behind them. The gear icon opens **Obelisk settings**; use Back to return to the encounter. The panel uses a pre-battle equipment snapshot, not a request on every turn. If no snapshot is available, open Equipment before the next encounter or let the battle lobby load.

### Unlock or upgrade an ability

Open **Character → Abilities**. Tap a category above the list, or use the grid button to see all categories grouped by weapon, armor, and magic. Each ability shows its rank. Tap **Unlock** or **Upgrade** beside the name to spend the displayed AP; tap the **name** instead to inspect effects or choose a slot without spending points. Locked rows show known level or point shortfalls. **Slots** manages equipped abilities; unlocking a slot uses mastery points, not AP.

### Choose a JPX battle configuration

Open **Obelisk settings → Battle configurations → Import file** and choose a JPX `.txt` or `.json` (up to 2 MiB). Check the preview and tap **Add to library**. A file such as `battle.json` is just a container: **One-handed · General**, **Two-handed · General**, and other modes with rules appear directly in your list. Empty slots are omitted from the preview and the library. If you imported that file before, re-importing the same file also clears its untouched empty entries; it preserves renamed, edited, or assigned entries. No presets are bundled.

- **Edit:** tap a configuration to go straight to its rules. Drag the handle on the left to reorder rules within a group. Use the switch to enable or disable a rule, tap its name to edit it, or use **⋮** to move it one place or delete it. **Apply configuration** publishes the draft; until then, battles use the previously applied rules.
- **Manage variants:** different imports can supply alternatives for the same mode. Use **⋮ → Set alias** to tell them apart. Re-importing the same file restores missing modes without resetting existing edits.
- **Automatic (default):** the library shows the detected style and battlefield separately from the chosen strategy. It tries the battlefield-specific configuration first, then **General** for the same style. Empty modes are skipped; if multiple imports match, the first nonempty variant imported wins. Without a verified style, nothing is automatically selected.
- **Assign by battlefield:** open **Battlefield strategies**, choose a style, then tap a battlefield. For example, assign **Staff · General** to Item World and a different staff variant to the Arena. Set a row back to **Match automatically** to remove its assignment. You can also assign a **General** fallback. These settings are per world and can be changed before or during battle.
- **Manual:** choose **Apply configuration** on a profile to publish its edits and pin it across battlefields and styles. Turning off **Apply automatically** pins the current choice without publishing edits; turn it back on to resume routing. The in-battle strategy panel shows the chosen profile and why it was selected. Selecting or publishing a strategy never starts combat on its own.
- **Export or remove:** either action in a configuration’s **⋮** menu affects only that entry. The exported JSON contains its saved rules, including unapplied edits, and can be imported again. Removing a variant clears its assignments and any manual pin to it; other modes and the original file stay intact. For example, choose **Staff · General → ⋮ → Export configuration** to save only that profile. Use **Import file** to add it back on its own; no full battle file is required.

### Run rules during a fight

Tap **Run rules** in the battle top bar. Obelisk reads the selected configuration in order and submits the first available matching action through the same turn request as a manual tap. **Pause** stops the next action; it cannot cancel one already sent. Opening settings or the strategy panel, leaving the foreground, an uncertain response, or round completion stops execution. Resume explicitly; the app does not solve riddles or advance rounds.

Rules can select targets, attack, cast skills or spells, use items, switch stances, apply smart debuffs, or stop. Put a **Target** rule before a normal attack or offensive spell; selecting a target does not itself spend a turn. `T1`–`T3` use the detected spell school. Keyboard groups are kept for editing and export, not executed by the loop. No JavaScript is evaluated. Monster-database filters have no matches without the external database, as in JPX. If a rule needs an unavailable input (for example historical action totals, an unknown spell school, or a player level that the battle page does not provide), the app pauses and identifies the rule instead of guessing.

The app needs an internet connection and a valid HentaiVerse session. Account cookies are sensitive credentials: do not share exported or imported cookie values, and use only an account you are authorized to access. The app disables Android backup and does not allow cleartext network traffic.

## Development

The Android app is under `app/`. Kotlin UI code is in `app/src/main/java/com/mitahv/mobile/`; parsers and networking code are in its `data/` package. Unit tests and sanitized HTML fixtures are under `app/src/test/`.

To extract standalone reference JSON from the pinned JPX and JPX-PLUS userscripts, run the optional command below. Choose an output folder **outside the app**. Only `battle.json` is a battle import; `stats.json` and `shortcuts.json` are for reference. Add `--check` to verify the output without writing files.

```powershell
node tools/extract_jpx_defaults.cjs ../jpx20261111.js ../JPX-PLUS/JPX-PLUS.user.js ../configs/jpx-defaults
```

Before opening a pull request, run the relevant unit tests and Android lint checks:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

Keep test fixtures free of personal information and live session data. Do not include credentials, cookies, local SDK configuration, generated build output, or private device logs in a change.

## License

This repository does not currently include a license file. Until a license is added, no permission to redistribute or reuse this project is granted.
