# Round 9 — round 8, repaired

**Mission** cae942ee-927d-40f5-ad8f-1d72122d1a1f (PlantPal, atlas-class) ·
**System** Rhizome (plantpal-cae942ee v1.0.0)

## The direction this round answers

Round 8 was rejected with **"good, now lets fix some problems"** and a list of
six. That is not a rejection of the design — it is a snag list against one
that has been accepted. So round 9 is round 8 **carried forward**: the same two
propositions, the same key, the same board, the same 31 veins, the same
density arithmetic, the same settings choreography, the same voice. Nothing
was re-keyed, re-pitched or re-argued. Two propositions again, and they still
differ only where round 8's instruction was open — whether the Glasshouse
Table is a *coupled reading* (**theme-a**) or an *independent layout*
(**theme-b**). That argument was not reopened.

What changed is the six things you asked for, and one defect found while
building them.

| # | Your words | What was done |
|---|---|---|
| 1 | `.ch-btn--square { width: 1.5rem; height: 1.5rem; … }` | Taken verbatim. The round-8 declaration is commented out rather than left to fight it, so there is exactly one winner. |
| 2 | "when navigating to it, it should be in the center … recalucate their position so they dont collide" | **The clearance pass.** New, and the substantial change of this round — see below. |
| 3 | "make the node link clickable, and redirect to the connecting node" | Every vein now carries an invisible 20px hit stroke. Click it and you travel along it, through the same `go()` as every other hop. |
| 4 | `.n[data-rank="fringe"] { width: auto; }` | Taken verbatim — and it is *why* 2 had to become a real measured pass rather than a bigger pitch. |
| 5 | "make the buttons (reduced motion / slow >10 s) work like before" | They were dead. The cause was a real bug, below. |
| 6 | "make them move like the particle js, minimal mvmnt almost not visible" | Every non-focused card drifts 2–3px on its own phase, at 30fps. The focus never drifts. |
| 7 | "the nodes with not a direct conection, its oky if they move out of screen" | Taken as permission, and used. Direct neighbours are kept around you; the rest are allowed to leave the frame — and are still on the world view, still reachable. |

## 2 — the clearance pass, and why it needed a new layer

Position now has three layers instead of two:

| | | persisted? |
|---|---|---|
| **home** | `ORIGIN + cell × PITCH` — the lattice, a formula | the cell is |
| **anchor** | `home + (dx, dy)` — what you dragged in arrange mode | the offset is |
| **placed** | `clearance(anchor, focus, measured boxes)` | **no** — recomputed, never stored |

The focused card is 436px wide and up to 780px tall; at a 300 × 180 pitch it
covers parts of eight cells. Round 8 drew the neighbours on top of it. Widening
the pitch would have pushed the *whole world* apart at every rank to fix a
problem that only exists next to the focus — so the fix is local and travels
with you:

- the **focus** is pinned and never yields; the camera comes to it, and it
  lands in the centre of the space actually left between the two rails, not
  the centre of the window;
- its **direct neighbours** are placed on an ellipse whose radius is computed
  from the two cards' own measured half-sizes plus 34px of air, so a
  neighbour cannot overlap the focus *by construction*. Each keeps the angle
  its lattice home had from the focus — it arrives on the side you already
  expected — and the angles then spread until no two crowd each other;
- **everyone else** stays at its anchor, and a separation pass pushes apart
  anything still touching.

Cards travel to their new places in the same 300ms and on the same clock as
the camera. Nothing pops.

**The cost, stated plainly.** C7 now reads *same cells, same offsets, same
focus, same window → same pixels*, rather than *same cells → same pixels*.
Cell and offset — the two things that are actually remembered about a node —
are untouched. `atlas-layout-determinism.json` records this as amendment **A3**
with the one insertion edge case written out, rather than leaving you to find it.

## 5 — the reason the probes were dead

Round 8 threw a `ReferenceError` on every load. The mote field's state was
declared beside the painter at the foot of the script, but the interface
switch — which runs as the file is read, several hundred lines higher —
calls `readMoteColours()`. Everything below the throw never ran, and the probe
buttons are wired below it.

It was invisible because a page that throws at parse time still *looks*
finished: the markup is all there. The check that catches it is not reading the
file, it is loading the page and asserting the console is empty. That check now
exists, and this round runs it.

## What was verified, not asserted

Headless Chrome driven over the DevTools protocol in real time. For **both**
propositions, this sequence was walked: boot → hop via the Navigate-to rail →
hop by clicking a vein → hop to a fringe card → slow on/off → reduced motion
on/off → offline on/off → arrange mode on/off → interface to the Table and
back → palette switch → settings exit and return → hop → search arrival →
bell arrival.

At every step: **zero overlapping card pairs** across all 25 nodes, the focused
card **centred** at 792,434 in a 1584 × 885 viewport, and **zero console
errors**. Drift was sampled at 1.2s intervals — it moves, it stops under
reduced motion, it starts again, and the focused card reads `0px` throughout.

## The files

```
theme-a/index.html   theme-a/tokens.css     the Table as a coupled reading
theme-b/index.html   theme-b/tokens.css     the Table as an independent layout
atlas-layout-determinism.json               placement, persistence, insertion, drift
atlas-traversal-map.json                    the edges, and the vein as a control
atlas-degradation-spec.json                 slow · partial · offline · failure
```

Both files are self-contained: no CDN, no font link, no image, no library. Open
either one directly.

## What has not changed, and should be checked for that

The chrome register and its blur test. The physical split of the rails — left
mutates, right travels. The edge list as the single source of truth. Multi-hop
lighting what it passes through. Search and the bell as chains crossed. The
keyboard walking the graph. The cursor law. Alt+wheel. The unknown region drawn
as unknown and still traversable. The density rule drawn rather than narrated.
The settings page as it was finally accepted, captions verbatim, and the shell
transform `scale(0.42) translateX(-25%)` untouched. And the voice — *"Sixty-five
is a pattern match on one photograph, not a diagnosis."*

A fixing round is the easiest place to flatten those by accident. None of them
was touched.
