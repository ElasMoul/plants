# Herbarium — design principles

Herbarium is PlantPal's design system. It is named for the practice it
borrows from: pressing a specimen onto a paper sheet, labelling it in a
patient hand, numbering the plate, and keeping it. A herbarium sheet is
never alarming, never dense for its own sake, and never dishonest about
what it does not know — the label says *cf.* when the collector was
unsure. That is the register PlantPal already speaks in, so it is the
register the system is built from.

These six principles govern every token, component, pattern and page in
this system. When a decision is not covered by a component spec, decide
it by these.

---

## 1. Paper, not console

The surface is a warm herbarium sheet under windowsill light: bone and
ecru paper, sepia ink, hairline rules, a lot of margin. Colour enters
only where it carries meaning — chlorophyll green for health, chlorosis
amber for attention, necrotic umber for harm, terracotta for the app's
own voice. There are no neon-on-dark gradients, no glows, no particle
fields, no telemetry chrome; a shadow, when it exists at all, is the
soft cast of a specimen lifted a millimetre off the mount, never a
floating card in a dark room. The dark theme is dusk in a potting shed —
damp humus and shadowed leaf — not an operator's terminal. Anything that
would look at home in a monitoring dashboard is wrong here by default.

## 2. Say so when unsure

Confidence is a first-class visual, not a caveat in small print. Every
identification, diagnosis and reading carries its confidence at the same
weight as its claim, and low confidence is rendered as an honest,
non-alarming state — a hedged label on the plate — not as a warning. The
same honesty governs data: a node that has not loaded is drawn as
explicitly *unknown* and is distinguishable at a glance from one that
loaded and was empty; stale data says how stale, per item; a failure is
a state *inside* the thing that failed, with its retry in place, never a
banner that moves the world. The system never fills a gap with a
confident-looking placeholder.

## 3. Every edge is visible before it is taken

The user moves along relationships they could already see. Navigation
affordances are drawn as edges — a labelled connector with a direction —
and they are visually distinct in form, not merely in colour, from
anything that mutates data: an edge is a rule-and-arrow; an action is a
filled stake. Nothing routes to a destination that was not on screen a
moment earlier, and no affordance both moves the camera and changes the
data. A user must be able to tell "this takes me somewhere" from "this
does something to my plant" without reading the label and without
activating it.

## 4. The place holds still

There is one world and the user is always somewhere in it. A persistent
world view carries a you-are-here marker, so "where am I?" is answerable
at every scale by looking, not by hovering or reading a breadcrumb, and
the origin of any hop stays on screen or exactly one visible hop back —
never behind the browser's back button. Positions are computed once,
deterministically, and persisted: two loads of the same garden lay out
identically. Inserting a plant does not move the plants already there,
and a non-structural change — a count, a status, a new photo — moves
nothing at all. Zooming rescales the world; it never unmounts it and
replaces it with a different screen.

## 5. Growth, not sliding

Motion in this system is the motion of a plant: unfurling, filling in,
slow, and always continuous. Nothing fades, pops or teleports — a thing
that changes place travels there, and the camera follows the path of the
edge being taken, carrying direction, scale and ownership so the user
can see the distance being crossed. Search results and arriving
notifications show where they came from rather than cutting to it.
Leaving to settings is choreographed as an exit — the world pulls back,
the app shrinks to a card, the panel opens beside it — rather than a
modal dropped over a blur. Under `prefers-reduced-motion`, transitions
shorten to near-zero but direction is still drawn and the you-are-here
marker still updates; no transition is ever replaced by an abrupt swap.

## 6. Domestic language, beginner's footing

The vocabulary is the vocabulary of someone standing next to their
plant with a phone: *my garden*, *nickname*, *due today*, *needs
attention*, *recent scans*. Latin binomials are information the system
supplies, set in italic as a secondary line — never something the user
must know or type. Copy is second person, calm, specific and free of
jargon; seriousness is carried by structure and confidence, never by
alarm colour or exclamation. Type is set on a five-step prominence
ladder (100 / 75 / 55 / 30 / 18–25) so that with the text blurred the
hierarchy of a screen is still readable from scale alone, and the layout
is generous enough that a garden of one plant reads as a world with room
in it rather than an empty dashboard. The system is trilingual by
construction — FR / AR / EN — so every component is logical-property
based and correct under RTL.
