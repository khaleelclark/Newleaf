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

For the repeatable signed handoff, run `scripts/release.sh`. It builds the minified release and mapped instrumentation APK, runs release unit tests and lint, then signs and verifies the APK. It uses a persistent private key under `~/.local/share/newleaf/signing/`; back up that directory securely outside version control. The key is deliberately absent from source packages. `scripts/package.py` checks the named handoff files and produces the source ZIP and checksums after the validation record is complete.

Mapped release instrumentation uses `-Pnewleaf.testBuildType=release`. Sign the resulting `app/build/outputs/apk/androidTest/release/app-release-androidTest.apk` with the same release key, install it and the release APK on the chosen test device, then run `adb -s DEVICE_SERIAL shell am instrument -w -r com.khaleel.newleaf.test/androidx.test.runner.AndroidJUnitRunner`. Kotlin, coroutine, Compose and tracing runtime ABI keep rules preserve libraries shared with this test runner. Do not install or test against unrelated connected devices.

The installable signed APK, checksum, public signing details, mapping file and validation evidence belong in `releases/`. Release documentation records exactly what was built and tested, including unconfigured nutrition providers and device testing limits.

## Nutrition service

Read [gateway setup](gateway/README.md) for the implemented API, local tests, environment variables, and deployment requirements. Never put USDA or Nutritionix keys into the Android app or a committed configuration file. The app needs only the gateway address and its device token, entered in Settings.

Keyed online features are not operational until an authorized gateway and provider accounts are configured. Open Food Facts barcode lookup uses its public product endpoint independently. No paid subscription or external deployment is part of a local build.

## Source packages

`data/` owns journal records, persistence, calculations, and portable exports. `nutrition/` owns provider normalization and networking. `ui/` owns the Newleaf interface. Tests and release documents are part of the handoff. Generated build files, credentials, and local SDK paths are excluded from source archives.
