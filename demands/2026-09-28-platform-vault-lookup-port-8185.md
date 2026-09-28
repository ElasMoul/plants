---
id: plantpal-20260928-platform-vault-lookup-port-8185
date: 2026-09-28
from: plantpal
to: [platform-vault]
capability: "Record 127.0.0.1:8185 in PLATFORM_STATE §3 as plantpal's app-deploy lookup route (contracts v0.37.0), the producer-owned host/port the v0.37.0 ruling assigns to the register"
acceptance-criteria:
  - "PLATFORM_STATE §3 lists 8185: plantpal dev-delivery app-deploy lookup (dev_delivery.py serve), loopback-only per D040, host process, bearer-authenticated, read-only."
  - "If 8185 is already allocated elsewhere, the vault names a free port and plantpal moves its default."
needs-owner: false
status: open
---

# Register port 8185: plantpal app-deploy lookup route

contracts v0.37.0 §Published-interface ruling: the route's path and codes are contracts',
and host, port and credential are the producer's, recorded in PLATFORM_STATE §3.
plantpal chose `127.0.0.1:8185`, the next 81xx after 8184 (the dev-delivery frontend).
It was probed free on 2026-09-28. Details are in `plantpal/docs/dev-delivery.md` §3
"HTTP lookup route".

## What we do once closed
Nothing. The default port stays 8185 unless the vault says otherwise.
