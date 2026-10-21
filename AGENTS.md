# Liftoff — playbook for AI agents

You are helping someone turn their Google TV / Android TV into a clean, fast, ad-free TV with the
Liftoff home screen. You do everything over ADB from their computer. Follow this file step by step.
Read it all before you run anything.

## The rules (non-negotiable)

1. **Back up first.** Run `scripts/backup.sh` before any change. No backup, no changes (the scripts enforce this).
2. **Disable, never uninstall.** Preinstalled apps are only ever *disabled* (`pm disable-user`), so
   `scripts/undo.sh` can bring every one back. The only thing ever uninstalled is Liftoff itself.
3. **Show the list and wait for OK.** Run `scripts/cleanup-list.sh`, show the user what you plan to
   disable in plain language (app name and why), and wait for an explicit yes. Drop anything they want to keep.
4. **Never touch** Google Play Services, Play Store, keyboards / input methods, TV inputs and HDMI,
   remote pairing, Bluetooth, AirPlay, Settings, or system UI. `scripts/lib.sh` refuses these
   (`PROTECTED_RE`); don't work around it.
5. **No root, no exploits, no sideloaded mods.** Only standard `adb` commands.
6. **No sign-ins.** Never type passwords, sign into accounts, or accept terms on the TV for the user.
   If something needs a sign-in, tell the user to do it with the remote.
7. **No piracy.** Don't install or recommend pirate streaming apps, cracked APKs, or sites that host them.
   Only install apps from the Play Store (the user does that) or from a project's official GitHub releases.
8. **Risky packages need a second OK.** Items marked `risky` in `catalog/debloat.tsv` (the Google TV
   home and its setup app) can make some TVs forget saved Wi-Fi. Only disable them with `--risky`
   after telling the user exactly that, and after they confirm they have Ethernet or their Wi-Fi password.
9. **Narrate.** Before each step, say in one line what you're about to do and why.

## 0. Prerequisites (on the computer)

- macOS or Linux (Windows: use WSL), `python3`, `curl`, `unzip`.
- `adb`, a JDK 17+ and Android build tools. If the user doesn't have them, run
  `scripts/setup-toolchain.sh` (downloads into `.toolchain/`, nothing system-wide; it asks the user to
  accept Google's Android SDK license — let *them* answer that prompt).

## 1. Turn on ADB on the TV (the user does this with the remote)

Tell the user:
1. Settings → System → About → scroll to **Android TV OS build** → press OK 7 times ("You are now a developer").
2. Settings → System → Developer options → turn on **USB debugging**.
3. Keep the TV on the same network as this computer.

Then run `scripts/find-tv.sh`. The first time, the TV shows **"Allow USB debugging?"** — the user must
pick **Always allow from this computer** and press OK. Run `find-tv.sh` again until it shows the model.

## 2. Ask what they want

Ask, in one short message:
- **Which apps** should be on the home screen? (e.g. "Netflix, YouTube, Spotify, Plex")
- **Where are they?** City or ZIP, for weather — or no weather. Fahrenheit or Celsius? 12 or 24-hour clock?
- **Background:** NASA's Astronomy Picture of the Day (changes daily), the bundled SpaceX Starman photo, or their own image?
- **Govee lights** with LAN Control? (optional)
- **Ad-blocking DNS** (AdGuard DNS for the whole TV)? Default yes.

Then:
- Run `scripts/apps.sh` to see installed apps and their package names; match the user's names to
  packages (`catalog/apps.tsv` helps). If an app they want isn't installed, tell them to install it
  from the Play Store with the remote, then re-run `apps.sh`.
- Look up latitude/longitude for their city yourself (round to 2 decimals). Don't store a street address.
- For Govee: `python3 scripts/govee.py scan` lists lights that have LAN Control on.

## 3. Write liftoff.json

`cp liftoff.example.json liftoff.json` and fill it in:

| key | meaning |
|---|---|
| `tv` | the TV's `ip:5555` from `find-tv.sh` |
| `apps` | `[{label, package}]` — shown alphabetically; missing apps are skipped |
| `weather` | `{latitude, longitude, units: "fahrenheit"\|"celsius"}`, or remove the key for no weather |
| `nowPlaying.packages` | apps whose music shows in the Now Playing card; `[]` = any app |
| `lights.govee` | Govee light IPs; empty = no Lights entry. `lights.label` renames it |
| `background.mode` | `"apod"` (NASA daily, falls back to the image) or `"image"` |
| `background.image` | optional path to your own 1920×1080 `.png`/`.jpg`; default is Starman |
| `clock.24h` | `true` for a 24-hour clock |
| `tweaks.animationScale` | `0.5` makes menus snappier; remove to leave alone |
| `tweaks.privateDns` | e.g. `dns.adguard-dns.com`; remove to leave alone |
| `tweaks.tclPrivacySwitches` | TCL only: turns off viewing-data sharing and diagnostics |
| `tweaks.noBackground` | packages that may not run in the background (e.g. YouTube) |

## 4. Back up

`scripts/backup.sh` → saves packages, settings and the current Home app to `state/<tv-serial>/`.

## 5. Build and install the home screen

```
scripts/build.sh            # → build/Liftoff.apk
scripts/install.sh          # installs, grants overlay + notification access, sets it as Home
```
Use `scripts/install.sh --no-home` if the user wants to try it before switching.
Check it with `scripts/screenshot.sh` and look at the image. The user should see the clock, their apps,
and (after a few seconds) the weather.

## 6. Clean up (only after OK)

1. `scripts/cleanup-list.sh` — show the user the table, grouped by category, in plain words.
   Mention what each `caution` item does (casting, voice search, USB media) and ask if they use it.
2. Wait for "yes" (or a list of things to keep).
3. `scripts/debloat.sh <package> <package> …` with exactly the approved packages.
4. `risky` ones only with `--risky`, only after rule 8.

## 7. Tweaks

`scripts/tweaks.sh` applies `tweaks` from `liftoff.json`, recording every old value.

## 8. Wrap up

Tell the user what changed (apps disabled, tweaks, new Home), and how to undo:
- everything: `scripts/undo.sh`
- one app: `adb -s <tv> shell pm enable --user 0 <package>`

Remind them: if they ever want a TCL firmware update and disabled `com.tcl.ttvs`, re-enable it first.

## Changing things later

- Edit `liftoff.json`, then `scripts/build.sh && scripts/install.sh`.
- Switch the background without rebuilding:
  `adb -s <tv> shell am start -n app.liftoff.home/.HomeActivity --es bg image` (or `--es bg apod`).
- Give Home back to Google TV without undoing anything else:
  `adb -s <tv> shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity`

## Handy tools

`scripts/key.sh home|back|ok|up|down|left|right|play|vol+|vol-|sleep|wake` · `scripts/ui.sh` (what's on
screen, focused item marked `[*]`) · `scripts/screenshot.sh` · `scripts/youtube.sh <url>` ·
`scripts/govee.py <ip> on|off|color r g b|temp K|bright N`

## Known gotchas

- **TCL TVs:** the notification listener (weather + Now Playing) won't bind without
  `appops set app.liftoff.home APP_AUTO_START allow`. `install.sh` does this.
- **After every `install -r`** the listener must be re-bound (disallow, then allow). `install.sh` does this.
- **Wi-Fi:** disabling the Google TV home or its setup app (`setupwraith`) wiped saved Wi-Fi on one TV.
  That's why they're `risky`.
- **Screenshots on MediaTek TVs:** `exec-out screencap` returns junk; `screenshot.sh` saves on the TV and pulls.
- **Video apps screenshot black** (DRM). That's normal.
- **NASA APOD:** the old api.nasa.gov APOD now returns a placeholder; Liftoff reads science.nasa.gov/apod.
  Video days fall back to the bundled image.
- **TCL's browser** is "BrowseHere", package `com.tcl.browser`.
- **TCL Guard** may restart at boot even when disabled. Leave it; stopping it would need root.
- **Other launchers (e.g. FLauncher) cache app banners**; if you switch back to one, it may show stale icons until its cache clears.
- **`pm disable-user` fails** on some protected firmware packages. Skip them; don't try harder.
