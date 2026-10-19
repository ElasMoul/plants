# Photo GPS audit (PLA-118)

Question: do plant photos keep the GPS coordinates a phone writes into the image metadata?
A plant photo can reveal where someone lives.

## Finding before the fix

Determined by reading the pipeline at `dev` 8791f4e (the pre-fix integration test was not run as
a separate failing run; `PhotoGpsIT` asserts the behaviours below and could not pass on that code):

| Place | Result |
|---|---|
| Stored photos | **Kept GPS.** `IdentificationServiceImpl.submitIdentification` passed the raw upload to `FileStorageService.savePhoto`, which wrote the bytes unchanged (disk or Cloudinary, plus the Redis cache). `ExifDateTakenReader` (PLA-92) only reads the date; nothing removed anything. |
| Served photos | **Kept GPS.** `GET /photos/{filename}` and `/api/v1/photos/{filename}` are public and return the stored bytes. |
| Photos sent to AI providers | **Kept GPS.** The identification, annotation and PlantNet calls read the stored photo through `loadPhotoBytes` and forwarded those bytes. |

## Fix

* `PhotoGpsStripper` removes GPS where upload bytes enter the backend (`submitIdentification` is
  the only upload path; plant photos, chat and PlantNet all reuse the stored photo URL). Everything
  downstream (storage, serving, Redis, Kafka event, AI providers, PlantNet) therefore sees the
  stripped bytes. The date taken is read from the original bytes first, unchanged from PLA-92.
* JPEG: the EXIF GPS directory is zeroed in place and its pointer neutralised; pixels, date taken,
  orientation and all other fields are untouched (lossless, no re-encode). An XMP segment that
  repeats the coordinates is dropped.
* PNG / WebP: the chunk holding EXIF (and XMP with coordinates) is dropped whole, so these two
  formats lose their date taken if they had one. Re-uploading the same photo as JPEG keeps it.
* Images without GPS are stored byte-for-byte as uploaded.
* Fails closed: an image that cannot be parsed, or that still reports GPS after stripping, is
  rejected (`400 Image could not be processed`); unverified bytes are never stored.
* Migration `042_strip_photo_gps` (Liquibase custom task) runs every stored photo referenced by
  `identifications.photo_url` or `plants.photo_url` through the same stripper and overwrites it under
  the same `/photos/<filename>` URL, refreshing the Redis copy (and Cloudinary with `invalidate`).
  It is idempotent, leaves GPS-free photos untouched, logs and skips a photo it cannot read
  (summary line: `Photo GPS cleanup: scanned=…, rewritten=…, unchanged=…, failed=…`), and does not
  touch `date_taken`. It rewrites bytes in place and cannot be undone.

## Result after the fix

| Place | Result | Proof |
|---|---|---|
| Stored photos | No GPS; `date_taken` equals the fixture's DateTimeOriginal | `PhotoGpsIT.uploadStoresAndServesWithoutGps` |
| Served photos | No GPS on both URL shapes, authenticated and anonymous, disk and Redis-cached | same test |
| Photos sent to AI providers | No GPS. Every AI/PlantNet call loads its bytes with `loadPhotoBytes`, which the test asserts is clean. The provider client is not captured at its HTTP boundary. | same test |
| Photos already stored | Cleaned by migration 042; clean photos byte-identical; corrupt photo logged and skipped; second run changes nothing | `PhotoGpsIT.migrationCleansStoredPhotos` |
| Stripper behaviour | JPEG / PNG / WebP / XMP / no-EXIF / garbage / idempotent | `PhotoGpsStripperTest` |

Fixture: `backend/src/test/resources/photos/gps-tagged.jpg` (regenerate with
`GpsPhotoFixtures.main`).

## Limits

* Photos already sent to external AI providers or PlantNet in the past cannot be recalled.
* The migration does not run in production as part of this task; promotion to `main` runs it
  there, so take a storage backup first.
* Copies held outside the app (browser caches, downloads) are out of reach.
