# Simple-inspired daily flow

The owner tested 0.1.2 and requested a better flow inspired by [Simple](https://simple.life). Its official [food guide](https://help.simple.life/en/articles/9887898-how-to-log-and-edit-food) begins with typing what was eaten, its [drink guide](https://help.simple.life/en/articles/9887906-how-to-log-and-edit-drinks) uses drink selection and a final save, and its [tracker overview](https://help.simple.life/en/articles/9887852-simple-life-overview-of-main-features) gives each tracker a direct action.

## Applied to Newleaf 0.1.3

- Food, Drinks and Movement cards answer where to start. Log food/Log drink/Log activity each go directly to a form; the entry-kind chooser is now secondary in More.
- Food asks What did you eat? with one text field and optional meal category. Save food works with just text. Amount/calories live in More details; supplied amounts/calories remain visible in a compact summary. There is no automatic nutrition inference from free text.
- Drinks offer familiar beverage choices plus Other for any custom name. Volume/unit are together, stacking at larger fonts. Beverage changes preserve event/volume but clear stale nutrition/source identity and update water classification.
- A week strip with full-date accessibility labels and Calendar supports browsing and logging on an explicit day. Scan stays visible in the header and returns to today.
- Scan still captures/looks up automatically, requires an explicit category, reviews amount/type and saves through one Add to journal. Unknown amounts/nutrients, cancellation, busy locks, retry and manual fallback retain their contracts.
- More retains full logging choices and saved drafts. Settings retains library, source setup, exports, backups, photos and credentials. History search/export moves into one secondary group.

## Verification

See [current release validation](../releases/VALIDATION.md). HomeFlowUiTest exercises direct food text/one durable save, drink selection/volume/water isolation, explicit-day food logging and activity/steps cancellation. Existing scanner and persistence/portability regressions remain required. Source review does not replace compiler or device checks; phone scanning and TalkBack are separate target-device checks.
