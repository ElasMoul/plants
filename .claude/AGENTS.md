# PlantPal — Agent Recovery Guide
> If you lose a conversation, paste the matching section below
> as the FIRST message in a new Claude Code conversation.
> The agent will be fully restored.

---

## ARCHITECT AGENT
> Conversation type: Claude.ai (this project)
> Scope: Architecture and infrastructure only.
> To restore: paste contents of ARCHITECT.md as first message.

## BACKEND AGENT  
> Conversation type: Claude Code
> Scope: Java/Spring Boot — execution and troubleshooting.
> To restore: paste contents of BACKEND.md as first message.

## FRONTEND AGENT
> Conversation type: Claude Code  
> Scope: Angular — execution and troubleshooting.
> To restore: paste contents of FRONTEND.md as first message.

---

## How to keep these files current
After each session:
1. Update STATE.md (completed tasks, active branches, open items)
2. Update the relevant agent .md if new patterns or decisions were established
3. Commit: `chore(agents): update agent memory — [what changed]`