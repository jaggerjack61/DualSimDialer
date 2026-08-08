# DualSimDialer

DualSimDialer is a Kotlin/Jetpack Compose default Phone app for Android 12+ (`minSdk 31`, target/compile API 36). It routes normal calls through the selected Telecom `PhoneAccountHandle`, keeps SIM aliases/colors in DataStore, reads contacts and call history live from Android providers, and presents Telecom calls through `InCallService`.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The acceptance artifact is copied to `outputs/DualSimDialer-debug.apk`.

## Device setup

1. Install the debug APK.
2. Open DualSimDialer and choose **Make default Phone app**.
3. Grant only the requested call-log, phone, contacts, notification, and Bluetooth permissions.
4. Use SIM settings to set app-local aliases/colors. Emergency numbers are delegated to the preloaded Phone app.

Call log and contacts are not copied into persistent storage. DataStore contains only serialized Telecom account keys, SIM aliases, and colors.
