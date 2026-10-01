# Build Newleaf

Newleaf is a native Android application. The nutrition gateway is a separate service; the phone's journal does not depend on it for offline/manual logging.

## Android requirements

- JDK 17.
- Android SDK platform 36 and Android build tools 35 or newer supported by AGP.
- Network access for the initial Gradle/dependency download.

The repository pins Gradle 8.13, Android Gradle plugin 8.12.0, Kotlin 2.2.10, and the Compose compiler plugin 2.2.10. The checked-in wrapper downloads Gradle. Use Android Studio's SDK settings, or create an ignored `local.properties` containing `sdk.dir=/absolute/path/to/your/Android/Sdk`.

From this directory:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A connected Android device/emulator can run instrumentation tests with `./gradlew :app:connectedDebugAndroidTest`. Installing the debug build and the signed release over each other requires matching signing identities; use the release for regular personal use.

## Release build and signing

```sh
./gradlew :app:assembleRelease
```

This produces an unsigned APK. Sign it using Android SDK `apksigner` with a private signing keystore, then verify the signature. Do not check a keystore or password into this repository. Future releases must use the same key and increment `versionCode` to update in place without clearing the journal.

The initial handoff's installable signed APK, checksum, signing details, and validation evidence belong in `releases/`. Release documentation records exactly what was built and tested, including unconfigured nutrition providers and device testing limits.

## Nutrition service

Read [gateway setup](gateway/README.md) for the implemented API, local tests, environment variables, and deployment requirements. Never put USDA or Nutritionix keys into the Android app or a committed configuration file. The app needs only the gateway address and its device token, entered in Settings.

Keyed online features are not operational until an authorized gateway and provider accounts are configured. Open Food Facts barcode lookup uses its public product endpoint independently. No paid subscription or external deployment is part of a local build.

## Source packages

`data/` owns journal records, persistence, calculations, and portable exports. `nutrition/` owns provider normalization and networking. `ui/` owns the Newleaf interface. Tests and release documents are part of the handoff. Generated build files, credentials, and local SDK paths are excluded from source archives.
