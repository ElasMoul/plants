# Account management audit (PLA-24)

Recorded at base `b5874ee` (epic PLA-23, after PLA-26), before any PLA-24 code change.

| Feature | State | Evidence |
|---|---|---|
| Language (en/fr/ar) | **exists** | `PUT /api/v1/users/me/preferences`; `UserPreferencesRequest.language` is `@Pattern(en\|fr\|ar)`; registration uses the same pattern. |
| Profile names (first/last) | **missing** | Set at registration only; no endpoint changes them, preferences DTO has no such fields, preferences response does not return them. |
| Email display / verified flag | **partial** | `emailVerified` is returned by preferences; the address itself is not, and there is no way to change it. |
| Email change | **missing** | No endpoint. PLA-26 provides `email_verification_tokens` (token row records the `email` it proves), the mail outbox and `ACCOUNT_VERIFICATION`; `verify` rejects a token whose email differs from the user's. |
| Password change | **missing** | No endpoint or service method (only register/login). Password rule at registration: min 8 characters. |
| Session revocation | **partial** | `SessionRegistryService.revoke` and `revokeAllForUser` exist (Redis, keyed by jti; `RevokedReason.PASSWORD_CHANGE` exists) but nothing calls them on password change and there is no "all except this session". The caller's jti is available as the `JwtAuthFilter.JTI_ATTRIBUTE` request attribute. |
| Screens | **partial** | Preferences page (`features/preferences`) has PlantNet, region, business tier, export and the verification resend; nothing for name, email or password. EN/FR/AR strings live in `shared/i18n/{fr,ar}.ts`. |

Design decisions that follow:

- The login identity is `users.email` (JWT subject). An email change therefore stores nothing on the
  user until the new address is confirmed: the change request issues a verification token whose
  `email` is the NEW address and mails that address. Confirming it swaps `users.email` and marks it
  verified. No schema change is needed.
- Existing sessions carry the old address as subject, so they stop working once the address is
  swapped; the user signs in again with the new address.
- Changing email and password both require the current password.
