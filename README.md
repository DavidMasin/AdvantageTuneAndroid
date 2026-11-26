# AdvantageTune

[![Build](https://github.com/Mechanical-Advantage/AdvantageTune/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Mechanical-Advantage/AdvantageTune/actions/workflows/build.yml)

AdvantageTune is a NetworkTables viewer and tuning application for iPhone and iPad developed by Littleton Robotics and intended for use by FIRST teams. It connects to [NetworkTables](https://docs.wpilib.org/en/stable/docs/software/networktables/index.html) servers (such as FIRST robots) with a native interface for viewing and modifying topics, including easy export of tuned values for use in robot code development. AdvantageTune supports reading and writing most data types, including primitives, arrays, WPILib structs, and Protobufs.

AdvantageTune is designed to provide a lightweight tuning experience to complement [AdvantageScope](https://docs.advantagescope.org). To keep the mobile experience streamlined and focused, more complex features (such as plotting, field visualization, and log viewing) are intentionally out of scope and are best served by AdvantageScope on the desktop.

## Development

AdvantageTune can be developed most easily using the iOS Simulator, as simulator builds do not require code signing.

1. Open `AdvantageTune.xcodeproj` in Xcode.
2. Select the `AdvantageTune` scheme and choose an iOS Simulator target (iPhone/iPad).
3. Build and run.

To test on a physical device using a free Apple Developer account:

1. In Xcode, select the `AdvantageTune` project > `AdvantageTune` target > `Signing & Capabilities`.
2. Change the team to your personal team and update the bundle identifier to a unique prefix (e.g., `com.<username>.advantagetune`).
3. On your iOS device, enable Developer Mode under `Settings` > `Privacy & Security` > `Developer Mode` (requires a restart).
4. Build and run the app on your device from Xcode. On the first run, trust your developer certificate under `Settings` > `General` > `VPN & Device Management`.

### Code Formatting

AdvantageTune uses Apple's `swift-format` tool (bundled with Xcode) for code formatting.

- Run `./format.sh` to automatically format all Swift files in-place.
- Run `./format.sh --check` to verify that all Swift files are properly formatted (this check runs automatically in CI).

## Android

The `android` directory contains a version of AdvantageTune for Android phones and tablets, built with Kotlin and Jetpack Compose. It mirrors the iOS app: every Swift file under `AdvantageTune` has a Kotlin counterpart with the same name under `android/app/src/main/java/org/littletonrobotics/advantagetune`, so a change to one app can be carried over to the other.

1. Open the `android` directory in Android Studio.
2. Select the `app` configuration and choose an emulator or a connected device running Android 8.0 or later.
3. Build and run.

To build from the command line, run `./gradlew assembleDebug` in the `android` directory (this requires JDK 17 or later and the Android SDK). The unit tests run with `./gradlew testDebugUnitTest`.

Hostnames ending in `.local` (such as the default `robot.local`) can be resolved on Android 12 and later. On older versions of Android, enter the IP address of the server instead.

### Building with EAS

The Android app can also be built in the cloud with [EAS Build](https://docs.expo.dev/build/introduction/). The root of the repository is the EAS project (`app.json`, `eas.json` and `package.json`), and EAS builds the `android` directory with Gradle. The app is not written in React Native, so it cannot run in Expo Go and does not use EAS Update.

1. Run `npx eas-cli@latest init` once to create the Expo project and save its ID in `app.json`.
2. Run `npx eas-cli@latest build --platform android --profile preview` to build an APK that can be installed from a link, or use `--profile production` to build an app bundle for Google Play.

EAS signs release builds by appending to `android/app/build.gradle`, which is why that file is written in Groovy while the other Gradle files are written in Kotlin.

### Shared Resources

The text and the app icon of the Android app are generated from the iOS project. After changing `AdvantageTune/Localizable.xcstrings` or `AdvantageTune/AppIcon.icon`, regenerate them:

- Run `python3 android/tools/import_strings.py` to regenerate the string resources.
- Run `python3 android/tools/generate_icons.py` to regenerate the launcher icon (this requires [Pillow](https://python-pillow.org)).

## Windows

The `desktop` directory contains a version of AdvantageTune for Windows, built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). It is the Android app in a window: the build compiles the Kotlin sources and reads the resources of the `android` directory, apart from the few files that use Android APIs. Each of those has a counterpart named `*.desktop.kt` under `desktop/src/main/kotlin`.

To build the installer, run `./gradlew packageMsi` in the `desktop` directory on Windows (this requires JDK 17 or later). The installer is written to `desktop/build/compose/binaries/main/msi` and contains its own Java runtime. `./gradlew run` starts the app without installing it, and `./gradlew test` runs the tests. Every push also builds the installer on GitHub Actions, where it can be downloaded from the artifacts of the "Build Desktop" workflow.

The app is laid out for a computer screen and can be used with a mouse or a touch screen:

- The window opens maximized, on the touch screen if there is one. After the first time, it opens where it was when it was closed.
- In a wide window the lists stay on the left, and the topic that is selected opens next to them. Pin a topic to keep it open, and the next topic opens beside it, so several values can be edited side by side. Pinned topics are opened again the next time the app starts. A narrow window works like the phone app.
- Lists scroll when they are dragged, rows on the Tuning tab are swiped to reveal their actions, and holding a finger on a row opens its menu (as does a right click).
- The button at the bottom of the tabs and F11 switch to full screen and back, and Escape goes back.
- Windows does not open its touch keyboard for the text fields of the app, so the app has its own: a number pad for values and letters for the server address, the search and text values. It is shown while a text field is being edited. The keyboard button at the bottom of the tabs turns it on and off, and it starts turned on if the computer has a touch screen.

After changing `AdvantageTune/AppIcon.icon`, run `python3 desktop/tools/generate_icons.py` to regenerate the icon (this requires [Pillow](https://python-pillow.org)).
