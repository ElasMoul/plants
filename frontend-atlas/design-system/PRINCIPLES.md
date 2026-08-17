# Rhizome — Principles

Rhizome is PlantPal's design system. PlantPal is a warm, plain-spoken plant
companion; Rhizome is the world that companion lives in. A rhizome is the
underground stem a plant runs sideways — no trunk, no crown, just a live
network where any point can put up a shoot. That is the shape of this app's
data and it is the shape of its interface: **one plane, nodes on a lattice,
veins between them, and a camera that travels.**

Rhizome is **atlas-class** (D053/D071). It is judged by the atlas design law,
C1–C26, and by no other regime's law. The register lives in
`constitution.json` — every C-number cited anywhere in this system resolves
to that file, and nowhere else.

Six principles. Each is an invariant with a check, because a principle you
cannot check is a preference, and a preference is lost by the next round.

---

## 1. The world is the interface

There is one plane, and everything is on it. A plant, a species, a problem, a
treatment course, a journal entry, an unfetched region — all are nodes with a
cell on the same lattice, joined by veins drawn before anyone travels them.
Nothing is ever mounted, unmounted, routed to or replaced; going somewhere is
the camera rescaling and translating one plane, so the place you left is still
in sight when you arrive. Screens are not a unit of this system. *Check
(C1, C2, C4):* after ten hops, the element set on the plane is identical, the
previous focus is still rendered, and `history.length` has not moved.

## 2. Geography is a promise

A node's position is a formula — `origin + cell × pitch + the user's own
persisted offset` — not the output of a simulation, a flow, or render order.
It is stable for the life of the record. A twelfth plant arriving, a status
turning red, a photo attaching, a slow endpoint, a lost connection: none of
these move anything. Only two things ever change a node's position: the user
dragging it in Arrange mode, and a genuinely new node taking a free cell.
*Check (C7, C8, C9):* load twice and diff the computed `left`/`top` of every
node — equal; insert a node and diff again — equal; toggle every degraded
state and diff again — equal.

## 3. Register before decoration

Fixed furniture and content are different *materials*, not different layers.
Chrome is flush to a viewport edge, bleeds off it, never moves with the
camera, takes **no shadow at all**, speaks in tracked uppercase monospace at
one rung, and is inked below a card's strength. A node floats on the plane,
carries the corner, the halo and the membrane hairline, and moves with the
camera. Both are cut from the same palette and the same type — the separation
is material, not a second aesthetic. *Check (C14, C16, C17):* blur the screen
and the cards are the only bright soft rectangles on it.

## 4. Scale is the argument

Rank is computed — breadth-first graph distance from the focus, recomputed on
every hop, never authored — and it is spent on **size**: four card widths,
stepped ink, per-rank height caps, and a recap that sheds its second line at
the fringe. Where text is illegible, a signature channel still carries rank
and kind, so a card answers "how far is this from me" and "what kind of thing
is this" at a size where its name cannot be read. Density is bounded once, for
every collection on the board: under four members draw them all, four or more
draw the two highest-ranked plus one traversable "+N more". *Check (C3, C12):*
freeze a frame, blur the text, and the hierarchy still reads.

## 5. Every motion states which edge was taken

There is exactly one navigation timing — 300ms — for every hop from every
trigger, and one code path that performs it. The camera's centre travels
**along the real edges** by arc length, not straight across the space, so the
motion itself says which vein was crossed and in which order; nodes passed
through light up and never open. A component that picks its own duration, or
a transition that cuts, is a defect and not a variation. Under reduced motion
the trail is still drawn along those edges and held, and the you-are-here
still moves. *Check (C10, C11, C13, C21):* sample the camera centre each frame
and assert every sample lies on the route's polyline.

## 6. Say what is true, per node

PlantPal's own instruction to its assistant — *"if unsure, say so — never
guess about plant health"* — is this system's law about state. Slowness,
staleness, absence and failure are drawn **inside the node they belong to**,
in that node's own words, never as a global banner, never as a spinner, never
as a modal, never as the word "loading". Unknown and empty are different
things and are drawn differently: one dashed and traversable with a real way
to fetch it, the other dashed and offering a way to begin. A failure names the
fact, the time, what happened to the user's data, and two ways forward — with
the camera undisturbed. *Check (C22–C25):* walk every state from the probe
panel without editing markup, and diff the geography each time.

---

## What these principles do NOT forbid

A principle is written as an invariant precisely so it cannot ossify into a
list of banned appearances. In particular: **decorative fields, canvases and
particle layers are permitted**, and any earlier prohibition on them in a
predecessor system is lifted and must not reappear. What governs them is
Principle 2 and the layer-ownership rule: decoration belongs to the surface it
decorates, is deterministic (fixed seed), reads its colours from the tokens so
it follows a live palette change, and moves nothing.

## Extending this system

`assets/` is not frozen. A theme round may add a token family or a primitive
under a **named amendment** recorded in `constitution.json → amendments.register`.
It may not remove or re-key an existing semantic token, and it may not change
a declared parameter without an owner ruling. See `assets/AUTHORING.md` §7 and
`guidelines/extending-this-system.html`.
