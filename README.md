# Bataria

A Material You battery monitor for Android 8+ (dynamic wallpaper colors on Android 12+, themed icon on 13+).

**Now** – animated liquid battery gauge, live charging panel (watts, mAh added, peak), sparkline stat tiles for current, power, voltage and temperature, screen-on vs screen-off drain, time to full / time left.
**Health** – expressive wavy health gauge, design vs. measured capacity, cycles (reported on Android 14+, estimated otherwise), health trend over time, every charge rated for battery-friendliness.
**History** – 6 h / 24 h / 3 d / 7 d interactive charts (press and drag to inspect) for level, current, temperature and voltage with charging periods shaded.
**Alerts** – charge-limit (e.g. unplug at 80 %), low battery and high temperature notifications.
**Widget** – Material You home-screen widget with level, current, temperature and health.
**Look & feel** – 7 animated backgrounds (Aurora, Bubbles, Constellation, Waves, Pulse, Grid, Space) with live previews, 11 accent colors (or your wallpaper via Material You) that each generate a full Material 3 palette, frosted-glass cards, a floating pill navigation bar, staggered card entrances, rolling-digit numbers, and a liquid gauge that tilts with your phone.
**Settings** – theme (system/light/dark), accent color, animated background, pure-black OLED mode, design-capacity override, current unit/sign override with raw-reading diagnostics.

## Build it

**Option A – Android Studio:** File › Open › this folder, let Gradle sync, press Run.

**Option B – no Android Studio (GitHub builds it):**
1. Create a new GitHub repo and push this folder to it.
2. Open the repo's **Actions** tab — the "Build APK" workflow runs automatically (~5 min).
3. Download the `Bataria-apk` artifact, unzip, and install `app-release.apk` on your phone
   (allow "install unknown apps" when prompted).

The release APK is signed with a debug key so it installs straight away; use your own key before publishing to Play.

## How accuracy is handled

* **Current** – Android reports `CURRENT_NOW` in µA, but some OEMs use mA, invert the sign, or report only
  positive values. The app learns the unit (no phone draws 20 A, so values that large must be µA) and the sign
  (unplugged, a battery can only discharge) from live readings. Both can be overridden in Settings, which also
  shows the raw value.
* **Health** – Android doesn't give regular apps the battery's internal health figure. Bataria measures it:
  during each charge of ≥15 % it records the charge-counter delta (falling back to integrating current) and
  divides by the % gained to get true full capacity. The median of the last five charges, compared to design
  capacity, gives health. Confidence goes Low (instant charge-counter estimate) → Medium (1–2 charges) → High
  (3+ charges, or the battery reports its capacity directly).
* **Design capacity** – read from the battery if accessible, otherwise from the manufacturer's power
  profile. If it shows "Unknown" or looks wrong, enter your phone's rated mAh in Settings.
* **Background monitoring** has two modes. By default it's quiet: no notification, a check every 15 minutes
  (health stays accurate because each charge is measured from its start and end readings). Turn on
  **Live notification** for live stats in the notification shade, checks every few seconds and instant alerts.
  On aggressive OEM skins, set the app's battery usage to "Unrestricted".
