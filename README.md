# Obelisk

Obelisk is an unofficial native Android client for HentaiVerse. It presents supported game pages in a mobile-first Jetpack Compose interface while using the game's web services for account sessions, page data, and actions.

## Features

- Native navigation for Character, Bazaar, and Battle pages across Persistent and Isekai worlds.
- Mobile layouts for character management, equipment, inventory, shops, market, mail, Monster Lab, and other supported pages.
- A focused battle screen with a scrollable enemy list, action selection, and controls placed for portrait use.
- English and Simplified Chinese interface options.
- Searchable selectors, dialogs, and confirmation prompts for supported forms and consequential actions.
- Jsoup and OkHttp handle game pages and actions. The config store and optional forum sign-in use WebViews.

Supported actions and page availability depend on the response returned by HentaiVerse. Obelisk is not affiliated with, endorsed by, or supported by E-Hentai or HentaiVerse.

## Requirements

- Android 8.0 (API 26) or later.
- For custom battle scripts: a system WebView that supports isolated JavaScript execution.
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

The gear icon in the battle top bar opens **Obelisk settings**; use Back to return to the encounter. Battlefield, style, and selected configuration are managed in **Battle configurations** before or during a fight.

### Unlock or upgrade an ability

Open **Character → Abilities**. Tap a category above the list, or use the grid button to see all categories grouped by weapon, armor, and magic. Each ability shows its rank. Tap **Unlock** or **Upgrade** beside the name to spend the displayed AP; tap the **name** instead to inspect effects or choose a slot without spending points. Locked rows show known level or point shortfalls. **Equipped upgrades** checks equipped abilities across categories and lists only upgrades offered by the game. Spending AP is always a separate tap. **Slots** manages equipped abilities; unlocking a slot uses mastery points, not AP.

### Create and edit battle rules

Open **Obelisk settings → Battle configurations → New configuration**. Enter a name, choose a fighting style and battlefield, then tap **Create**. For a basic attack configuration:

1. Tap **Add rule**, choose **Target**, set the target order and tap **Save changes**.
2. Add a **Normal attack** rule below it and save. Target selection does not spend a turn. The attack acts on the selected monster.
3. Tap **Apply configuration** to publish and pin this configuration. Tap **Auto** in battle when you want to start the round.

Tap any rule to edit its action, conditions or custom JavaScript. Drag a rule by its handle to reorder it within its group, use its switch to enable or disable it, or open **⋮** to move or delete it. Saved edits stay in the draft until you apply them.

### Import, share and select configurations

- **Import file** accepts JPX `.txt` and `.json` files up to 2 MiB. Check the preview and tap **Add to library**. Each mode containing rules becomes a separate entry, such as **Two-handed · General**. Importing the same file again restores missing modes and preserves edits. Use **⋮ → Set alias** to distinguish variants.
- Open **Config store** to browse shared configurations. Open an entry and tap **Save**. Obelisk checks the download against its published SHA-256 digest and adds compatible battle modes to your drafts. Statistics and configurations for another world remain available in the store’s local library. When saving an entry again, you can update your existing copy or save another. Browse while signed out. Sign in to GitHub to share a configuration, rate it or post a comment. A profile’s **⋮ → Share** opens the sharing form.
- With **Apply automatically** enabled, Obelisk uses the detected fighting style and tries the battlefield-specific configuration before **General**. When several variants match, it uses the first nonempty one in library order. Open **Battlefield strategies** to assign a particular variant or a **General** fallback for that world. **Match automatically** clears a battlefield assignment.
- **Apply configuration** pins a profile across battlefields and styles. Turning off **Apply automatically** pins the current selection. Turn it back on to resume automatic selection.
- **⋮ → Export configuration** saves one profile, including its draft edits, as an importable JSON file. Removing a profile clears its assignments and manual pin.

### Run rules during a fight

Tap **Auto** at the start of each round to run the selected rules. Obelisk submits one action at a time and uses the server’s reply to choose the next move. Enable **Auto-advance rounds** in settings to load the next wave in place. Each new round waits for a manual action or another tap on **Auto**. **Pause** is available while a combat action is in flight. Opening settings, leaving the foreground, or receiving an uncertain result pauses the strategy. Riddles and final results wait for you.

Rules can select targets, attack, cast skills or spells, use items, switch stances, apply smart debuffs, or stop. Put a **Target** rule before an offensive spell or normal attack. `T1`–`T3` and spell-school conditions use the element selected in the JPX-PLUS profile, falling back to the detected school when the profile uses automatic selection. Conditions can use vitals, effects, cooldowns, difficulty, rounds, recent actions and observed cast counts. If a built-in rule requires a value the app has yet to observe, such as the player’s level or the start of a round, Auto can pause and identify the rule. Monster class, power level and last-update conditions use the same public Sukka database as JPX-PLUS. Obelisk caches a daily snapshot per world and saves confirmed Scan results. It looks up spawned IDs first and uses an exact name only when the spawn ID is missing. An offline first run can have no matching records. Keyboard shortcut groups are available for editing and export. Auto evaluates the battle rules.

### Custom scripts

Enable the script rule, apply the configuration and tap **Auto** in battle. This rule attacks the first living monster. Returning `false` lets the next rule run.

```js
return jpx.doAttackGoNext(jpx.monstersObj.activeMonsters[0]);
```

Scripts can read vitals, rounds, cooldowns, the equipped spell school and each monster’s effects. They can call JPX action handlers with conditions or use helpers such as `doAttackGoNext`, `doSpellGoNext` and `doToggleGoNext`. The bridge accepts one available game action per turn. Cast counts come from the observed log. When the spawn record is missing, monster HP uses a relative 100-point scale.

The isolated JavaScript engine keeps globals and in-memory `localStorage` and `sessionStorage` across script rows and turns for the active configuration and encounter. The `document` adapter exposes item slots, effect icons and the battle log to common JPX helpers. Scripts that depend on other page elements, browser plugins, network access, browser cookies, persistent browser storage or an asynchronous result need adaptation. Script errors and timeouts pause Auto at the affected rule.

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
