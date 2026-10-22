# Newleaf development guidance

Apply the owner's Ponytail and UX references to work in this repository.
Read [Ponytail's compact rules](docs/references/ponytail-AGENTS.md) before coding and
[Newleaf UX principles](docs/UX_PRINCIPLES.md) before changing a user flow.
The upstream snapshot is reference guidance; current user requirements take precedence.

- Trace affected callers, persistence, exports, tests and documentation before editing.
- Make the smallest complete change; reuse the shared UI in Design.kt, existing models,
  standard platform behavior and installed dependencies before adding anything.
- Preserve validation, save/retry/cancel safety, unknown nutrition, provenance, credential
  isolation and accessibility when simplifying code.
- Keep Food, Drinks and Movement direct. Show populated nutrition; keep optional editing
  secondary. One explicit Save/Add commits the entry, with recoverable failure feedback.
- Verify meaningful behavior, keyboard/large-text layouts and accessibility semantics for
  changed flows. Distinguish emulator/fixture evidence from actual S25 Ultra use.
- Update affected docs and include the current signed APK in releases and Project files
  for an app release. Follow BUILD.md and docs/DELIVERY_AND_ACCEPTANCE.md.
- Report what changed, executed checks, and any material unverified behavior honestly.
