# Rhizome — authoring contract

You are writing one page of **Rhizome**, PlantPal's atlas-class design system.
Follow this file exactly. It is machine-followable on purpose: every later
batch's prompt cites it, and assembly QA checks against it.

Read first, in this order:

1. `constitution.json` — the register. The C-invariants, the laws, the
   numbers, the page manifest. **Every C-number you cite resolves to this
   file.** Quote the `statement` verbatim; never paraphrase it into a second
   wording.
2. `PRINCIPLES.md` — the six principles and the vocabulary.
3. `layout-determinism.json`, `traversal-map.json`, `degradation-spec.json` —
   the three sibling specs. If your page is about placement, traversal or
   degradation, the relevant spec is your source; do not re-derive it.

---

## 1. Ground rules

1. **Use only classes that already exist** in `assets/system.css` (prefix
   `.rz-`) and `assets/docs.css` (prefix `.doc-`). Do not invent a class.
2. **Never a raw colour or size value.** Not in a `style` attribute, not in a
   `<style>` block, not in an SVG `fill`. Every value is
   `var(--rz-…)`. The one exception in the whole system is the shell
   transform `scale(0.42) translateX(-25%)`, which is hand-tuned and is used
   verbatim where `C20` requires it.
3. **Do not write a `<style>` block or a `<script>` block that duplicates
   asset behaviour.** A short page-local `<script>` that *calls* `RZ.*` to
   mount a specimen is expected and correct. Re-implementing travel, rank,
   placement or the density rule inside a page is not.
4. **`assets/` is versioned, not frozen** — see §7. You may not edit it
   casually; you may extend it under a recorded amendment when the existing
   vocabulary genuinely cannot carry what you need.
5. **Escape every code sample.** `&lt;` `&gt;` `&amp;` inside `<code>` and
   `<pre>`. A sample that renders as live markup is a build defect.
6. **Voice.** PlantPal's own register, applied to a design document: warm,
   plain-spoken, specific, second person where a reader is addressed. Say
   "the card", "the vein", "the world", "your garden" — never "the widget",
   "the container", "the entity". Be honest about limits, in the app's own
   spirit: *if unsure, say so.* Never cute, never a slogan, never marketing.
7. **Never show a user a slot, ring, band, row, column or lattice
   coordinate.** Those are the system's private vocabulary. A collapsed
   summary speaks the domain: "Plants · 12 plants", "Problems · 3 active".
8. **Never ship a spinner, the word "loading", a modal dialog, or a global
   blocking overlay** — not even as an anti-pattern illustration in a
   Do/Don't. An illustration gets copied. Describe the anti-pattern in prose
   and show only the correct form.
9. **State a law once.** If your page needs a law that another page owns
   (`constitution.json → laws[].carried_by`), cite it and link to it. Do not
   restate it. The density rule is owned by `components/aggregate.html` and
   `foundations/spacing.html`; the travel timing by `patterns/traversal.html`
   and `foundations/motion.html`; the chrome register by
   `components/chrome.html`.
10. **Link only to slugs in the manifest.** `constitution.json → pages`,
    `assets/docs.js → NAV` and every page's own links must agree exactly.

---

## 2. Every law you write gets a NUMBER and a VERIFICATION

Not "use size to convey hierarchy" but *"the hierarchy survives with the text
blurred"*. Not "coordinates are persisted" but *"load twice and diff the
computed left and top"*. If a reader cannot check it, it is a preference, not
a law, and the next round will lose it.

Use the law block:

```html
<div class="doc-law">
  <p class="doc-law__id">Law 3 · <span class="doc-cite">C7, C8</span></p>
  <p class="doc-law__statement">A node's position is a formula, never a flow.</p>
  <p class="doc-check">Load the page twice and diff the computed
    <code>left</code>/<code>top</code> of every <code>.rz-n</code>. They are equal.</p>
</div>
```

---

## 3. Where the law needs a runtime, use the runtime

`assets/system.js` carries the primitives, and a page must not assert a
behaviour the assets cannot perform:

| You need | Call |
|---|---|
| the graph | `RZ.setEdges([...])`, `RZ.adj`, `RZ.degree(id)` |
| placement from cells | `RZ.readNodes(plane)`, `RZ.place(rec)`, `RZ.freeCell(col)` |
| rank | `RZ.ranks(focus)`, `RZ.rankName(d)`, `RZ.present(focus, expanding)` |
| a route | `RZ.route(a, b)` |
| a hop | `RZ.go(id)` — one code path, one timing |
| travel along the edges | `RZ.travel(chain, then)` |
| fit the focused card | `RZ.fitFocus()` |
| camera (never focus) | `RZ.zoomBy(f)`, `RZ.recentre()`, `RZ.fitAll()` |
| the world view | `RZ.extent()`, `RZ.drawMap()` |
| arrange mode | `RZ.setArrange(true/false)` |
| keyboard direction | `RZ.neighbourInDirection("ArrowRight")` |
| a degradation state | `RZ.setState("net", "offline")` |
| palette / theme | `RZ.setPalette(name)`, `RZ.setTheme("light")` |
| the settings exit | `RZ.setMode("overview" \| "app")` |
| announce | `RZ.say(text)` |
| parameter agreement | `RZ.verifyParameters()` → `[]` |

Mount a live specimen with `RZ.mount({ world, plane, edges, actions, focus,
path })`. On a page with no world, `RZ.mount({})` still wires palettes, state
probes and the field.

---

## 4. The page skeleton — copy this exactly

Every page except `index.html` lives one directory down
(`components/`, `patterns/`, `foundations/`, `guidelines/`) and uses these
relative paths verbatim:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" data-palette="potting-bench">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Node · Rhizome</title>
  <link rel="stylesheet" href="../assets/tokens.css">
  <link rel="stylesheet" href="../assets/system.css">
  <link rel="stylesheet" href="../assets/docs.css">
</head>
<body class="doc-body" data-doc-depth="1">
  <div class="doc-shell">
    <aside class="doc-sidebar" data-doc-nav></aside>
    <main class="doc-article">

      <p class="doc-eyebrow">Components</p>
      <h1 class="doc-title">Node</h1>
      <p class="doc-lede">One sentence saying what this page settles.</p>

      <!-- sections go here -->

      <footer class="doc-footer">
        <span data-doc-version></span>
        <span>Atlas-class · every C-number cites
          <a href="../constitution.json">constitution.json</a></span>
      </footer>
    </main>
  </div>
  <script src="../assets/system.js"></script>
  <script src="../assets/docs.js"></script>
</body>
</html>
```

`index.html` at the system root is identical except `data-doc-depth="0"` and
the asset paths lose the `../`: `assets/tokens.css`, `assets/system.css`,
`assets/docs.css`, `assets/system.js`, `assets/docs.js`.

The sidebar is **generated** by `docs.js` from `NAV`. Never hand-write it.

---

## 5. Canonical content blocks

### 5.1 Live-demo specimen

A real component, running, from real classes — never a screenshot, never a
mock-up drawn in CSS. Caption states what it demonstrates and which invariant.

```html
<figure class="doc-specimen">
  <div class="doc-specimen__stage">
    <article class="rz-n" id="sp-fig" data-cell="2,3"
             data-kind="Species" data-kind-key="species"
             data-name="Fiddle-leaf Fig"
             data-recap="12 plants · 1 overdue"
             data-rank="near" data-show="recap">
      <span class="rz-n__grad"></span>
      <p class="rz-n__kind">Species</p>
      <h3 class="rz-n__name">Fiddle-leaf Fig</h3>
      <p class="rz-n__recap">Ficus lyrata · 12 plants
        <span class="rz-n__recap-second">1 needs water today</span></p>
    </article>
  </div>
  <figcaption class="doc-specimen__caption">
    <span>A node at rank “near”</span><span>C3 · C12</span>
  </figcaption>
</figure>
```

### 5.2 Token table

Always three columns at least: token, value or effect, and what it means.

```html
<div class="doc-table-wrap">
  <table class="doc-table">
    <caption class="doc-eyebrow">Kind channel</caption>
    <thead><tr><th>Token</th><th></th><th>Means</th></tr></thead>
    <tbody>
      <tr>
        <td><code>--rz-kind-plant</code></td>
        <td><span class="doc-swatch" style="background: var(--rz-kind-plant)"></span></td>
        <td>A plant the user actually owns, with a nickname.</td>
      </tr>
    </tbody>
  </table>
</div>
```

(The `style` attribute is permitted **only** for a swatch, and only ever with
a `var(--rz-…)` value.)

### 5.3 Do / Don't pair

Both cases in prose or correct markup. The "don't" side never renders a
forbidden artefact.

```html
<div class="doc-pair">
  <div class="doc-case" data-case="do">
    <p class="doc-case__label">Do</p>
    <p>Draw the failure inside the node that failed, with retry in place.</p>
  </div>
  <div class="doc-case" data-case="dont">
    <p class="doc-case__label">Don’t</p>
    <p>Open a dialog over a blurred world. It breaks the geography and the
      you-are-here at once (C25).</p>
  </div>
</div>
```

### 5.4 Standalone code block

```html
<pre class="doc-code"><code>RZ.mount({
  edges: [["n-garden", "n-office"], ["n-garden", "n-studio"]],
  focus: "n-garden"
});</code></pre>
```

---

## 6. Page shape

In order: eyebrow, title, lede, then

1. **What this is** — one paragraph, the domain first.
2. **The laws** — numbered `doc-law` blocks, each with its `doc-check` and its
   `doc-cite`. Between two and six.
3. **Specimens** — at least one live one.
4. **Tokens / attributes** — the table of what a theme fills.
5. **Do / Don't** — at least one pair.
6. **Where else this appears** — links to the pages that own the laws you
   cited, using manifest slugs only.

---

## 7. Extending the system

`assets/` is versioned, not frozen. Herbarium froze it before a single theme
round had exercised it, and every gap found downstream became un-fixable from
inside by contract. Instead:

- If a class or token you need **already exists**, use it.
- If it **does not**, and the existing vocabulary genuinely cannot carry the
  meaning, you may add it under a **named amendment**:
  1. Add the token family or primitive to `assets/tokens.css` (or the class to
     `assets/system.css`), in the correct layer, with the system's prefix.
  2. Record it in `constitution.json → amendments.register` using
     `amendments.entry_shape`: id, round, what it adds, why the existing
     vocabulary could not carry it, which files changed.
  3. Say so on your page, in one sentence.
- You may **not** remove or re-key an existing semantic token, and you may
  **not** change a declared parameter (the 4, the 2, the 300ms, the pitch, the
  origin, the zoom clamp, the slow threshold) without an owner ruling.

---

## 8. Finishing checklist

Before you hand the page back, verify each of these:

- [ ] Skeleton copied exactly; asset paths correct for this page's depth.
- [ ] `data-doc-depth` matches the directory.
- [ ] Sidebar generated, not hand-written; the page marks itself current.
- [ ] Every link points at a slug in `constitution.json → pages`.
- [ ] Every class used exists in `system.css` or `docs.css`.
- [ ] No raw colour or size anywhere (swatch `style` with `var(--rz-…)` aside).
- [ ] Every C-number cited exists in `constitution.json` and says what you
      claim it says — check the statement, do not trust memory.
- [ ] Every law on the page has a number and a verification a reader can run.
- [ ] No law restated that another page owns.
- [ ] At least one live specimen; it runs from `RZ.*`, not from page-local
      re-implementation.
- [ ] At least one Do/Don't pair.
- [ ] All code samples escaped.
- [ ] No spinner, no "loading", no modal, no global overlay, no lattice
      coordinate spoken to a user.
- [ ] `RZ.verifyParameters()` returns `[]` with the page loaded.
- [ ] Any amendment you made is recorded in the constitution's register.
