# Third-party notices

Newleaf uses these open-source components in its Android implementation. Their upstream licenses remain applicable to the components and do not establish a public license for the owner's original Newleaf source.

| Component | License | Source |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle, Compose, Room, SQLite | Apache License 2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin and kotlinx.coroutines | Apache License 2.0 | https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines |
| Gson | Apache License 2.0 | https://github.com/google/gson |
| Guava ListenableFuture | Apache License 2.0 | https://github.com/google/guava |
| ZXing Android Embedded | Apache License 2.0 | https://github.com/journeyapps/zxing-android-embedded |
| ZXing core | Apache License 2.0 | https://github.com/zxing/zxing |
| Gradle wrapper | Apache License 2.0 | https://github.com/gradle/gradle |

The Android package includes a copy of Apache License 2.0 and a component notice in its assets. The resolved Gradle dependencies are recorded during release validation. Upstream notices embedded in dependencies must remain preserved.

Nutrition content has separate terms. USDA attribution: U.S. Department of Agriculture, Agricultural Research Service, FoodData Central; its data are public domain/CC0. Open Food Facts data attribution and applicable database/content/image licensing are recorded with source information. Only free sources are enabled; Nutritionix is excluded from active capabilities. User meal photos and diary notes are not contributed publicly by this application.

## Development guidance and UX reference

Ponytail's compact agent guidance is vendored as a development reference only, at commit
09c434ab73ca43761c5d8b0c5d341926c2fba710. Copyright (c) 2026 DietrichGebert;
[MIT license](docs/references/ponytail-LICENSE.txt). The [snapshot](docs/references/ponytail-AGENTS.md)
and [Newleaf application notes](docs/UX_PRINCIPLES.md) are included in source packages;
Ponytail is not an Android dependency.

The owner supplied Elvis Canziba's *Hands-On UX Design for Developers* (Packt, 2018)
for design reference. Newleaf's original application notes cite the reviewed sections.
The copyrighted book is not redistributed in the repository, source package or APK.
