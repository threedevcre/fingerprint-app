# POS Fingerprint (Android)

Companion app for the Odoo module `pos_fingerprint_login`.

## Build
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync (needs internet once).
2. Run on a phone (Android 11+ with a fingerprint enrolled) or Build > Build APK(s).

## Pair (once)
1. In Odoo: Point of Sale > Fingerprint App Keys > New, give the phone a name, click "Generate API Key" and copy it.
2. In the app enter the Odoo URL + the key, tap Connect. Allow notifications and background running.

## Use
- Employee form > Fingerprint tab > Configure Fingerprint: this phone appears in the list; pick it,
  a notification arrives, scan your fingerprint.
- POS login screen > Scan Fingerprint: a notification arrives on the cashier's phone, scan to log in.

## Get the APK without Android Studio
Push the contents of this folder to a GitHub repository. The workflow in `.github/workflows/build-apk.yml`
builds it; download `pos-fingerprint-apk` from the run's Artifacts (Actions tab).

## Build the APK locally
Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s). File: `app/build/outputs/apk/debug/app-debug.apk`.
Install: copy to the phone, open it, allow "Install unknown apps" when asked.
