# Newleaf everyday logging revision

The owner tested 0.1.1 and still found it confusing. The October 2 direction is simple, human-readable daily logging and a main-screen barcode action that opens one reviewed journal Add with a chosen meal category. More disclosure buttons alone do not address that complaint.

## Implemented editor changes

- Food and drink ask “What did you have?” and show name, optional amount/volume, unit dropdown, optional calories, optional meal dropdown and one Save. Name-only entries remain useful.
- No lookup method chips appear initially. Find a food opens Name/Barcode choices, with Food list additionally available in multiple-item meals. Back to entry abandons lookup while retaining the draft and barcode.
- Activity uses one type dropdown plus distance/time. Other supports a custom name; intensity is secondary.
- Product review shows one prominent product name, label-reference amount, unit dropdown and calorie preview. Food/Drink is easy to find. Advanced nutrition, amount evidence and source attribution share one Details group.
- Entry detail shows name/time/portion/calories before one primary Edit; full nutrition/source/technical data are behind Details. Favorites, recipes, notes, photos, custom categories, dates/time zones, exports and restore remain accessible in secondary locations.
- Saved foods & recipes opens Library from Settings; nutrition setup starts collapsed; masked USDA key, Keystore protection, optional gateway and privacy/export safeguards retain their contracts.

Today uses Log and Scan at the bottom, one compact Calories/Water/Steps summary and one + 16 oz water shortcut. Custom amounts remain in Log → Drink. Less frequent entries including Daily note remain under More ways to log; date navigation stays in History.

## Shared quick-scan contract

`PortionDialog(candidate, original, allowAdditions=true, quickLog=false, busy=false, saveError=null, onDismiss, onConfirm)` preserves ordinary-editor calls. With quickLog=true it shows Log scanned item and Add to journal; a required Category dropdown starts blank with “Choose a category” and offers Breakfast/Lunch/Dinner/Snack/Other. Add is disabled until the user selects one; ordinary manual-entry categories remain optional. The returned Entry includes the chosen category and Food/Drink kind. No category is inferred and no journal save happens within the dialog. Parent QuickScan owns capture, automatic lookup and persistence after Add. Busy plus an immediate local submission guard blocks duplicate clicks and dismissal; saveError remains visible and unlocks retry when busy clears.

Missing product amounts/nutrients remain blank/null. A populated amount is a label reference requiring review. Default nested-editor mode still confirms only the draft before its separate Save. Existing fake capture and lookup hooks remain offline.

## Validation status

Source-level review and required-file verification are complete. Updated existing Android tests cover new initial/editor/detail hierarchy, lookup/manual continuity and quick-scan category/type/one-add/unknown/busy/error retry. The existing JVM editor-state regression now verifies category preservation through lookup population. No compiler, Gradle, emulator, real provider, private credential or Git operations were performed by the specialist. Parent integration passed 120 JVM tests and 31 signed-release Android emulator tests. Actual main camera launch/denial/Back and keyboard/large-text/dark checks passed at the shown configuration. Final specialist screenshot review and release packaging remain pending. Physical Samsung scan/network behavior and TalkBack remain phone checks; see [validation](../releases/VALIDATION.md).
