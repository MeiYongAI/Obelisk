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
2. Sign in using the available account login, cookie import, or forum web-login option.
3. Choose a world, tap a bottom navigation category, then select a page from its list. Dismiss the list to stay on the current page. Battle controls appear when the server returns an active encounter.

During combat, tap **Strategy panel** in the top bar to see the detected weapon style, spell affinity, and the bonuses behind them. The gear icon opens **Obelisk settings**; use Back to return to the encounter. The panel uses a pre-battle equipment snapshot, not a request on every turn. If no snapshot is available, open Equipment before the next encounter or let the battle lobby load.

### Choose a JPX battle configuration

Open **Obelisk settings → Battle configurations → Import file**, choose a JPX battle `.txt` or `.json` (up to 2 MiB), then tap **Add to library**. Importing opens the file without applying it. No presets are bundled.

- **Inspect and edit:** tap a saved file, then a mode to see its ordered rules. Tap a rule to edit its action and conditions. You can also add rules, disable them, or use the row menu to move or delete them. Saving changes edits the library copy only.
- **Apply:** use **Apply configuration** for the whole file or **Apply this mode** for one mode. Confirm to update the active snapshot in the current account and world. Other modes stay intact. This does not execute custom scripts or play battle turns automatically.
- **Export:** use **⋮ → Export file** in the library, or the export icon inside a file or mode. Pick a destination in the Android file picker. The JSON contains the saved edits, whether applied or not. Exporting a mode excludes unrelated global settings and modes.

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
