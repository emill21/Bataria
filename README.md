# Battery Pulse

A Material You battery monitor for Android 8+ (dynamic wallpaper colors on Android 12+, themed icon on 13+).

**Now** – live level ring, current (mA), power (W), voltage, temperature, time to full / time left, charge counter.
**Health** – estimated health %, design vs. measured capacity, cycle count (Android 14+), every measured charge.
**History** – 6 h / 24 h / 3 d charts of level, current and temperature, plus average drain and charge rate.
**Settings** – background monitoring, design-capacity override, current unit/sign override, raw-reading diagnostics.

## Build it

**Option A – Android Studio:** File › Open › this folder, let Gradle sync, press Run.

**Option B – no Android Studio (GitHub builds it):**
1. Create a new GitHub repo and push this folder to it.
2. Open the repo's **Actions** tab — the "Build APK" workflow runs automatically (~5 min).
3. Download the `BatteryPulse-apk` artifact, unzip, and install `app-release.apk` on your phone
   (allow "install unknown apps" when prompted).

The release APK is signed with a debug key so it installs straight away; use your own key before publishing to Play.

## How accuracy is handled

* **Current** – Android reports `CURRENT_NOW` in µA, but some OEMs use mA, invert the sign, or report only
  positive values. The app learns the unit (no phone draws 20 A, so values that large must be µA) and the sign
  (unplugged, a battery can only discharge) from live readings. Both can be overridden in Settings, which also
  shows the raw value.
* **Health** – Android doesn't give regular apps the battery's internal health figure. Battery Pulse measures it:
  during each charge of ≥15 % it records the charge-counter delta (falling back to integrating current) and
  divides by the % gained to get true full capacity. The median of the last five charges, compared to design
  capacity, gives health. Confidence goes Low (instant charge-counter estimate) → Medium (1–2 charges) → High
  (3+ charges, or the kernel fuel gauge is readable).
* **Design capacity** – read from the kernel fuel gauge if accessible, otherwise from the manufacturer's power
  profile. If it shows "Unknown" or looks wrong, enter your phone's rated mAh in Settings.
* **Background monitoring** runs as a low-priority foreground service (silent notification with live stats) and
  restarts after reboot. On aggressive OEM skins, set the app's battery usage to "Unrestricted".
