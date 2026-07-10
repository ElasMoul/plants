---
id: plantpal-20260709-ai-gateway-full-coverage
date: 2026-07-09
from: plantpal
to: [ai-gateway, contracts]
capability: Route all of PlantPal's AI calls (vision identification, annotation, structured reasoning, streaming chat, PlantNet auxiliary lookups) through ai-gateway, closing gaps G1-G6 and adding a per-app model-manifest contract
needs-owner: false
status: archived
---

> **Archived 2026-07-10.** Fulfilled and owner-approved by both workers (`contracts` v0.9.1
> `ai.model-manifest` schema; `ai-gateway` G1/G3/G4/G5/G6 — see
> `../contracts/demands/fulfilled/plantpal-20260709-ai-gateway-full-coverage-report.md` and
> `../ai-gateway/demands/fulfilled/plantpal-20260709-ai-gateway-full-coverage-report.md`).
> PlantPal's own follow-ups (this demand's §5) worked on `feature/PP-088-gateway-full-routing`:
> `ai-model-manifest.yaml` committed (G5, declarative only — ai-gateway doesn't mount
> `AI_MANIFEST_DIR` yet, and its contracts pin stays at v0.7.0 pending two confirmed v0.9.0
> defects); GITHUB_GPT4O/GITHUB_GPT41 identification + the always-on gpt-4o-mini annotation now
> route through the gateway when enabled, same additive if/else shape as the existing
> ANTHROPIC_CLAUDE/PLANTNET routing (G1); PlantNet aux lookups (projects/languages/quota) and the
> disease cross-check now route through `PlantNetGatewayClient` → `ai-gateway`'s `/ai/plantnet/*`
> endpoints (G4); G3 needed no PlantPal-side change, existing gateway context-assertion coverage
> re-verified green. G2 (streaming chat sessions) and the Ollama-vision-routing ruling remain
> explicitly out of scope — chat still streams direct-to-Ollama, Ollama vision routing untouched.

# Demand — ai-gateway: full AI coverage for PlantPal

> **From:** `plantpal` (tenant zero) · **To:** platform architect → `ai-gateway` / `contracts`
> **Date:** 2026-07-09 · **Status:** open
> **Owner directive:** ai-gateway should handle *all* of an app's AI access; where it can't
> yet, the app writes a demand describing the need. This is that demand. Per the
> cross-repo rule, nothing here was implemented in `ai-gateway` or `contracts` from the
> plantpal session — this document is the handoff.

---

## 1. PlantPal's complete AI need inventory

What "gateway handles everything" has to cover, verbatim from production code paths:

| Capability | In / out | Models used today | Gateway status |
|---|---|---|---|
| Species identification | photo (base64) in → strict JSON out | gpt-4o, gpt-4.1, claude-sonnet-4-6, gemma3:4b (local), PlantNet (specialist API) | Claude ✅, PlantNet ✅ (identify only), **gpt-4o/4.1 ❌ (G1)**, Ollama-vision unruled |
| Visual annotation (polygon regions on the photo) | photo in → JSON regions | gpt-4o-mini (always, not preference-routed) | ❌ (G1 — same media gap) |
| Structured reasoning (cure advice, disease description, care plans) | text in → strict JSON out | DeepSeek-R1, o4-mini, gpt-4.1-mini, claude-sonnet-4-6, gemma3:4b | ✅ routed — but **G3 degrades every Ollama call** |
| Chat assistant (garden context + history, token streaming) | text + context in → streamed text out | gemma3:4b (local) | ❌ buffered-only (G2), and G3 drops the entire context |
| Species enrichment (system-initiated, no triggering user) | text in → JSON out | DeepSeek-R1 or gemma3 | ✅ routed (`userId="system"`) |
| PlantNet auxiliary | projects / languages / quota lookups; disease cross-check | PlantNet REST | ❌ no adapter (G4) |

## 2. The gaps (G1–G6)

- **G1 — OpenAI/Azure adapter is text-only.** It never reads `request.media()`; routing any
  GitHub-Models vision call today would silently drop the photo and answer from text. Need:
  media support in the OpenAI adapter (and a ruling on Ollama-vision routing, open since the
  gateway-swap chunk). *This is the single biggest blocker to "gateway handles all AI."*
- **G2 — No streaming.** `/ai/request` is buffered. Chat currently streams token-by-token
  direct from Ollama; through the gateway it degrades to one giant final chunk. Proposal in §3.
- **G3 — OllamaAdapter ignores `request.context` entirely.** Confirmed by code-read: every
  gateway-routed chat turn loses the system prompt, garden context, and conversation history;
  Ollama reasoning calls lose the JSON-shape instruction (silent downgrade to unstructured
  text). Must be fixed before chat/reasoning routing is anything but nominal.
- **G4 — PlantNet coverage is partial.** `identify()` has an adapter (and now honors
  organs/project/lang context — thanks for that fix). Still uncovered: `getProjects()` /
  `getLanguages()` / `getQuota()` and the disease cross-check endpoint. Low urgency, but
  they're AI-provider calls living outside the gateway, which the directive says shouldn't exist.
- **G5 — No model-manifest contract.** There is no way for an app to declare which models it
  needs; `modelHint` is free text per call. See §4 — this is the structural fix.
- **G6 — Hardening (pre-existing audit findings that become PlantPal's problem once routed):**
  no connect/read timeouts on Anthropic/DeepSeek/OpenAI adapters (a hung provider hangs the
  app's request); unknown `modelHint` silently falls back to Anthropic (a typo returns a
  plausible answer from the wrong model); unmapped `appId` gets **no guardrail** at all.

## 3. Streaming chat — gateway sessions (owner's sketch, one correction applied)

Owner's idea: the gateway opens **limited sessions** with access tokens + refresh, so it isn't
re-engaged on every message but still controls rate limits and token spend. Sound — with one
correction required by D022: **the session token grants access to the gateway's streaming
endpoint, never to the provider.** Provider keys must not leave the gateway, so the stream
still physically transits it; what the session removes is the *per-message* preflight/Treasury
round-trip, not the gateway itself.

Sketch for the ai-gateway spec:

1. `POST /ai/session` `{appId, userId, capability: "chat", modelHint}` → one Treasury
   preflight → `{sessionId, sessionToken (short TTL), refreshToken, ceilings}`.
2. `GET /ai/session/{id}/stream` (SSE or WS, bearer sessionToken): app sends messages, gateway
   proxies the provider stream token-by-token. Cheap passthrough — no per-message preflight.
3. Accounting: gateway tallies tokens per session, flushes `usage.event`s periodically and at
   session close. Refresh extends the session while ceilings allow.
4. Mid-session downshift/block arrive **in-band as typed events** (`{"type":"downshift",...}` /
   `{"type":"blocked","reason":...}`) — never a silent stall; the app surfaces the block state
   (D023, same rule as the 402 on the sync path).

PlantPal commits to consuming this the day it exists: `chatStream()` moves to the session
endpoint and the direct-Ollama streaming path retires. Until then, chat streaming stays
direct-to-Ollama (status quo, documented asymmetry).

## 4. Model-manifest contract (new schema in `contracts`)

Proposal: `ai.model-manifest` — a static per-app declaration living next to
`app-manifest.yaml`, consumed by ai-gateway at startup:

- **Per capability** (vision-identification, annotation, reasoning-json, chat): the model set
  the app uses, in preference order.
- **Downshift policy per capability** (D023): PlantPal declares downshift-to-local *acceptable*
  for reasoning and chat, **not acceptable for identification** — a 4B local model
  misidentifying a plant is worse than an explicit "limit reached, try later"; block instead.
- **Gateway behavior keyed off it:** validate `modelHint` against the declared set (unknown →
  400, closing G6's silent-Anthropic fallback); key guardrails off the manifest (closing the
  unmapped-appId → no-guardrail hole); capacity planning gets a real inventory of who needs what.

Draft of PlantPal's declaration (shape illustrative — contracts owns the final schema):

```yaml
appId: plantpal
class: low-stakes
capabilities:
  vision-identification:
    models: [gpt-4o, claude-sonnet-4-6, gpt-4.1, gemma3:4b, plantnet]
    media: required
    downshift: block          # never silently downshift an identification
  annotation:
    models: [gpt-4o-mini]
    media: required
    downshift: skip           # annotation is decorative; skip beats degrade
  reasoning-json:
    models: [DeepSeek-R1, o4-mini, gpt-4.1-mini, claude-sonnet-4-6, gemma3:4b]
    downshift: allow
  chat:
    models: [gemma3:4b]
    streaming: desired        # consumes §3 sessions when available
    downshift: allow
```

## 5. What PlantPal does once each gap closes

| Gap closed | PlantPal follow-up (a chunk each, small) |
|---|---|
| G1 | Route gpt-4o/4.1 identification + gpt-4o-mini annotation through the gateway; direct GitHub-Models client retires from the hot path |
| G2 (§3 sessions) | `chatStream()` → session endpoint; in-band block/downshift wired to the block-state UI |
| G3 | No code change — existing routed calls simply start working correctly; re-verify with the context-assertion tests already in place |
| G4 | Swap PlantNet aux + disease cross-check to gateway calls |
| G5 | Commit `ai-model-manifest.yaml` at repo root; drop per-call-site `modelHint` improvisation |
| G6 | Nothing — pure gateway hardening |

End state: every AI byte in and out of PlantPal transits ai-gateway; the direct provider
clients survive only as the seeds they were always meant to be (spec §2.1).
