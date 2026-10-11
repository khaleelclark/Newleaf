# Install and use Newleaf 0.1.4

Documentation reviewed October 11, 2026 against the unchanged 0.1.4 APK.

## On your Galaxy S25 Ultra

1. Download `Newleaf-0.1.4.apk` from the Project files to your phone.
2. Open the file through Samsung My Files or your download notification.
3. If Android asks, allow app installation for the app you used to open this file, then tap Install.
4. If you already have Newleaf installed, install this update over it; do not uninstall. Your local journal and source credentials stay on the device.
5. For a first installation, open Newleaf, choose your units, and tap Start your journal.

This is a direct APK installation, not a Play Store listing. If Samsung Auto Blocker prevents installation, use your phone's Auto Blocker controls for this trusted APK and restore your preferred protection afterward. Menu locations vary with One UI version. [Samsung's Auto Blocker explanation](https://www.samsung.com/us/support/answer/ANS10003636/)

No diary account is needed. The app starts empty; fictional sample exports are separate files and are never seeded into your journal.

## Daily use

- Today has Food, Drinks and Movement cards. Tap Log food, Log drink or Log activity to go straight to that entry.
- For food, type what you ate (a food name or a short meal description), then Save food. Meal category is optional. More details contains optional amount, calories, nutrition, notes/photos and date/time. Typed text alone does not estimate nutrition.
- For drinks, choose Water, Coffee, Tea, Juice, Milk or Other, enter an optional volume, then Save drink. Other allows any name. Changing the beverage clears the previous product's nutrition; coffee is kept separate from water.
- Tap + 16 oz water for one quick water entry, with Undo. Log drink accepts custom volumes.
- Log activity opens distance/time; Add steps opens the day's step total. Log weight under Movement opens body weight directly; enter lb/kg and Save weight.
- Scan is visible beside Settings. It opens the camera immediately for today. Point it at a packaged product barcode; a match opens automatically. Choose Breakfast/Lunch/Dinner/Snack/Other, review what you had, and tap Add to journal once. Cancel saves nothing.
- Nutrition filled by scanning is visible in the review and stays visible after saving. Known nutrients update as you change the amount; missing nutrients remain unknown.
- When package size is available, review starts at one whole package. For half a bottle enter 0.5 with Unit → package, or choose Unit → Percent of package and enter 50. A 500 mL bottle with a 250 mL label serving contains two servings; half the bottle is one serving. Check the reported package size against your label, especially multipacks (the whole outer package). Without package size, enter a measured volume/weight or a known label serving instead.
- If camera access is declined or no product is found, type the barcode, scan again or use Enter it yourself. Missing nutrition stays unknown.
- Tap a day in the week strip to browse or log on that date; Calendar reaches any other date. Scan always returns to today.
- More keeps the full logging chooser and Saved drafts. More ways to log includes multi-item Meal, Weight, Steps, Check-in and Daily note.
- Find a food opens optional USDA search or barcode lookup. Search needs your free USDA key; barcode scanning does not. The multi-item Meal editor also supports the existing assisted Food list lookup.
- Tap an entry to view/edit it. Settings holds Saved foods & recipes, units, goals/reminders, source setup and exports/backups. History keeps search/export under Search & export; Trends shows recorded totals.

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

## Troubleshooting

- **Update will not install:** confirm you opened the signed release APK. A debug or differently signed install cannot update in place; export a full backup before considering any uninstall. Insufficient storage or Android installation policy can also block installation.
- **Barcode finds a name but not nutrition:** that product may lack supported nutrition fields. Enter measured values from your label or keep them unknown; missing information is not zero.
- **Package/percentage option is absent:** the provider did not report a supported normalized package size. Use measured g/mL or a known label serving instead.
- **USDA search fails:** save your key in Settings first. Check setup shows configuration, not live authentication. An explicit search checks service access; manual logging continues offline.
- **Large export fails:** use smaller dated batches for attachments and retain the structured journal export. A failed export is not a usable backup.

## Validation boundary

See [validation](VALIDATION.md) for exact build/test results and known limitations. Emulator tests do not certify physical S25 Ultra camera behavior, One UI installation prompts, or the phone's current OS. The phone installation is the remaining device-specific check.
