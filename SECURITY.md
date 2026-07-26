# Security Policy

NutriAI handles personal health data. Security and privacy are first-class requirements,
not afterthoughts. This document tracks the controls in place and how to report issues.

## Reporting a vulnerability

Please open a private security advisory on the GitHub repository, or email the maintainer.
Do **not** file public issues for security problems.

## Data protection

- **Encryption at rest (application layer):** sensitive health fields (weight, body
  measurements, blood pressure, blood sugar, conditions) are encrypted with AES-256-GCM
  before being written to Postgres, on top of Neon's own at-rest encryption. The key comes
  from `HEALTH_DATA_ENCRYPTION_KEY` and is never committed.
- **Encryption in transit:** TLS everywhere (Neon requires `sslmode=require`; Render
  terminates TLS for the API).
- **Passwords:** hashed with Argon2id. Never logged, never returned by the API.
- **Tokens:** short-lived JWT access tokens + rotating refresh tokens with revocation.
  On Android, tokens are stored in `EncryptedSharedPreferences` backed by the Keystore.

## Access control

- RBAC: `user`, `dietitian`, `admin`. Family members are scoped to their owning user.
- `AuditLog` records every access to health data.

## Application hardening

- `helmet` security headers, strict CORS allow-list (`CORS_ORIGINS`), rate limiting
  (Redis-backed when `REDIS_URL` is set, in-memory otherwise), and input validation with
  `zod` at every boundary.
- Secrets are read from the environment only. `.env` is git-ignored; only `.env.example`
  (placeholders) is committed.

## Encryption-at-rest tradeoffs (plaintext vs encrypted fields)

Sensitive health data is AES-256-GCM-encrypted at the application layer with **key versioning**
(`HEALTH_DATA_ENCRYPTION_KEY`, optional `…_V2` for rotation; legacy 3-part ciphertext still reads).
Encrypted blobs: `Profile.sensitiveEnc`, `FamilyMember.sensitiveEnc`, `VitalLog.valueEnc`,
`Medication.detailsEnc`, `ChatMessage.content`, `WeeklyCheckin.measurementsEnc`, and
`PeriodLog.detailsEnc`.

A few fields are deliberately kept **plaintext** because the engines must query/order by them and a
value alone is low-sensitivity in isolation:

- **`PeriodLog.startDate` / `endDate`** — the cycle-phase math and ordering need to filter and sort
  by date; a bare date isn't identifying on its own. The *sensitive* per-period detail (symptoms,
  flow, mood, notes) lives encrypted in **`PeriodLog.detailsEnc`** and is decrypted in memory only
  when explicitly requested — never touched by the (date-only) cycle computation.
- **`VitalLog.type` / `measuredAt`** and **`Medication.type` / `active`** — needed for
  series/filtering; the actual readings and medicine names/doses are in the encrypted blob.
- **`WeeklyCheckin` subjective 1–5 ratings** (mood/stress/sleep) — coarse, non-identifying scalars
  used for trend math; the weigh-in/measurements are encrypted.

## Data subject controls

- **Export my data** and **delete account** (cascade + audit) are supported endpoints.

## Medical safety

NutriAI provides **educational guidance, not medical advice**. Deterministic guardrails
(see `server/src/guardrails`) enforce calorie floors, safe weight-change caps, and
condition-specific modifiers. See the disclaimers throughout the app.
