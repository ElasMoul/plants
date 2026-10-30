# Herbarium pin for Atlas (design pack plantpal-e8e4b3e5 1.1.0)

- `tokens.css` is a byte-identical copy of the pack's `design/tokens.css`.
- `atlas-theme.css` is a byte-identical copy of the accepted theme-b,
  `design/reference/themes/atlas/tokens.css`.

Both are pins, never edited here. They live outside `projects/atlas` so the design
check (`--root app/frontend/projects/atlas --theme design/reference/themes/atlas/tokens.css`)
judges the theme by the hexagon's file and not by a second copy. Atlas loads them from
`angular.json` ahead of `rhizome.css`. `projects/atlas/src/styles/tokens-pin.spec.ts` guards them.
