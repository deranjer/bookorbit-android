---
sidebar_position: 2
---

# Installing the app

BookOrbit for Android requires **Android 8.0 (API 26)** or newer.

## Play Store

The easiest way to install is from the Google Play Store (once published). Search for
"BookOrbit" or follow the link from the [GitHub repository](https://github.com/deranjer/bookorbit-android).

## Building from source

If you'd rather build it yourself:

### Prerequisites

| Tool               | Version                                | Notes                                                   |
| ------------------ | --------------------------------------- | -------------------------------------------------------- |
| **Android Studio** | Ladybug (2024.2) or newer               | Easiest path; bundles a compatible JDK + SDK manager.    |
| **JDK**            | 17+ (21 recommended)                    | Android Studio's bundled JBR works.                      |
| **Android SDK**    | Platform **36**, Build-Tools 35+        | Installed via Android Studio's SDK Manager.              |
| **A device**       | Emulator (API 26+) or a physical phone  | Min SDK is **26**; compile/target SDK is **36**.         |

### Steps

1. Clone the [repository](https://github.com/deranjer/bookorbit-android).
2. Open the repository's root folder in Android Studio and let Gradle sync finish.
3. Pick a device in the toolbar's device dropdown, then click **Run**.

Or from the command line:

```bash
./gradlew :app:installDebug      # build + install on the connected device/emulator
./gradlew :app:assembleDebug     # just build the APK -> app/build/outputs/apk/debug/
```

The debug build installs alongside the release build as `com.bookorbit.debug`, so you can have
both on one device.
