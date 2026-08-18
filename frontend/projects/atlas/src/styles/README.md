# Vendored Rhizome design tokens

`tokens.css` here is a **byte-identical, pinned copy** of the Rhizome theme tokens:

- Source: `frontend-atlas/theme-a/tokens.css` (Rhizome `plantpal-cae942ee` v1.0.0).
- Provenance / rules: `frontend-atlas/design-system/DESIGN_SYSTEM.md`.

## Do not hand-edit

This file is a pin, not a fork. If a token is wrong or missing, raise it as a
proposal back to the design-studio registry, re-pin `frontend-atlas/`, then
re-copy the file here — never patch it locally. Keeping it byte-identical is
what lets us diff this copy against the registry snapshot.

A CI guard asserting `projects/atlas/src/styles/tokens.css` still matches
`frontend-atlas/theme-a/tokens.css` is wired in Phase F.
