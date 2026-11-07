# IntentGate 2.0 — Android source project

## Included
- User-selected launcher-visible apps (not limited to Telegram/Instagram).
- Custom prompt text shown before the required purpose field.
- Three separate screens: Home, Apps, and Statistics.
- Local usage history grouped by day, with app, purpose, start time, and foreground duration (90 days / up to 5,000 visits).
- Per-app time allowances persist across app switches and service restarts.
- Accessibility service no longer reads `Settings.Secure.ENABLED_INPUT_METHODS`, which caused a `SecurityException` on Android 14+ when targetSdk is above 33.

## Build with GitHub Actions
Upload the *contents* of this `IntentGate` directory to the repository root. The workflow uses Android SDK 35 and Gradle 8.9. This project has no Gradle wrapper, so the workflow invokes the installed `gradle` command.

## Important limitations
- Accessibility events are used to identify the foreground app; screen contents are not read.
- While the gate overlay is visible, transient windows such as the keyboard and system dialogs are ignored so they do not dismiss the prompt. The user can leave through the explicit Leave app button.
- Usage history measures observed foreground visits. Opening a separate system file picker pauses that visit's timer; the per-app authorization deadline remains active.
- This is a best-effort behavioral tool, not an OS security boundary. OEM background restrictions or disabling Accessibility can interrupt it.
- Source has been statically reviewed, but this environment does not have Android SDK/Gradle dependencies available for a verified APK build or on-device test.
