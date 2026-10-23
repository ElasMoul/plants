# Email verification (PLA-26)

How Planotell proves a user owns their email address, what an unverified user may do, and how
existing users are treated. Mail delivery itself is in `docs/transactional-email.md`.

## Model

- `users.email_verified_at` (nullable). `NULL` means "ownership not proven".
- `email_verification_tokens`: one row per issued link. Only the SHA-256 of the token is stored.
  The token is 32 `SecureRandom` bytes, base64url. Lifetime 24 h. Single use (`used_at`).
  Each row also records the `email` it proves.

## Flow

1. **Register** sends the first link (queued through the mail outbox, so a provider outage never
   fails registration). The link is `<app.mail.public-base-url>/login/verify-email?token=...`.
   Set `APP_MAIL_PUBLIC_BASE_URL` to the frontend origin in staging and production (default
   `http://localhost:4200`; the app refuses to start with a non-http(s) value).
2. The landing page `POST /api/v1/auth/verify-email {token}` (public). A POST, not a GET, so a
   mail scanner that prefetches links cannot spend the token.
3. Consumption is one conditional `UPDATE` (`used_at IS NULL AND expires_at > now AND hash =`), so of
   any number of concurrent attempts exactly one wins. The address is then marked verified only
   if the token's `email` still equals the user's current email.
4. Every failure (unknown, altered, reused, expired, address changed) returns the **same** 400 and
   message, so the endpoint does not reveal which case occurred.
5. **Resend** `POST /api/v1/auth/verify-email/resend` (signed-in; acts only on the caller). Already
   verified: 200, nothing sent. Otherwise at most one new link per 60 s and 5 per hour per user,
   counted from the token table (429 with `Retry-After`). A new link retires every earlier unused one.

## What an unverified user may do

Enforced by the server, not the UI. The gate is `@RequiresVerifiedEmail` (an aspect that runs
before AI quota is reserved) and returns **403 with `code: EMAIL_NOT_VERIFIED`**.

| Allowed while unverified | Blocked until verified |
|---|---|
| Sign in, sign out, session renewal | Start a new identification (`POST /api/v1/identifications/analyze`) |
| Recover a password (independent of verification) | Send a chat message (`POST /api/v1/chat`, `/api/v1/chat/stream`) |
| Read and manage own plants, groups, journal, reminders, care log, history | |
| Read and change preferences; export own data | |
| Request a verification email | |

Widening the blocked set later is one annotation per service method. Login is never gated, and
the JWT carries no verification claim: the status is read from the database.

## Existing users

The migration (048) is additive: it adds a nullable column and a table, with no backfill and no
`NOT NULL` on existing rows. Nothing seen proves any existing address, so **every existing user
starts unverified** and is never treated as verified. They are not locked out (login and recovery
do not read the column); they see "not verified" on the preferences page and can resend the link
there. No mass email is sent.

## Changing the address (PLA-24)

`PUT /api/v1/users/me/email {newEmail, currentPassword}` (signed in). The password is checked, the
address must differ from the current one and be unused, then a link is mailed to the NEW address in
the account's language (same `ACCOUNT_VERIFICATION` template). Nothing on the user row changes yet:
the account keeps its old address, login and verified status, and `pendingEmail` shows the waiting
address. The link token carries a `chg_` prefix (part of the hashed value), which is what lets
`verify` swap `users.email` to the token's address and mark it verified; an ordinary link for another
address is still refused, and a taken address at confirmation time is refused with the generic 400.
Existing sessions carry the old address, so the user signs in again with the new one. A change
request retires every earlier open link; only the hourly cap (5) applies to it, not the 60 s cooldown.

## Password recovery (PLA-65)

Independent. Recovery is not gated on verification, and completing a recovery does not mark the
address verified. The two flows only share the mail templates and outbox.

## Dev and review

Dev sends no real mail (`app.mail.transport=log`; bodies are never logged). The dev profile seeds
`pla26-pending@`, `pla26-expired@` and `pla26-verified@plantpal.test` (password `pla26-password-1`);
the pending and expired accounts hold fixed links, `pla26-valid-link-0001` and
`pla26-expired-link-0002`, usable at `/login/verify-email?token=<value>`.
