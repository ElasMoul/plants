# Transactional email (PLA-25)

How verification and recovery emails are sent, how to configure them, and what the operator sees when
sending fails. Payment receipts and marketing email are out of scope.

## How it works

```
verification / recovery code
        │  MailService.send(template, to, language, {name, link})
        ▼
  email_outbox row (PENDING)   ← written in the caller's transaction
        │
        ▼  EmailDispatcher (every app.mail.poll-interval, default 10s)
  claim due rows (FOR UPDATE SKIP LOCKED) → render EN/FR template → EmailSender
        │                                                   │
        ▼ success                                           ▼ failure
  SENT, variables wiped                      retry with backoff, then FAILED
```

- **One abstraction.** `com.plantpal.mail.service.MailService#send` is the only entry point. Nothing else
  talks to a transport. `EmailSender` is the port; `SmtpEmailSender` (`app.mail.transport=smtp`) and
  `LoggingEmailSender` (`log`, the default) are the adapters.
- **Queued, not inline.** A provider outage never fails a registration or recovery request, and the email
  exists only if the caller's change commits.
- **Templates.** `src/main/resources/mail/<verification|recovery>.<en|fr>.{subject.txt,txt,html}`. Placeholders are
  `{{name}}` and `{{link}}` (both required; `link` must be http(s)). HTML output is escaped. An account language
  without a template (Arabic today) gets English.
- **One-time links are not kept.** `email_outbox.variables` holds the link while the row is PENDING and is blanked
  as soon as the row is SENT or FAILED. Logs show a masked address (`s***@example.com`) and the subject, never the
  body.
- **At-least-once.** If the process dies after the provider accepted a message but before the row is marked SENT,
  the claim lease expires and the email is sent again.

## Retry and failure visibility

| Setting (`app.mail.*`) | Default | Meaning |
|---|---|---|
| `max-attempts` | 5 | Sends tried before the row becomes FAILED |
| `retry-base-delay` | `PT1M` | Wait before attempt 2; doubles each time (1, 2, 4, 8 min) |
| `claim-lease` | `PT2M` | How long a claimed row is hidden from other instances |
| `batch-size` / `poll-interval` | 10 / `PT10S` | Rows per cycle / cycle period |

A permanent content error (bad stored variables, missing template variable) fails immediately without retrying.

When an email gives up, the operator sees it in three places:

1. **Admin API:** `GET /api/v1/admin/email-deliveries?status=FAILED` (admin only; also `PENDING` and `SENT`). Each
   item has `template`, `recipient`, `attempts`, `lastError`, `nextAttemptAt`, `sentAt`. It never returns the
   message body or link.
2. **Logs:** one `ERROR  Email delivery FAILED permanently: id=… template=… to=s***@… attempts=… error=…` line
   per failure (staging/prod ship ERROR events to Sentry). Each retry logs a `WARN`.
3. **Metric:** Micrometer counter `plantpal.mail.delivery` with tag `outcome=sent|retry|failed`
   (`/actuator/metrics/plantpal.mail.delivery`; exposed in dev, not in prod, where only `health,info` are).

## Configuration

Secrets are environment variables only; nothing is stored in the repository.

| Variable | Staging/prod | Value |
|---|---|---|
| `MAIL_HOST` | required | `smtp.resend.com` |
| `MAIL_PORT` | optional (587) | `587` (STARTTLS, required by the app) |
| `MAIL_USERNAME` | required | `resend` |
| `MAIL_PASSWORD` | required | the Resend API key |
| `MAIL_FROM` | required | `Planotell <no-reply@<verified domain>>` |
| `MAIL_TRANSPORT` | optional (`smtp`) | `log` to switch delivery off |

Staging and prod fail at startup if a required `MAIL_*` variable is missing. Locally the default is
`MAIL_TRANSPORT=log`, so nothing is sent.

### Owner steps (outside the repository)

1. Choose the provider (Resend) and add the sending domain.
2. Add the SPF, DKIM and MX records Resend shows at the DNS host, plus DMARC (`_dmarc` TXT, start with
   `p=none`). Wait for the domain to show **Verified**.
3. Create an API key with **sending access** restricted to the domain. Put it in the server `.env`
   (`deploy/vps/.env.example`) or your local `backend/.env`. Never commit it or paste it into a ticket.
4. Record the provider, domain and the verified SPF, DKIM and DMARC on the ticket.

### Sending a real test email

`SmtpSmokeManualTest` sends the verification and recovery emails in EN and FR through the real SMTP adapter and
templates. It is skipped unless `MAIL_SMOKE_TO` is set, so CI never sends mail. Export the `MAIL_*` values from
`backend/.env` (without printing them) and run:

```bash
MAIL_SMOKE_TO=you@example.com mvn test -Dtest=SmtpSmokeManualTest -Djacoco.skip=true
```

Then, in Gmail, open one message and choose **Show original**: `SPF: PASS`, `DKIM: PASS`, `DMARC: PASS`.

## For PLA-26 and PLA-65

```java
mailService.send(
    EmailTemplate.ACCOUNT_VERIFICATION,        // or ACCOUNT_RECOVERY
    user.getEmail(),
    user.getLanguage(),                        // en | fr | ar (ar falls back to en)
    Map.of("name", user.getFirstName(), "link", verificationUrl));
```

Call it inside the transaction that stores the token. An `IllegalArgumentException` means the variables cannot fill
the template and nothing was queued.

## Not covered yet

- No admin UI page for the outbox; the API above is the operator surface.
- No manual "resend" for FAILED rows: PLA-26/PLA-65 issue a fresh token and send a new email.
- Outbox rows are kept; a retention/cleanup job is a follow-up.
