# Brand and store assets

Everything here derives from `logo.svg` (the owner's logo: a dark rounded tile with two cards and a
check). Palette: tile `#1C1A17`, cream card `#F4F1EA`, indigo card `#4F46E5`.

| File | What it is | Used for |
|---|---|---|
| `logo.svg` | The logo tile, as delivered | Source of truth |
| `logo-square.svg` | Same, with square corners | Play icon (Play applies its own mask) |
| `play-icon-512.png` | 512 × 512, 32-bit PNG | Play listing icon, F-Droid `images/icon.png` |
| `feature-graphic.svg` / `.png` | 1024 × 500, no transparency | Play feature graphic, F-Droid `featureGraphic.png` |
| `widget-preview.svg` | Widget with sample numbers | `app/src/main/res/drawable-nodpi/widget_preview.png` (pre-Android 12 picker) |
| `screenshots/phone/` | 1080 × 2400, demo data, 9 light + 2 dark | Play and F-Droid phone screenshots |
| `screenshots/tablet-7/`, `tablet-10/` | 1200 × 1920 portrait, 2560 × 1600 landscape | Play tablet screenshots |
| `generate_icon_drawables.py` | Writes the vector drawables below | See "In the app" |
| `demo/` | Demo collection and a mock AI server | Re-shooting the screenshots |

## In the app

`generate_icon_drawables.py` (needs `pip install shapely`) writes, from the logo geometry:

- `app/src/main/res/drawable/ic_launcher_foreground.xml`: the cards and check inside the 66 dp safe
  zone of the 108 dp canvas. The background is the colour `ic_launcher_background` (`#1C1A17`).
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: the themed-icon layer (Android 13+): one
  shape, the check cut out, a gap between the cards.
- `app/src/main/res/drawable/ic_splash.xml`: the logo on a dark disc. The Android 12 splash mask
  shows a 192 dp circle of a 288 dp icon, so the disc fills it exactly. `Theme.Mnemo.Starting`
  puts it on the window background (light and dark).
- `core/designsystem/src/main/res/drawable/mnemo_logo.xml`: the whole tile, for `MnemoLogo`
  (onboarding, Settings › About).

minSdk is 29, so adaptive icons cover every launcher and there are no `mipmap-*dpi` fallbacks.
Notification icons (`core_data_ic_reminder`, `core_data_ic_transfer`) stay plain Material glyphs:
a status-bar icon has to be a single-colour silhouette, and the logo's two overlapping cards don't
read at 24 px.

## Regenerating the graphics

The SVG text uses the bundled fonts. Point fontconfig at them (`/tmp/fonts.conf`):

```xml
<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig><dir>/path/to/mnemo/core/designsystem/src/main/res/font</dir><cachedir>/tmp/fontcache</cachedir></fontconfig>
```

Then, from `docs/release/assets`:

```bash
rsvg-convert -w 512 -h 512 logo-square.svg -o play-icon-512.png && magick play-icon-512.png -alpha on PNG32:play-icon-512.png
FONTCONFIG_FILE=/tmp/fonts.conf rsvg-convert feature-graphic.svg -o /tmp/fg.png && magick /tmp/fg.png -background '#1C1A17' -alpha remove -alpha off feature-graphic.png
FONTCONFIG_FILE=/tmp/fonts.conf rsvg-convert widget-preview.svg -o ../../../app/src/main/res/drawable-nodpi/widget_preview.png
```

`feature-graphic.svg` embeds two phone screenshots, so re-shoot those first if the UI changes.

## Re-shooting the screenshots

The screenshots are real app screens on a 1080 × 2400 emulator (Pixel 8, API 35), with a demo
collection instead of test data:

1. Build and install the debug app on the emulator only (`./gradlew :app:assembleDebug`, then
   `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`; a plain
   `installDebug` also installs on every real device that's plugged in). Launch it once.
2. Seed the demo collection. `demo/seed_demo.py` fills a copy of the app's database: 7 decks
   (Spanish, Biology with two subdecks, Organic Chemistry with an exam date, World History, Cloud
   Networking), 177 notes of every card type, 80 days of review history for Analytics, and an
   "Ollama" provider that points at the mock server. Pull `databases/mnemo.db` (plus `-wal` and
   `-shm`) with `adb exec-out run-as com.yahyafati.mnemo cat …`, run the script on the copy, and push
   it back with the app force-stopped, deleting the device's `-wal` and `-shm`. Dates are relative
   to the day you run it.
3. For the AI screens, run `python3 demo/mock_ai_server.py` and `adb reverse tcp:11435 tcp:11435`.
   It answers Smart Extract, Co-Author and Explain with canned text; no key or network is needed.
4. Clean status bar: `adb shell settings put global sysui_demo_allowed 1`, then
   `adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941` (and
   `battery`, `network`, `notifications`). Light theme: `adb shell cmd uimode night no`. Take
   pictures with `adb exec-out screencap -p`.
5. Tablets: `adb shell wm size 2560x1600` + `wm density 240` (10", landscape) or `1200x1920` at 240
   (7", portrait), then `wm size reset` and `wm density reset`.
6. Undo everything on the emulator when done (demo mode off, `uimode night auto`, `wm … reset`).

The soft keyboard's floating toolbar shows over text fields on the emulator. Hide it for a shot with
`adb shell ime disable com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.
