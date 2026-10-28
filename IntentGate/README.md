# IntentGate — Android source project

An Android accessibility-overlay prototype that asks for a required purpose and time allowance before Telegram or Instagram can be used. Telegram is enabled by default; Instagram is opt-in.

## Build
1. Open this folder in Android Studio (recent version supporting Android Gradle Plugin 8.7.3 / JDK 17).
2. Allow Gradle to download dependencies, then select **Build > Build APK(s)**.
3. Install on an Android 8+ device. Open IntentGate, select protected apps and tap **Enable IntentGate in Accessibility settings**.
4. Find IntentGate under downloaded/installed accessibility services and enable it. Some OEMs require allowing restricted settings for sideloaded apps.

## Design and limitations
- The full-screen `TYPE_ACCESSIBILITY_OVERLAY` appears over a selected foreground app; it requires a nonblank purpose and a duration before dismissing.
- When the allowance expires while the protected app is foregrounded, the gate appears again.
- Leaving the app resets authorization; returning prompts again. The app does not read messages or screen contents.
- This is a best-effort behavior intervention, **not** an OS-enforced security boundary. A user can disable the accessibility service, uninstall the app, or sometimes use system navigation to bypass it. OEM background-service policies can affect reliability.
- This starter project has no cloud services, analytics, or internet permission. Only the last purpose and duration are stored locally.
- Accessibility APIs are subject to Google Play policies. Review the applicable policy and disclosures before distribution.
- Source code is provided; an APK was not built in this environment because the Android SDK/Gradle build environment is not installed.
