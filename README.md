# BLEChat Android

IRC-style chat over a Bluetooth LE mesh network - no internet required, no servers, no phone numbers.

This repo contains the Android client built with Kotlin + Jetpack Compose, using a foreground service to keep the mesh running in the background.

![App icon](fastlane/metadata/android/en-US/images/icon.png)

## Quick start

- Android Studio: open the repo folder and run the `app` configuration on a physical device.
- CLI:
  - macOS/Linux: `./gradlew :app:installDebug`
  - Windows: `gradlew.bat :app:installDebug`

## Features

- Peer-to-peer messaging over Bluetooth LE (mesh-style relay between nearby devices)
- Channels and direct messages (IRC-style UX)
- Optional internet transports for relay reachability (Nostr/WebSocket) and Tor routing (Arti)
- QR-based verification flows (camera)
- Voice notes and media/file helpers

## Requirements

- Android Studio (or Gradle CLI)
- JDK 17
- Android device with Bluetooth LE (BLE)
- `minSdk 26` (Android 8.0), `targetSdk 35`

## Build

### Debug (local development)

```bash
./gradlew :app:assembleDebug
```

Install on a connected device:

```bash
./gradlew :app:installDebug
```

### Release APKs (ABI splits)

The app is configured to produce ABI-split APKs for `assemble*` tasks (plus a universal APK).

```bash
./gradlew :app:assembleRelease
```

## Run / Permissions

BLE discovery and background mesh operation require runtime permissions and device settings that vary by Android version/manufacturer. The app requests the relevant permissions at runtime, including:

- Bluetooth: Scan / Connect / Advertise
- Location: required by Android for BLE scanning (and background scanning where enabled)
- Notifications: for message alerts
- Foreground service: to keep the mesh active in the background
- Optional: Microphone (voice notes), Camera (QR), Media access (sharing)

For best reliability, disable battery optimization for the app so Android doesn't stop the mesh service while the app is backgrounded.

## Tor (Arti) notes

This repo includes prebuilt native libraries under `app/src/main/jniLibs/` for a custom Arti (Tor-in-Rust) bridge.

- Rebuild / verify instructions: `tools/arti-build/README.md`

## CI

GitHub Actions runs:

- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- APK builds: `./gradlew assembleDebug` and `./gradlew assembleRelease`

Tagged releases (`vX.Y.Z`) build ABI-split release APK artifacts and attach them to a GitHub Release.

## Project structure

Main module: `app/`

Key packages:

- `ui/`: Compose UI + ViewModels (MVVM)
- `service/`: foreground service + boot/notification receivers
- `mesh/`, `protocol/`, `noise/`, `crypto/`: transports, wire protocol, and encrypted channels

## Contributing

PRs welcome. Before opening a PR:

- Run: `./gradlew testDebugUnitTest lintDebug`
- Keep changes focused and follow Kotlin/Compose conventions used in the codebase
- If it's a core feature, include tests where practical

## License

GPL-3.0 - see `LICENSE.md`.
