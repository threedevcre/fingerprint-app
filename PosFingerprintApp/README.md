# POS Fingerprint (Android) — v2.3

Companion app for the Odoo module `pos_fingerprint_login`.

## What's new in 2.3
- **Odoo sign-ins:** requests from the Odoo login page are shown as "Odoo sign-in request" (globe
  badge, "Approve Odoo sign-in"), counted in their own **Odoo sign-ins** card on the home screen and
  no longer listed as a POS terminal. Fingerprint setups for Odoo users say so too.
- Fingerprints on the home screen are labelled **Odoo user** or **POS cashier**.
- **Glowing hero fingerprint:** soft light, real blur (Android 12+) and crisp ridges, coloured by
  status (teal online, amber offline, indigo not connected) and breathing gently while online.
  It stays still if animations are turned off on the phone.
- Matching glow behind the scanner on the approval screen; stat icons on the home card.
- Works with module 19.0.1.1.1 (sends "purpose" and "owner_type"); older modules still work.

## What's new in 2.2
- **Theme choice in the app:** Settings › Appearance › Theme lets you pick Light, Dark, or match the phone.
- **No notification while the app is open:** if the app is on screen when a fingerprint request
  arrives, the scanner opens straight away. When the app is closed or in the background (or the
  phone is locked) you get the notification as before. If you open the app normally while a request
  is waiting, the scanner opens automatically.
- **App logo in notifications:** request and status notifications show the full-colour logo.
  (The small status-bar icon stays a white fingerprint: Android draws every status-bar icon as a
  single-colour silhouette.)

## What's new in 2.0
- New fingerprint logo (adaptive launcher icon, themed icon on Android 13+, splash screen).
- Redesigned UI: IBM Plex Sans, light and dark mode, Material 3 components.
- **Home** shows this phone's connection status, the **POS terminals** it has approved logins for,
  the **fingerprints** registered on it, and recent activity.
- **Settings** holds the one-time Odoo connection (locked once linked), plus checks for
  notifications, background running, full-screen alerts and the fingerprint sensor.
- Redesigned approval screen with a clear scan, success and error state.

Data from version 1.0 (connection and fingerprint names) carries over automatically.

## Security (v2.1)

### What the app does
- **Fingerprint keys** are P-256 keys in the Android Keystore (StrongBox chip when available). Every
  signature needs a fresh strong-biometric scan. Keys are destroyed if a new fingerprint is enrolled,
  and the private key can never be exported. Odoo only ever receives a signature and a public key.
- **API key** is encrypted at rest with AES-256-GCM using a hardware-backed key. 1.0 plain-text keys
  are migrated automatically on first launch.
- **Transport:** https:// is required for every public address. http:// is accepted only for literal
  local-network addresses (10.x, 172.16–31.x, 192.168.x, localhost, *.local), after a warning, and
  Settings marks such a connection as "Not encrypted". Only system certificate authorities are
  trusted (user-installed or MDM certificates are ignored). Redirects are refused so the key can't
  be forwarded to another host. Responses are capped at 512 KB.
- **Requests** from the server are validated (type, token/challenge format, field sizes) before a
  notification or signature. An approval screen expires after 2 minutes.
- **Screens** block screenshots, screen recording and the recent-apps preview, hide overlay apps
  (Android 12+) and ignore taps while another app draws on top.
- **Lock screen** notifications show only "Fingerprint request", never employee or POS names.
- **No data leaves the phone** through cloud backup or device-to-device transfer.
- **Erase all data** in Settings deletes the connection, the encrypted key, all fingerprint keys
  and the history.

### Production builds — important
Debug APKs are debuggable, which lets anyone with USB access read app data. **Give cashiers only the
signed release APK.** One-time setup:
1. Create a signing key (keep it safe and backed up; you need the same key for every update):
   `keytool -genkeypair -v -keystore release.jks -alias posfp -keyalg RSA -keysize 4096 -validity 10000`
2. In GitHub: Settings › Secrets and variables › Actions, add
   `POSFP_KEYSTORE_BASE64` (output of `base64 -w0 release.jks`), `POSFP_KEYSTORE_PASSWORD`,
   `POSFP_KEY_ALIAS` (`posfp`), `POSFP_KEY_PASSWORD`.
3. Push. Download `pos-fingerprint-release-apk` from the run's Artifacts.
Without these secrets the workflow builds `pos-fingerprint-DEBUG-testing-only`, for testing only.

### Must be enforced by the Odoo module (the app can't do this)
The server decides who gets logged in, so these checks belong there:
- Verify the ECDSA signature over `kind:token:challenge` with the stored public key, every time.
- Challenges are random (≥128 bits), single-use, and expire within ~60–120 seconds.
- Bind each challenge to the POS session that requested it, so an approval can't be replayed elsewhere.
- Store only a hash of each phone's API key, allow revoking it, and rate-limit `/pos_fp/app/*`.
- Serve Odoo over HTTPS with a valid certificate, and log every approval and rejection.

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
