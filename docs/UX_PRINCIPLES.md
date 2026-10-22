# Newleaf UX and implementation principles

Adopted October 22, 2026 at the owner's request. This is project guidance for ongoing
implementation and review, with focused changes in Newleaf 0.1.5.

## References and scope

- [Ponytail](https://github.com/DietrichGebert/ponytail), pinned to commit
  `09c434ab73ca43761c5d8b0c5d341926c2fba710`. Its compact rules are preserved in
  [ponytail-AGENTS.md](references/ponytail-AGENTS.md), with the full
  [MIT license](references/ponytail-LICENSE.txt). [Repository guidance](../AGENTS.md)
  points future coding work to these rules. This is an instruction-only adoption;
  no agent hooks, telemetry, runtime library or new Android dependency is installed.
- Elvis Canziba, *Hands-On UX Design for Developers* (Packt, 2018), supplied by the owner
  as `/home/phoenix/khaleel/ux-design.pdf`. Reviewed sections: research/testing and
  accessibility (printed pp. 47–50; PDF pp. 59–62), cognitive load, correction and
  grouping (printed pp. 68–74; PDF pp. 80–86), alignment, hierarchy, contrast,
  repetition, proximity, balance and space (printed pp. 160–168; PDF pp. 172–180),
  and post-launch evaluation (printed pp. 305–306; PDF pp. 317–318).
  The guidance below is an original application of those ideas to Newleaf. The PDF
  is not copied into source archives, the repository or APK.

## Apply the principles to actual tasks

| Principle | Newleaf rule | Review evidence |
| --- | --- | --- |
| Reduce decisions | Food, Drinks and Movement open direct forms; optional editing stays secondary | Name-only food save and direct drink/activity/weight regressions |
| Clear hierarchy | One primary Save/Add; product name, portion and populated nutrition are visible | Review screenshot with keyboard; section headings exposed to accessibility services |
| Group related controls | Keep amount and unit together; distinguish package from label serving | Half-package conversion/save/reload checks |
| Consistency and repetition | Reuse Section, Field, SelectField, Disclosure, Action and Panel | Shared-component checks plus affected call-site review |
| Useful feedback | Explain a disabled required-category Add; announce lookup/save/validation errors without moving focus | Category hint disappears after selection; live-region error and retry regression |
| Accessible control states | Expose headings, selection, disabled state and expanded/collapsed groups | Semantics tests; actual TalkBack use is still a separate phone check |
| Readable contrast and space | Retain Newleaf light/dark palettes, spacing and minimum 48 dp actions; let text grow and content scroll | Light/dark and large-text/keyboard screenshots; no blanket accessibility certification |
| Correctable mistakes | Preserve drafts, cancellation, retry, Undo and explicit commit | Existing persistence, error/retry and cancel tests |
| Iterate from evidence | Begin with the owner's task and observed difficulty; compare the changed flow after implementation | Executed tests and visual evidence in release validation; phone observations recorded separately |

These are review criteria, not a claim that every screen has passed a full accessibility
audit or that user task times have been measured. Proposed performance/usability targets
remain targets until observed on the owner's S25 Ultra. Feedback comes from voluntary
owner testing; the book's discussion of analytics does not add analytics to Newleaf.

## Smallest complete implementation

Trace callers, tests, fixtures, persistence and exports before editing. Prefer existing
components and platform behavior, then installed dependencies. Add abstractions or new
packages only when needed for the requested behavior. Preserve error handling,
validation, accessibility, provenance and unknown values while removing complexity.
Test meaningful new logic and the affected user outcome. Finish related documentation
and release packaging, and report unverified behavior explicitly.

## Applied in 0.1.5

The shared Section exposes a heading. Disclosure and SelectField expose expansion state.
Scan review explains that a category enables Add to journal and removes the hint after
selection. Entry and barcode errors share a polite accessibility live region while retaining
their visible wording, validation and retry behavior. Food/drink/weight navigation and
nutrition calculations keep their established behavior.

See [experience](EXPERIENCE.md), [flow review](UX_FLOW_REVIEW.md),
[release validation](../releases/VALIDATION.md) and the
[delivery checklist](DELIVERY_AND_ACCEPTANCE.md) for acceptance and release boundaries.
