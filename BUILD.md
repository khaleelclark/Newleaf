# Build Newleaf

Newleaf is a native Android application. The nutrition gateway is a separate service; the phone's journal does not depend on it for offline/manual logging.

## Android requirements

- JDK 17.
- Android SDK platform 36; release.sh defaults to build-tools 35.0.0. Install that tool version or set NEWLEAF_BUILD_TOOLS to an installed compatible build-tools directory.
- Network access for the initial Gradle/dependency download.

The repository pins Gradle 8.13, Android Gradle plugin 8.12.0, Kotlin/Compose compiler 2.2.10 and KSP 2.2.10-2.0.2. Room is 2.8.5 and Compose BOM is 2025.08.00. app/build.gradle.kts declares release 0.1.4/code6, minSdk29, compile/targetSdk36. The checked-in wrapper downloads Gradle. Use Android Studio's SDK settings, or create an ignored `local.properties` containing `sdk.dir=/absolute/path/to/your/Android/Sdk`.

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

For the repeatable signed handoff, run `scripts/release.sh`. It builds the minified release and mapped instrumentation APK, runs release unit tests and lint, then signs and verifies the APK. It uses a persistent private key under `~/.local/share/newleaf/signing/`; back up that directory securely outside version control. The key is deliberately absent from source packages. On a fresh host the script creates a new signing identity if the key is absent; recover the established private signing directory before running it for an update. A new key cannot update the existing installed release. `scripts/package.py` checks the named handoff files and produces the source ZIP and checksums after the validation record is complete.

Mapped release instrumentation uses `-Pnewleaf.testBuildType=release`. Sign the resulting `app/build/outputs/apk/androidTest/release/app-release-androidTest.apk` with the same release key, install it and the release APK on the chosen test device, then run `adb -s DEVICE_SERIAL shell am instrument -w -r com.khaleel.newleaf.test/androidx.test.runner.AndroidJUnitRunner`. Kotlin, coroutine, Compose and tracing runtime ABI keep rules preserve libraries shared with this test runner. Do not install or test against unrelated connected devices.

The installable signed APK, checksum, public signing details, mapping file and validation evidence belong in `releases/`. Release documentation records exactly what was built and tested, including unconfigured nutrition providers and device testing limits.

## Nutrition service

Open Food Facts product lookup is public and requires no personal account/key. For free USDA search, details and assisted descriptions, enter a personal data.gov API key in the installed app's Settings. It is encrypted with Android Keystore protection and excluded from journal exports/backups. Never embed a key in an APK, committed file, source archive or diagnostic log.

An authenticated HTTPS USDA gateway is an optional alternative for server-managed credentials. Read [gateway setup](gateway/README.md) for local tests and a secrets-free environment example. Shared server keys stay in its private environment; the phone stores only its device token. No deployment or subscription is required for personal-key mode. Nutritionix is disabled, including when legacy commercial credentials exist.

## Source packages

`data/` owns journal records, persistence, calculations, and portable exports. `nutrition/` owns provider normalization and networking. `ui/` owns the Newleaf interface. Tests and release documents are part of the handoff. Generated build files, credentials, and local SDK paths are excluded from source archives.
