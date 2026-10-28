# POS Fingerprint (Android) — v2.0

Companion app for the Odoo module `pos_fingerprint_login`.

## What's new in 2.0
- New fingerprint logo (adaptive launcher icon, themed icon on Android 13+, splash screen).
- Redesigned UI: IBM Plex Sans, light and dark mode, Material 3 components.
- **Home** shows this phone's connection status, the **POS terminals** it has approved logins for,
  the **fingerprints** registered on it, and recent activity.
- **Settings** holds the one-time Odoo connection (locked once linked), plus checks for
  notifications, background running, full-screen alerts and the fingerprint sensor.
- Redesigned approval screen with a clear scan, success and error state.

Data from version 1.0 (connection and fingerprint names) carries over automatically.

## Build
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync (needs internet once).
2. Run on a phone (Android 11+ with a fingerprint enrolled) or Build > Build APK(s).

## Connect (once)
1. In Odoo: Point of Sale > Fingerprint App Keys > New, give the phone a name, click "Generate API Key" and copy it.
2. In the app tap the settings icon (or "Connect to Odoo" on the home screen), enter the Odoo address and the key,
   and tap "Connect this phone".
3. In the same Settings screen, make sure Notifications and Background running show as allowed.

## Use
- Employee form > Fingerprint tab > Configure Fingerprint: pick this phone, a notification arrives, scan your fingerprint.
- POS login screen > Scan Fingerprint: a notification arrives on the cashier's phone, scan to log in.
- POS terminals appear on the home screen after their first fingerprint login from this phone.

## Get the APK without Android Studio
Push the contents of this folder to a GitHub repository. The workflow in `.github/workflows/build-apk.yml`
builds it; download `pos-fingerprint-apk` from the run's Artifacts (Actions tab).

## Build the APK locally
Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s). File: `app/build/outputs/apk/debug/app-debug.apk`.
Install: copy to the phone, open it, allow "Install unknown apps" when asked.

## Fonts
IBM Plex Sans is bundled under the SIL Open Font License 1.1.
