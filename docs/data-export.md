# Self-service data export (PLA-112)

A signed-in user can download their own plants, care log, scan history and photos from
**Preferences → Export my data** in the classic app. This complements the manual privacy-request
workflow (PLA-61), which is unchanged.

## API

| Call | Result |
|---|---|
| `POST /api/v1/users/me/export` | Builds the ZIP synchronously. `ApiResponse` with `id`, `expiresAt`, `sizeBytes`, `retentionHours`. |
| `GET /api/v1/users/me/export/{id}` | Streams the ZIP (`Content-Disposition: attachment`). |

Both require a bearer token (401 otherwise). The user comes only from the authenticated principal,
never from the path or body.

## Contents

`plants`, `care-log`, `scan-history` and `journal` (PLA-111 events) as `.json` and `.csv`, a `photos/` folder (named from record
ids, e.g. `plant-12.jpg`, `scan-40.jpg`) and a `README.txt` with the creation time, the retention and
any photo that could not be included. Archived plants are included. CSV cells starting with `=`, `+`, `-` or `@` are prefixed with `'` so spreadsheets do
not run them as formulas. Chat history, push subscriptions and AI provider data are not exported.

Rows come from user-scoped, id-ordered repository queries paged 200 at a time and are streamed into
the ZIP file; nothing is built in memory.

## Isolation

The file is `{userId}-{uuid}.zip` in `app.export.directory`. The download resolves
`{authenticatedUserId}-{id}.zip` only, so another user's id, a missing id, an expired id and a
non-UUID all return the same 404.

## Rate limit

`app.export.rate-limit-per-hour` (default **3**) per user, using Bucket4j in memory per instance.
Over the limit: HTTP 429 with a `Retry-After` header.

## Retention

`app.export.retention-hours` (default **24**). An hourly job (`ExportCleanupJob`) deletes export and
abandoned partial files older than that by file modification time; the download also refuses files
past retention. The UI and `README.txt` state the configured value.

## Limits and configuration

| Property | Env var | Default |
|---|---|---|
| `app.export.directory` | `APP_EXPORT_DIRECTORY` | `${java.io.tmpdir}/plantpal-exports` |
| `app.export.retention-hours` | `APP_EXPORT_RETENTION_HOURS` | 24 |
| `app.export.rate-limit-per-hour` | `APP_EXPORT_RATE_LIMIT_PER_HOUR` | 3 |
| `app.export.max-photos` | `APP_EXPORT_MAX_PHOTOS` | 200 |
| `app.export.max-photo-bytes` | `APP_EXPORT_MAX_PHOTO_BYTES` | 209715200 (200 MiB) |

Photos beyond the caps are skipped and listed in `README.txt`. Files live on the container's local
disk: with several backend instances and no shared volume, a download can reach an instance that
does not hold the file (known V1 limitation).

## Review data (dev profile only)

`DataExportReviewSeeder` creates `export-owner@plantpal.test` and `export-other@plantpal.test`
(password `export-password-1`), each with an active and an archived plant, a care log, a scan and a
photo. The owner's export must contain only "Owner …" values.
