# Install and use Newleaf 0.1.1

## On your Galaxy S25 Ultra

1. Download `Newleaf-0.1.1.apk` from the Project files to your phone.
2. Open the file through Samsung My Files or your download notification.
3. If Android asks, allow app installation for the app you used to open this file, then tap Install.
4. If you already have Newleaf installed, install this update over it; do not uninstall. Your local journal and source credentials stay on the device.
5. For a first installation, open Newleaf, choose your units, and tap Start your journal.

This is a direct APK installation, not a Play Store listing. If Samsung Auto Blocker prevents installation, use your phone's Auto Blocker controls for this trusted APK and restore your preferred protection afterward. Menu locations vary with One UI version. [Samsung's Auto Blocker explanation](https://www.samsung.com/us/support/answer/ANS10003636/)

No diary account is needed. The app starts empty; fictional sample exports are separate files and are never seeded into your journal.

## Daily use

- Today shows known logged calories, water, steps, and your timeline.
- Tap + Log your day for Food, Drink or Activity. More ways to log contains Meal, Weight, Steps, Check-in and Daily note.
- Enter a name to log a food quickly. Expand Portion & calories when needed; More details holds favorites, notes/photos and date/time.
- Meals support manual items, online food search, barcode lookup, and USDA-assisted meal descriptions when configured.
- For packaged food, choose Barcode → Scan barcode. A successful match opens the populated product review automatically. Confirm the item, then tap Save food or Save drink. Typed barcode lookup follows the same path. If camera access is declined, enter the code directly or use Search/Manual.
- Review suggested foods and portions before saving. Blank calories/nutrients stay unknown. If a product has no supported nutrition, its identity still appears and missing values stay unknown. Failed/unknown lookups retain the code for retry or manual entry.
- Save favorites, repeat a previous meal, or use Library for saved meals and recipes.
- History lets you log/edit earlier dates. Trends show the recorded data and its gaps.
- Units, appearance, an optional water goal, source settings, and portability are in Settings.

The app asks for camera permission only when needed. Photo selection uses Android's picker. Notification permission is needed only for optional check-in reminders. Manual logging needs no location, step-sensor, or health-account access.

## Nutrition setup

Open Food Facts barcode lookup uses the public product service directly. Real-world product coverage and nutrition completeness vary; unsupported/missing matches can be entered manually.

USDA general food search, details and assisted meal descriptions are free with a data.gov API key. In Settings → Nutrition sources, enter your personal USDA key, then scroll down and tap Save preferences. Check setup shows configuration only; a food Search or Describe request checks the key with USDA. Get a key through the [official USDA API guide](https://fdc.nal.usda.gov/api-guide/). No hosted server or subscription is needed. The key is protected locally, excluded from exports/backups, and never embedded in this APK. Restoring a journal retains this phone's existing credentials.

Describe meal accepts up to eight comma/newline-separated foods or drinks, with optional quantities and units. It proposes USDA matches; review each food, preparation and portion before saving. It does not infer unrestricted meal language or promise exact matches. Missing conversions/nutrients stay unknown. Nutritionix and paid fallback services are disabled.

Advanced source settings optionally accept an authenticated HTTPS USDA gateway URL and device token for server-managed credentials. This is unnecessary for personal-key mode; see [gateway setup](../gateway/README.md). No subscription, paid call, external account registration or hosted deployment was created for this release.

## Export and recovery

Settings → Export offers JSON, a CSV ZIP bundle, and Markdown for all dates, one day, or a date range. Include notes, library, recipes, and photos as desired. Linked context can extend outside the selected range and is labeled separately. Save through Android's file picker or open its share sheet. The app sends nothing to an AI automatically.

Settings → Create full backup makes a ZIP containing the active journal, library, recipe snapshots, settings, and original local photos. USDA keys, device tokens and transient drafts are excluded. Save it outside the app so it survives uninstall or phone loss. A readable export/backup is accessible to anyone who can access its saved destination.

Settings → Restore validates a file and previews records/photos. Merge preserves existing IDs and asks how to resolve conflicts; Replace first creates an on-device recovery backup. Canceling or a validation failure leaves the current journal intact.

This version supports attachments up to 64 MiB each and archives up to 256 MiB, with bounded extraction checks. For a large photo collection, use dated export batches and keep those files alongside your full structured journal export; merge restoration can combine batches. Review the generated file before deleting any original copy.

## Updates and uninstall

Future APKs must have the same package and signing identity with an increased version code. Install them over the existing release; do not uninstall merely to update. The signing key is kept privately on the development host in `/home/phoenix/.local/share/newleaf/signing/`, outside the source archive and Project files. It needs a secure operator backup for future releases; it is never attached in chat.

The debug/test build uses another signing identity. It is not the phone handoff. Android will reject an in-place update from that debug build to the release; export first if you installed a development build with data.

Uninstalling or clearing Android app data deletes the local journal and photos. Export a full backup first. Automatic platform cloud backup is disabled; this version has explicit backup/restore instead. Exported files in other destinations are managed separately by you.

## Validation boundary

See [validation](VALIDATION.md) for exact build/test results and known limitations. Emulator tests do not certify physical S25 Ultra camera behavior, One UI installation prompts, or the phone's current OS. The phone installation is the remaining device-specific check.
