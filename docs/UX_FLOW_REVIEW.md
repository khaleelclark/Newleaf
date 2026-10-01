# Newleaf logging and barcode flow revision

The owner tested 0.1.0 on their phone and reported too many scattered controls, too many simultaneous options, poor flow, and a barcode scan that did not visibly populate product information. This review drives the 0.1.1 revision; the specialist implementation and nutrition fix are present. The combined release passed 120 JVM tests and 24 mapped device tests; a final specialist screenshot review remains requested.

## Findings from the shipped source

- The logging picker presents eight entry types alongside saved drafts and draft management. Common food, drink and movement tasks need a stronger hierarchy.
- Food and meal editors display lookup modes beside manual fields, category controls, portions, nutrition, notes, date/time, time zone and photos. Optional details should appear when requested.
- Barcode capture already starts an Open Food Facts request, but its response becomes a result button. A separate selection step stands between a successful scan and product/portion review.
- The Open Food Facts mapper discarded recognized products when their nutrition input sets had no supported reference basis. The nutrition source fix retains product identity and leaves nutrition/quantity unknown instead of rejecting the product. Its regression cases passed in the combined 120-test JVM run.
- Portion review displays unit chips, an editable unit, evidence choices, attribution and nutrition controls together. The primary task is confirming the product and consumed amount.

## Intended acceptance

1. Today has one clear logging action and a small set of common entry choices. Less frequent tasks and saved drafts remain accessible through secondary controls.
2. A new entry initially shows the fields needed for its primary task. Notes, photos, timing and detailed nutrition remain available without competing with it.
3. A successful barcode capture automatically performs lookup and opens the matched product review, populated with the available name, source nutrition and portion information. It requires explicit confirmation and saving.
4. Lookup progress, unavailable networking, an unknown barcode and cancellation each have clear states. Retrying or using manual entry preserves the draft; stale results cannot replace a newer lookup.
5. Missing nutrition and unsupported conversions remain unknown. USDA configuration, credential protection, source provenance, exports, backups, recipes, reminders and photo safeguards continue working.
6. Focused offline UI/client regressions cover scan-result population and error recovery. The parent runs the combined release build, tests and emulator checks before delivering a signed update with the existing signing identity.

A fixture-driven scan-result flow does not certify physical camera decoding or Open Food Facts coverage for every product. The owner's physical barcode and phone network behavior remain reproducibility inputs if the revised flow still fails.
