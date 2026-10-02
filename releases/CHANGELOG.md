# Newleaf 0.1.1

UX and barcode revision requested after the owner tested 0.1.0. Release verification is recorded in [VALIDATION.md](VALIDATION.md).

- Log opens with Food, Drink and Activity; less frequent entries are under More ways to log.
- Food supports quick name-only logging. Portion/calories expands directly; a single More details group holds favorites, notes/photos and timing. Drink volume stays up front, while additional choices expand when needed.
- Camera access is requested before opening the scanner. Denial keeps manual entry available with a plain explanation; the scanner stays in portrait orientation.
- A barcode capture automatically looks up the product and opens populated product/portion review. Typed-code lookup follows the same path. Confirm updates the draft; Save creates the journal record.
- Lookup progress prevents duplicate requests; failed and unknown codes remain available for retry or manual entry and draft recovery.
- Recognized Open Food Facts products remain available when their nutrition format is missing or unsupported. No unsupported conversion or nutrient value is invented.
- Product review keeps confirmation visible and tucks source/portion details away. Settings and entry actions have a clearer hierarchy; portability returns to its originating screen.
- Same application/signing identity with version code 3 supports an in-place update from 0.1.0, retaining local journal data and source credentials.

# Newleaf 0.1.0

First Android implementation, October 1, 2026. Final verification status is recorded in [VALIDATION.md](VALIDATION.md).

- Custom ivory/forest and charcoal themes, journal timeline, History, and coverage-aware Trends.
- Meals and individual foods with optional quantities, calories, macronutrients, source evidence, and photos.
- Drinks in US fluid ounces or milliliters, separate water totals, quick water with Undo, and drink additions counted once.
- Manual daily step totals, activities with distance/duration, multiple body-weight observations, notes, and meal-linked feeling check-ins.
- Durable drafts, edits, favorites, saved meals, repeat logging, recipe versions and immutable consumed snapshots.
- Free USDA food search/details and assisted descriptions, plus Open Food Facts barcode lookup. Review matches and portions before saving.
- Local JSON, CSV ZIP, and Markdown exports; full attachment backups and validated merge/replace restoration.
- Optional local check-in reminders, system photo/file pickers, platform-protected personal USDA key and device access token, explicit platform backup exclusions.
- Optional authenticated USDA gateway with shared deployment credentials kept on the server, persistent request ceilings, and safe provider errors.

## Remaining external and device checks

USDA needs a free data.gov key entered in Settings; no server is required. Open Food Facts lookup needs no personal account/key. Nutritionix is disabled after the owner's free-only clarification. No paid subscription, account registration, deployment or commercial call was made.

The owner's USDA key passed two official free read-only calls for search/details. One Open Food Facts production product response returned HTTP 200 and was replayed through nutrition/portion mapping. These host checks do not certify physical Android networking or universal food coverage. The final free-only release passed 108 JVM tests, 17 mapped release device tests and 20 optional gateway tests; see VALIDATION.md for evidence and boundaries.

The physical Galaxy S25 Ultra has not been connected. Samsung installation prompts, physical camera/scanner, TalkBack, and target-phone performance remain device acceptance work. Emulator results and every remaining limitation are listed in VALIDATION.md. Check-in reminders use Android's inexact alarms; timing can be delayed by power-saving, and reopening the app reschedules eligible reminders after a reboot.

There is no automatic step/health-account sync, location collection, AI upload, or cloud diary sync. Those were outside the manual journal scope.
