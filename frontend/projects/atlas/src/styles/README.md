# Atlas styles

Atlas serves the Herbarium design pack (`plantpal-e8e4b3e5` 1.2.0) and its accepted
theme-b. The two pinned, byte-identical copies live in `frontend/design-pack/`
(`tokens.css`, `atlas-theme.css`) and are loaded from `angular.json` before
`rhizome.css`. They are pins, never hand-edited: a missing token is raised as a gap
against the design pack. `tokens-pin.spec.ts` guards them. The old Rhizome round-8
`tokens.css` is gone.

`rhizome.css` is Atlas's own layout/screen CSS and uses only semantic Herbarium or
theme tokens (checked by `node design/check.mjs` from the hexagon root).
