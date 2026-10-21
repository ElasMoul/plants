# Personal-data map (PLA-57) — placement, light and region entries

PLA-107 adds four optional fields. All are nullable and unset by default.

| Field | Stored in | Purpose | Notes |
|---|---|---|---|
| Placement (`INDOOR`/`OUTDOOR`/`GREENHOUSE`) | `plants.placement` | Lets care fit where the plant lives | Chosen from a fixed list; owned by the plant's user |
| Light exposure (`LOW`/`MEDIUM`/`BRIGHT`/`DIRECT_SUN`) | `plants.light_exposure` | Lets care fit how much light the plant gets | Chosen from a fixed list |
| Region country | `users.region_country` | Coarse region, so care can later fit the user's area | ISO 3166-1 alpha-2 code, validated server-side against the JDK list |
| Region climate zone (`TROPICAL`/`ARID`/`TEMPERATE`/`CONTINENTAL`/`POLAR`) | `users.region_climate` | Same purpose, climate granularity | Fixed list, validated server-side |

**No precise location is requested or stored.** There are no coordinates, address, free text,
geolocation prompt, geocoding or IP lookup behind these fields. Nothing consumes the values yet
(no care-plan, reminder or AI-prompt use). They are returned only to their owner
(`/plants`, `/users/me/preferences`) and are removed with the plant or account like other rows.

> The canonical PLA-57 map was not found in this repository; this file records the entries so they
> can be merged into it.
