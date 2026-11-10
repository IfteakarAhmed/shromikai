# ShromikAI: API Specification

| Field | Value |
| --- | --- |
| Spec version | v1.1 (tag `spec-v1.1`), aligned with the master prompt playbook v1.2 |
| API version | `v1`, base path `/api/v1`; OpenAPI at `/v3/api-docs`, UI at `/swagger-ui` |
| MCP endpoint | `/mcp` (profile `mcp`) |
| Schema dialect | JSON Schema 2020-12 |
| Error format | RFC 9457 `application/problem+json` |

Binding rules: [`AGENTS.md`](../../AGENTS.md). Requirements: [`PRD.md`](PRD.md). The JSON Schemas
in §5 are normative; the springdoc OpenAPI document must match them (`OpenApiContractTest`).
Example values that look like statute references use the fictional pack `xx`; they are not law.

---

## 1. Conventions

| Topic | Rule |
| --- | --- |
| Encoding | UTF-8 JSON; REST field names in `camelCase`; MCP tool arguments and results in `snake_case` (§9); enum values in `UPPER_SNAKE_CASE` except language codes |
| Languages | `bn` and `en`. `Accept-Language` selects the language of problem `title`/`detail` and SSE progress messages; default `bn`. Analysis content uses `outputLanguage` from the request |
| Time | RFC 3339 in UTC with `Z`, millisecond precision |
| IDs | UUID v4 strings unless a schema states another pattern |
| Nulls | Optional response fields are present with `null`; requests may omit optional fields |
| Pagination | Query `page` (0-based, default 0) and `size` (1 to 100, default 20); response `{ "items": [...], "page": 0, "size": 20, "totalItems": 57 }` |
| Idempotency | `POST /analyses` and `POST /compare` accept `Idempotency-Key` (1 to 64 visible ASCII characters); same subject and key within 30 minutes returns the original response; same key with a different body returns `409` `idempotency-key-reused` |
| Request IDs | Every response carries `X-Request-Id`; problem documents carry it as `traceId` |
| Caching | Responses under `/analyses`, `/compare`, `/memos`, `/review-tickets` and `/auth` send `Cache-Control: no-store` |

## 2. Authentication and authorisation

| Subject | How it authenticates |
| --- | --- |
| User (`WORKER`, `HR`, `ADMIN`) | `Authorization: Bearer <access JWT>` (15 minutes, HS256, issuer `shromikai`, claims `sub`, `role`, `org`, `demo`). Refresh token in cookie `shromik_refresh` (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`), 7 days, rotated on each refresh |
| Guest | `Authorization: Bearer <guest JWT>` from `POST /auth/guest` (2 hours, role `WORKER`, claim `guest: true`, claim `dev` = SHA-256 of the device token); no refresh token |
| Demo user | Same as a user, claim `demo: true`; only in the `demo` profile |
| API key | `Authorization: Bearer shk_<40 base62>`; scopes `analyses`, `laws`, `mcp`, `automation`; belongs to an organisation. The n8n key holds `automation` and `laws` (§10) |

Tokens and keys are accepted **only** in the `Authorization` header, on every endpoint including
SSE (§4.2). A request that carries `access_token`, `token` or `api_key` as a query parameter is
rejected with `400` `validation-failed` before authentication, so credentials never appear in
URLs, access logs or browser history (architecture T-23). A guest whose token expires during an
SSE stream requests a new guest token with the same device token; the subject (the device-token
hash) is unchanged, so the stream can resume.

Access matrix (✔ allowed; "own" = the caller's own resources or organisation):

| Endpoint group | Public | Guest | WORKER | HR | ADMIN | API key scope |
| --- | --- | --- | --- | --- | --- | --- |
| `GET /meta`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui` | ✔ | ✔ | ✔ | ✔ | ✔ | any |
| `/auth/login`, `/auth/refresh`, `/auth/guest`, `/auth/demo/{role}` | ✔ | | | | | |
| `/auth/logout`, `GET /auth/me` | | ✔ | ✔ | ✔ | ✔ | |
| `PATCH /auth/me`, `POST /auth/me/password`, `DELETE /auth/me` | | | ✔ (not demo) | ✔ (not demo) | ✔ (not demo) | |
| `/laws/**` | | ✔ | ✔ | ✔ | ✔ | `laws` |
| `/analyses/**`, `/compare/**`, `/memos/render` | | ✔ own | ✔ own | ✔ own | ✔ own | `analyses` own |
| `GET /review-tickets/{id}` (with ticket token) | | ✔ | ✔ | ✔ | ✔ | `analyses` |
| `/playbooks/**` | | | | ✔ own org | ✔ | |
| `/review-items/**` | | | | | ✔ | |
| `/admin/**` | | | | | ✔ | `automation` for `/admin/laws/**` (including `POST /admin/laws/retrieval-smoke`) and `POST /admin/review-items` only |
| `/dev/**` (profile `dev` only) | | | | | ✔ | |
| `/mcp` | | | | | | `mcp` |

### 2.1 Auth endpoint behaviour (P2)

| Topic | Behaviour |
| --- | --- |
| Login | `200` `TokenResponse` and the refresh cookie. A wrong password and an unknown or disabled e-mail address give the same `401` `invalid-credentials`. The 5th consecutive failure locks the account for 15 minutes; while locked, every attempt, even with the right password, gets `423` `account-locked` with `Retry-After` and `retryAfterSeconds`. A successful login or the end of the window resets the counter |
| Rate limits | Auth endpoints: 20 requests per minute per IP, and for login 10 per minute per e-mail address, each answered `429` `rate-limited` with `Retry-After`. The IP is the connection's remote address; forwarded headers are not trusted until P11 |
| Refresh | Reads the cookie only. Each call rotates the token. Reuse of an already-rotated token revokes its whole family and returns `401` `invalid-token`. A missing, unknown, expired or revoked token also returns `401` `invalid-token` |
| Logout | Needs a valid access token. Revokes the family of the cookie's refresh token if it belongs to the caller, clears the cookie and returns `204`; a guest's logout only clears the cookie |
| Password change (E-10) | `POST /auth/me/password`; wrong `currentPassword` returns `401` `invalid-credentials`; success revokes all of the user's refresh tokens. Guests get `403` `forbidden`, demo accounts `403` `demo-account-restricted` |
| Guest | `deviceToken` must be a UUID v4, otherwise `400`. Quotas (FR-09) are checked by the analyses endpoint through `GuestQuotaService`: per device and per IP per Asia/Dhaka day (`429` `quota-exceeded` with `retryAfterSeconds` until midnight) and one concurrent analysis (`429` `concurrency-limit`) |
| Demo | `POST /auth/demo/{role}` exists only in the `demo` profile and never with `prod`; elsewhere it is `404` `not-found`. An unknown role is `404`. It ignores the lockout so judges can always enter |
| Credentials in URLs | `access_token`, `token` or `api_key` as a query parameter on any endpoint returns `400` `validation-failed` before authentication; the response never echoes the value |
| Audit | `LOGIN_SUCCESS`, `LOGIN_FAILURE` (reason code only; the attempted e-mail address is never stored), `DEMO_LOGIN`, `ACCESS_DENIED` (role and path) and `ADMIN_ACTION` (every successful write under `/admin/` by an admin) |
| Not yet implemented | `PATCH /auth/me` (FR-56, P7), `DELETE /auth/me` (FR-78, P9) and API keys (P8) |

Non-owners receive `404` (not `403`) for analysis, compare, trace and ticket resources.
Demo accounts receive `403` `demo-account-restricted` on password change and deletion.

## 3. Endpoint catalogue

| # | Method and path | Request | Success | Errors | FR |
| --- | --- | --- | --- | --- | --- |
| E-01 | `GET /api/v1/meta` | none | `200` `Meta` | none | FR-01 |
| E-02 | `GET /actuator/health` | none | `200` `{ status, components: { db, ollama } }` | `503` | FR-04 |
| E-03 | `POST /api/v1/auth/login` | `{ email, password }` | `200` `TokenResponse` + refresh cookie | `400`, `401` `invalid-credentials`, `423` `account-locked`, `429` `rate-limited` | FR-05, FR-07 |
| E-04 | `POST /api/v1/auth/refresh` | refresh cookie | `200` `TokenResponse` + rotated cookie | `401` `invalid-token` | FR-05 |
| E-05 | `POST /api/v1/auth/logout` | refresh cookie | `204` (cookie cleared, token family revoked) | `401` | FR-05 |
| E-06 | `GET /api/v1/auth/me` | none | `200` `Me` | `401` | FR-05 |
| E-07 | `POST /api/v1/auth/guest` | `{ deviceToken: uuid }` | `200` `TokenResponse` (`guest: true`) | `400`, `429` | FR-09 |
| E-08 | `POST /api/v1/auth/demo/{role}` (`worker`, `hr`, `admin`; profile `demo` only) | none | `200` `TokenResponse` + refresh cookie | `404` outside `demo` | FR-08 |
| E-09 | `PATCH /api/v1/auth/me` | `{ profile?: Profile, locale?: bn \| en }` | `200` `Me` | `400`, `403` (guest) | FR-56 |
| E-10 | `POST /api/v1/auth/me/password` | `{ currentPassword, newPassword (12..128) }` | `204` | `400`, `401`, `403` `demo-account-restricted` | FR-08 |
| E-11 | `DELETE /api/v1/auth/me` | none | `204` | `403` `demo-account-restricted` | FR-78 |
| E-12 | `POST /api/v1/analyses` | multipart (§4.1) or JSON `AnalysisTextRequest` | `200` `AnalysisResult` (JSON text ≤ 8,000 characters finished within 60 s) or `202` `AnalysisAccepted` | `400`, `401`, `409`, `413`, `415`, `422`, `429`, `503` | FR-24, FR-58 |
| E-13 | `GET /api/v1/analyses/{id}` | none | `200` `AnalysisResult` (`SUCCEEDED`); `202` `AnalysisStatusView` (`QUEUED`, `RUNNING`); `200` `AnalysisStatusView` with `problem` (`FAILED`) | `404`, `410` `analysis-expired` | FR-54, FR-58 |
| E-14 | `GET /api/v1/analyses/{id}/events` | `Authorization: Bearer`, `Accept: text/event-stream`, optional `Last-Event-ID` | SSE (§4.2) | `404`, `410` | FR-58 |
| E-15 | `GET /api/v1/analyses/{id}/trace` | none | `200` `AnalysisTrace` (30 days) | `404` | FR-48, FR-58 |
| E-16 | `GET /api/v1/analyses/{id}/obligations` | none | `200` `ObligationsExport` | `404`, `410` | FR-53 |
| E-17 | `DELETE /api/v1/analyses/{id}` | none | `204` | `404` | FR-54 |
| E-18 | `POST /api/v1/compare` | same as E-12 | `202` `CompareAccepted` | as E-12 | FR-59 |
| E-19 | `GET /api/v1/compare/{id}` | none | `200` `CompareResult`; `202` while running | `404`, `410` | FR-59 |
| E-20 | `GET /api/v1/compare/{id}/events` | SSE | SSE (§4.2) | `404`, `410` | FR-59 |
| E-21 | `POST /api/v1/memos/render` | `MemoRenderRequest` | `200` `application/pdf` or `text/html; charset=utf-8`; writes a `MEMO_RENDER` usage event | `400` `signature-invalid`, `410` `envelope-expired`, `503` `renderer-unavailable` | FR-55 |
| E-22 | `GET /api/v1/review-tickets/{ticketId}` | header `X-Ticket-Token` | `200` `ReviewTicketStatus` | `404`, `410` `ticket-expired` (after the last of its items expires) | FR-61 |
| E-23 | `GET /api/v1/review-items` | query `status`, `kind`, `clauseType`, `severity`, `page`, `size` | `200` page of `ReviewItem` | `401`, `403` | FR-60 |
| E-24 | `GET /api/v1/review-items/{id}` | none | `200` `ReviewItem` | `403`, `404` | FR-60 |
| E-25 | `PATCH /api/v1/review-items/{id}` | `ReviewItemPatch` | `200` `ReviewItem` (audited) | `409` `already-claimed` / `already-decided`, `422` `citation-not-verifiable` / `invalid-transition` | FR-60 |
| E-26 | `GET /api/v1/playbooks` | `page`, `size` | `200` page of `Playbook` | `403` | FR-57 |
| E-27 | `POST /api/v1/playbooks` | `PlaybookInput` | `201` `Playbook` | `400`, `403`, `422` `playbook-limit-reached` | FR-57 |
| E-28 | `GET /api/v1/playbooks/{id}` | none | `200` `Playbook` | `404` | FR-57 |
| E-29 | `PUT /api/v1/playbooks/{id}` | `PlaybookInput` | `200` `Playbook` (`version` + 1) | `400`, `404` | FR-57 |
| E-30 | `DELETE /api/v1/playbooks/{id}` | none | `204` (archives) | `404` | FR-57 |
| E-31 | `GET /api/v1/laws` | query `jurisdiction` | `200` `ActSummary[]` | `401` | FR-22 |
| E-32 | `GET /api/v1/laws/search` | `q` (1..500), `lang` (`bn`, `en`, `any`), `k` (1..20, default 8), `jurisdiction` | `200` `SearchResults`; local retrieval only, no cloud call and no query rewriting (architecture §8.2, §10.7) | `400`, `401`, `429` | FR-22 |
| E-33 | `GET /api/v1/laws/{act}` | none | `200` `Act` | `404` | FR-22 |
| E-34 | `GET /api/v1/laws/{act}/{section}` | `version` (optional; default current) | `200` `Section` | `404` | FR-22 |
| E-35 | `GET /api/v1/admin/users`, `POST /api/v1/admin/users` | page / `UserInput` | `200` page of `User` / `201` `User` | `400`, `409` `email-taken` | FR-73 |
| E-36 | `PATCH /api/v1/admin/users/{id}`, `DELETE /api/v1/admin/users/{id}` | `{ role?, orgId?, disabled? }` / none | `200` `User` / `204` | `404`, `403` (demo user) | FR-73, FR-78 |
| E-37 | `GET /api/v1/admin/api-keys`, `POST /api/v1/admin/api-keys` | none / `{ orgId, name (1..60), scopes[] }` | `200` `ApiKey[]` / `201` `ApiKeyCreated` (secret shown once) | `400` | FR-65 |
| E-38 | `DELETE /api/v1/admin/api-keys/{id}` | none | `204` (revokes) | `404` | FR-65 |
| E-39 | `GET /api/v1/admin/usage` | `from`, `to` (dates, max 92 days), `orgId` | `200` `UsageReport` | `400` | FR-73 |
| E-40 | `GET /api/v1/admin/traces/{analysisId}` | none | `200` `AnalysisTrace` | `404` | FR-73, FR-82 |
| E-41 | `POST /api/v1/admin/laws/ingest` | `{ acts?: string[] }` | `202` `{ ingestRunId }`; result in the quality report | `409` `ingest-running` | FR-15, FR-84 |
| E-42 | `POST /api/v1/admin/laws/check-hashes` | `{ pages: [{ url, sha256 }] }` | `200` `{ changed: [{ url, storedSha256, newSha256 }], unchanged: int }` | `400` | FR-84 |
| E-43 | `POST /api/v1/admin/laws/retrieval-smoke` | none | `200` `{ hitAt5, mrrAt10, recallAt8, questions, corpusRelease }` from `RetrievalSmokeService` (architecture §8.6); smoke numbers only, never written to `eval/reports/` | `503` (`smoke-questions` resource missing or empty) | FR-84 |
| E-44 | `POST /api/v1/admin/review-items` | `{ kind: CORPUS_CHANGE, sourceUrl, storedSha256, newSha256, ingestRunId }` | `201` `ReviewItem` | `400` | FR-84 |
| E-45 | `GET /api/v1/dev/analyses/{id}/redacted` (profile `dev` only) | none | `200` `{ clauses: [{ id, type, confidence, redactedText, flags }] }` | `404` outside `dev` | FR-33 |

Request and response objects without a full schema in §5:

| Object | Fields |
| --- | --- |
| `Meta` | `name`, `version` (build), `models` `{ reasoning, fast, verifier, verifierFallback, judge, localPii, embedding }` (`verifier` and `judge` are `null` when `GEMINI_MODEL` is unset), `corpus` `{ release, acts, sections, currentSections, embeddedSections, lastRefreshAt }`, `audit` `{ privacyBlocksLast7Days, openReviewItems, latestEval: { reportDate, cases, citationPrecision, fabricatedCitationsShown, riskRecall } \| null }` |
| `TokenResponse` | `accessToken`, `expiresIn` (seconds), `tokenType` (`Bearer`), `guest` (boolean), `user` (`Me` \| `null` for guests) |
| `Me` | `userId`, `email`, `role` (`Role`), `org` `{ id, name, kind, plan }` \| `null`, `locale`, `profile` (`Profile`), `demo` (boolean) For a guest, `userId`, `email`, `org` and `profile` are `null`, `role` is `WORKER` and `locale` is `bn`. `profile` is also `null` for an account that has not set one (FR-56, P7) |
| `LoginRequest` | `email` (valid address, ≤ 254), `password` (1..128) |
| `GuestRequest` | `deviceToken` (UUID v4, generated by the browser and kept in `localStorage`) |
| `PasswordChangeRequest` | `currentPassword` (1..128), `newPassword` (12..128) |
| `Profile` | `side` (`Side`), `sector` (`Sector`), `jobRole` (string ≤ 60 \| `null`), `preferredLanguage` (`bn` \| `en`), `readingLevel` (`BASIC` \| `STANDARD`) |
| `AnalysisTextRequest` | `text` (200..60,000), `side`, `outputLanguage`, `profile` (\| `null`), `playbookId` (\| `null`, HR only), `workerCategory` (corpus code \| `null`) |
| `AnalysisAccepted` | `analysisId`, `status` (`QUEUED`), `eventsUrl`, `resultUrl`, `deadlineAt` (created + 120 s) |
| `AnalysisStatusView` | `analysisId`, `status`, `stage` (`Stage` \| `null`), `progress` (0..100), `problem` (`Problem` \| `null`) |
| `AnalysisTrace` | `analysisId`, `createdAt`, `events[]` `{ stage, at, durationMs, promptId, promptVersion, modelId, tokensIn, tokensOut, cacheReadTokens, costUsdMicros, outcome, detail }`; `detail` holds only section keys, scores, statuses, reason codes, flags and counts |
| `ObligationsExport` | `analysisId`, `parties` `{ EMPLOYER: ObligationItem[], WORKER: ObligationItem[], BOTH: ObligationItem[] }`, `disclaimer` |
| `CompareAccepted` | `compareId`, `eventsUrl`, `resultUrl` |
| `CompareResult` | `compareId`, `baseline` `{ modelId, findings[] { title, severity, explanation, citations[] { claimed: ClaimedCitation, verification: VerificationResult } } }`, `shromikai` (`AnalysisResult`), `summary` `{ baselineCitations, baselineRejected, baselineUncertain, baselineVerified, shromikaiShownCitations }`, `disclaimer` |
| `MemoRenderRequest` | `envelope` (`SignedEnvelope`), `format` (`PDF` \| `HTML`) |
| `SignedEnvelope` | `payload` (the `AnalysisResult` **as returned to the owning subject**, that is re-identified, without its `envelope` field), `keyId`, `algorithm` (`HMAC-SHA256`), `expiresAt`, `signature` (base64url). Clients build it from the `AnalysisResult` they received: `payload` is that result with the `envelope` property removed and nothing else changed; the other four fields are copied from `envelope`. The server signs the RFC 8785 canonical form, so property order and whitespace do not matter (architecture §7.7) |
| `ReviewTicketStatus` | `ticketId`, `expiresAt` (the latest `expiresAt` of its items), `items[]` `{ findingId, title, status (ReviewStatus), decision (ReviewDecision \| null), citation (VerifiedCitation \| null), note (≤ 1000 \| null), decidedAt \| null }` |
| `ReviewItemPatch` | one of: `{ status: IN_REVIEW }` (claim), `{ status: OPEN }` (release), `{ decision: APPROVED_WITH_CITATION, sectionKey, quote (20..300), claim (20..600), note? }`, `{ decision: APPROVED_WITHOUT_CITATION, note }`, `{ decision: REJECTED, note }` |
| `PlaybookInput` | `name` (2..80), `side`, `rules[]` (1..50) `{ clauseType, fact (FactParameter), comparator, value (number \| null; null only for REQUIRED, PROHIBITED), unit (allowed for the fact, §6.4), severity, messageBn (1..300), messageEn (1..300), statuteSectionKey (\| null) }` |
| `Playbook` | `PlaybookInput` plus `id`, `orgId`, `version`, `createdAt`, `archivedAt` |
| `UserInput` | `email`, `password` (12..128), `role`, `orgId` (\| `null`), `locale` |
| `User` | `id`, `email`, `role`, `orgId`, `locale`, `demo`, `createdAt`, `disabledAt`, `lockedUntil` |
| `ApiKey` | `id`, `orgId`, `name`, `prefix`, `scopes[]`, `createdAt`, `lastUsedAt`, `revokedAt` |
| `ApiKeyCreated` | `ApiKey` plus `key` (returned only here) |
| `UsageReport` | `from`, `to`, `orgId` (\| `null`), `days[]` `{ date, analyses, compares, memoRenders, apiCalls, mcpCalls, costUsd, costByModel: { modelId: number } }`, `totals`, `kpis` `{ windowDays: 7, completionRate: { value, count, n }, memoDownloadRate: { value, count, n }, reviewTurnaroundHoursMedian: { value, n } }` (KPI-12 to KPI-14, PRD §10; computed over the 7 days ending at `to` from `analysis_run`, `usage_event` and `review_item`; `null` values when `n = 0`) |
| `ActSummary` | `actCode`, `titleBn`, `titleEn`, `year`, `jurisdiction`, `authoritativeLang`, `status`, `ratificationStatus` (ILO only), `sourceUrl`, `sectionCount` |
| `Act` | `ActSummary` plus `sections[]` `{ sectionKey, sectionNo, sectionNoOriginal, headingBn, headingEn }` |
| `Section` | `sectionKey`, `actCode`, `actTitleBn`, `actTitleEn`, `sectionNo`, `sectionNoOriginal`, `headingBn`, `headingEn`, `authoritativeLang`, `authoritativeText`, `translation` `{ lang, text, official }` \| `null`, `versionLabel`, `effectiveFrom`, `isCurrent`, `sourceUrl`, `fetchedAt`, `contentHash`, `related[]` `{ sectionKey, kind, reviewed }`, `corpusRelease` |
| `SearchResults` | `query`, `corpusRelease`, `results[]` `RetrievalResult` (free-text search does not rewrite queries) |
| `RetrievalResult` | `sectionKey`, `actCode`, `sectionNo`, `headingBn`, `headingEn`, `snippet` (≤ 300), `sourceUrl`, `vectorRank` (\| `null`), `textRank` (\| `null`), `fusedScore`, `origin` (`SEARCH`, `GRAPH`, `TOPIC_MAP`) |

## 4. Analysis lifecycle

### 4.1 `POST /api/v1/analyses` multipart request

Exactly one content part plus `options`, or `options` alone with `"sample": true` (no content
part; any content part sent with `sample: true` is rejected with `400`):

| Part | Type | Constraint |
| --- | --- | --- |
| `file` | `application/pdf` (magic bytes `%PDF-`) | One part; ≤ 10 MB; ≤ 15 pages |
| `images` | `image/jpeg` (FF D8 FF) or `image/png` (89 50 4E 47) | 1 to 15 parts in page order; ≤ 10 MB in total; each ≤ 6000 × 6000 px |
| `text` | `text/plain; charset=utf-8` | 200 to 60,000 characters |
| `options` (required) | `application/json` | `AnalysisOptions` |

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:analysis-options:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["side", "outputLanguage"],
  "properties": {
    "side": { "enum": ["WORKER", "EMPLOYER"] },
    "outputLanguage": { "enum": ["bn", "en"] },
    "profile": {
      "type": ["object", "null"],
      "additionalProperties": false,
      "properties": {
        "sector": { "$ref": "#/$defs/sector" },
        "jobRole": { "type": ["string", "null"], "maxLength": 60 },
        "preferredLanguage": { "enum": ["bn", "en"] },
        "readingLevel": { "enum": ["BASIC", "STANDARD"] }
      }
    },
    "playbookId": { "type": ["string", "null"], "format": "uuid" },
    "workerCategory": { "type": ["string", "null"], "maxLength": 40 },
    "clientKind": { "enum": ["MOBILE_WEB", "DESKTOP_WEB", null] },
    "sample": { "type": "boolean", "default": false, "description": "Run the bundled synthetic sample contract instead of an upload; the request then has no content part, and the result sets input.sample = true and names the sample as synthetic" }
  },
  "$defs": {
    "sector": { "enum": ["READY_MADE_GARMENTS", "TEXTILES", "CONSTRUCTION", "IT_SOFTWARE", "RETAIL", "DOMESTIC_WORK", "SECURITY_SERVICES", "NGO", "BANKING_FINANCE", "OTHER"] }
  }
}
```

`playbookId` requires an `HR` or `ADMIN` user of the playbook's organisation; otherwise `403`.

### 4.2 Server-sent events

- **Authentication:** the same `Authorization: Bearer` header as every other endpoint (user, guest or API key token); no query-string tokens (§2). Browsers use the project's fetch-based SSE client (architecture §17), not `EventSource`. A missing or invalid token returns `401` before the stream opens; another subject's job returns `404`. When the token expires during an open stream, the stream continues until it closes; the next reconnect must carry a fresh token.
- Each event has an integer `id`, increasing by 1 per job, starting at 1. Reconnecting with `Last-Event-ID: n` replays events `n+1` onward from the in-memory job.
- A comment line `: keep-alive` is sent every 15 seconds; the stream closes after `completed` or `failed`.

| `event` | `data` (JSON) |
| --- | --- |
| `stage` | `{ "analysisId": "uuid", "stage": "Stage", "state": "STARTED" \| "COMPLETED" \| "SKIPPED", "progress": 0..100, "message": "localised progress message", "at": "time" }` |
| `warning` | `{ "code": "WarningCode", "message": "localised text" }` |
| `completed` | `{ "analysisId": "uuid", "resultUrl": "...", "findingCount": int, "uncertainCount": int }` |
| `failed` | `Problem` |

Progress values sent with `COMPLETED`: `INTAKE` 5, `OCR` 15, `REDACT` 25, `CLASSIFY` 35,
`RETRIEVE` 45, `RULES` 50, `RISK` 65, `VERIFY` 80, `CORRECT` 85, `COMPOSE` 95, `COMPLETE` 100.
Bangla messages (`Accept-Language: bn`): `INTAKE` "চুক্তি পড়া হচ্ছে", `OCR` "ছবি থেকে লেখা বের করা হচ্ছে",
`REDACT` "ব্যক্তিগত তথ্য সরানো হচ্ছে", `CLASSIFY` "ধারাগুলো চেনা হচ্ছে", `RETRIEVE` "প্রাসঙ্গিক আইন খোঁজা হচ্ছে",
`RULES` "নিয়ম মিলিয়ে দেখা হচ্ছে", `RISK` "ঝুঁকি বিশ্লেষণ হচ্ছে", `VERIFY` "আইনের উদ্ধৃতি যাচাই হচ্ছে",
`CORRECT` "অনিশ্চিত উদ্ধৃতি আবার খোঁজা হচ্ছে", `COMPOSE` "মেমো তৈরি হচ্ছে", `COMPLETE` "সম্পন্ন".

## 5. Schemas

### 5.1 `ClauseType`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:clause-type:1",
  "enum": [
    "APPOINTMENT_AND_ROLE", "PROBATION", "WAGES", "WAGE_PAYMENT_AND_DEDUCTIONS",
    "WORKING_HOURS", "OVERTIME", "REST_AND_HOLIDAYS", "LEAVE", "MATERNITY_BENEFIT",
    "NOTICE_AND_RESIGNATION", "TERMINATION", "DISCIPLINE_AND_MISCONDUCT", "SERVICE_BENEFITS",
    "TRANSFER_AND_DEPLOYMENT", "CONFIDENTIALITY", "NON_COMPETE", "INTELLECTUAL_PROPERTY",
    "HEALTH_AND_SAFETY", "DISPUTE_RESOLUTION", "GOVERNING_LAW", "GENERAL_PROVISIONS", "OTHER"
  ]
}
```

| Value | Bangla | English |
| --- | --- | --- |
| `APPOINTMENT_AND_ROLE` | নিয়োগ ও পদ | Appointment and role |
| `PROBATION` | শিক্ষানবিশকাল | Probation |
| `WAGES` | মজুরি | Wages |
| `WAGE_PAYMENT_AND_DEDUCTIONS` | মজুরি পরিশোধ ও কর্তন | Wage payment and deductions |
| `WORKING_HOURS` | কর্মঘণ্টা | Working hours |
| `OVERTIME` | ওভারটাইম (অধিকাল) | Overtime |
| `REST_AND_HOLIDAYS` | বিশ্রাম ও ছুটির দিন | Rest and holidays |
| `LEAVE` | ছুটি | Leave |
| `MATERNITY_BENEFIT` | প্রসূতি কল্যাণ | Maternity benefit |
| `NOTICE_AND_RESIGNATION` | নোটিশ ও পদত্যাগ | Notice and resignation |
| `TERMINATION` | চাকরি অবসান | Termination |
| `DISCIPLINE_AND_MISCONDUCT` | শৃঙ্খলা ও অসদাচরণ | Discipline and misconduct |
| `SERVICE_BENEFITS` | চাকরি-পরবর্তী সুবিধা | Service benefits |
| `TRANSFER_AND_DEPLOYMENT` | বদলি ও পদায়ন | Transfer and deployment |
| `CONFIDENTIALITY` | গোপনীয়তা | Confidentiality |
| `NON_COMPETE` | প্রতিযোগিতা-নিষেধ | Non-compete |
| `INTELLECTUAL_PROPERTY` | মেধাস্বত্ব | Intellectual property |
| `HEALTH_AND_SAFETY` | স্বাস্থ্য ও নিরাপত্তা | Health and safety |
| `DISPUTE_RESOLUTION` | বিরোধ নিষ্পত্তি | Dispute resolution |
| `GOVERNING_LAW` | প্রযোজ্য আইন | Governing law |
| `GENERAL_PROVISIONS` | সাধারণ বিধান | General provisions |
| `OTHER` | অন্যান্য | Other |

### 5.2 `ClauseFact`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:clause-fact:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["parameter", "value", "unit", "confidence", "regexAgrees"],
  "properties": {
    "parameter": { "$ref": "urn:shromikai:schema:fact-parameter:1" },
    "value": { "type": ["number", "null"], "description": "null only when the clause mentions the parameter without a value; booleans are 0 or 1" },
    "unit": { "$ref": "urn:shromikai:schema:unit:1" },
    "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
    "regexAgrees": { "type": ["boolean", "null"], "description": "null when no regex extractor applies (FR-35)" }
  }
}
```

### 5.3 `Clause`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:clause:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "ordinal", "label", "type", "typeConfidence", "secondaryTypes", "heading", "text", "paragraphIds", "pages", "language", "facts", "flags", "segmentation"],
  "properties": {
    "id": { "type": "string", "pattern": "^c[0-9]{1,3}$" },
    "ordinal": { "type": "integer", "minimum": 1 },
    "label": { "type": ["string", "null"], "maxLength": 40, "description": "Clause number as printed, for example ১. or (a)" },
    "type": { "$ref": "urn:shromikai:schema:clause-type:1" },
    "typeConfidence": { "type": "number", "minimum": 0, "maximum": 1 },
    "secondaryTypes": { "type": "array", "maxItems": 3, "items": { "$ref": "urn:shromikai:schema:clause-type:1" } },
    "heading": { "type": ["string", "null"], "maxLength": 200 },
    "text": { "type": "string", "maxLength": 8000, "description": "Re-identified clause text, only in responses to the owning subject; never persisted (R-02)" },
    "paragraphIds": { "type": "array", "minItems": 1, "items": { "type": "string", "pattern": "^p[0-9]{1,4}$" } },
    "pages": { "type": "array", "items": { "type": "integer", "minimum": 1 } },
    "language": { "enum": ["bn", "en", "mixed"] },
    "facts": { "type": "array", "maxItems": 20, "items": { "$ref": "urn:shromikai:schema:clause-fact:1" } },
    "flags": { "type": "array", "items": { "enum": ["INSTRUCTION_LIKE_TEXT", "LOW_OCR_CONFIDENCE", "LEGACY_FONT_CONVERTED"] } },
    "segmentation": { "enum": ["RULE_BASED", "LLM_FALLBACK"] }
  }
}
```

### 5.4 `ClaimedCitation`

Internal: produced by the risk agent, rules or reviewers; appears in the trace (section key and
statuses only), in review items and in compare mode's baseline column. Never shown to users as a
citation in ShromikAI's own results.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:claimed-citation:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["citationId", "sectionKey", "actCode", "sectionNo", "quote", "claim", "claimLanguage", "origin", "ruleId"],
  "properties": {
    "citationId": { "type": "string", "format": "uuid" },
    "sectionKey": { "$ref": "#/$defs/sectionKey" },
    "actCode": { "type": "string", "maxLength": 32 },
    "sectionNo": { "type": "string", "maxLength": 32 },
    "quote": { "type": "string", "minLength": 20, "maxLength": 300, "description": "Copied from the section's authoritative text" },
    "claim": { "type": "string", "minLength": 20, "maxLength": 600, "description": "Proposition the section is cited for; placeholders instead of PII" },
    "claimLanguage": { "enum": ["bn", "en"] },
    "origin": { "enum": ["LLM", "RULE", "MAPPING", "REVIEWER", "EXTERNAL", "BASELINE"], "description": "architecture §7.1" },
    "ruleId": { "type": ["string", "null"], "maxLength": 64 }
  },
  "$defs": {
    "sectionKey": {
      "type": "string",
      "pattern": "^[A-Za-z][A-Za-z0-9-]{1,31}:[0-9A-Za-z().-]{1,32}@[a-z0-9-]{1,32}$",
      "description": "{actCode}:{sectionNo}@{versionLabel}; section numbers in Latin digits"
    }
  }
}
```

In the `risk-analysis` and `risk-citation-repair` LLM output schemas, `sectionKey` is replaced per
request by `{ "enum": [<candidate section keys>] }` (architecture §8.3). Baseline citations
(`origin: BASELINE`) carry whatever the plain LLM produced, mapped to a section key when parsable.

### 5.5 `VerificationStatus`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:verification-status:1",
  "enum": ["VERIFIED", "UNCERTAIN", "REJECTED"]
}
```

| Value | Meaning | Shown to users |
| --- | --- | --- |
| `VERIFIED` | Existence, quote match and support all passed | As a citation with ✓ যাচাইকৃত / Verified |
| `UNCERTAIN` | Existence and quote passed; support was `PARTIAL`, low-confidence, or could not be judged | Never as a citation; the finding shows ? অনিশ্চিত / Uncertain |
| `REJECTED` | Section missing, not current, outside the candidates, misquoted, contradicted or unrelated | Never in ShromikAI results; in compare mode the baseline's rejected citations show ✗ বাতিল / Rejected with the reason |

### 5.6 `VerificationResult`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:verification-result:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["citationId", "sectionKey", "status", "attempt", "checks", "reasonCodes", "verifiedAt", "corpusRelease"],
  "properties": {
    "citationId": { "type": "string", "format": "uuid" },
    "sectionKey": { "type": "string" },
    "status": { "$ref": "urn:shromikai:schema:verification-status:1" },
    "attempt": { "type": "integer", "enum": [1, 2] },
    "checks": {
      "type": "object",
      "additionalProperties": false,
      "required": ["existence", "quoteMatch", "support"],
      "properties": {
        "existence": {
          "type": "object", "additionalProperties": false, "required": ["outcome"],
          "properties": { "outcome": { "$ref": "#/$defs/outcome" } }
        },
        "quoteMatch": {
          "type": "object", "additionalProperties": false, "required": ["outcome", "similarity", "matchedLanguage"],
          "properties": {
            "outcome": { "$ref": "#/$defs/outcome" },
            "similarity": { "type": ["number", "null"], "minimum": 0, "maximum": 1 },
            "matchedLanguage": { "enum": ["bn", "en", null] }
          }
        },
        "support": {
          "type": "object", "additionalProperties": false, "required": ["outcome", "label", "confidence", "independence", "modelId", "reason"],
          "properties": {
            "outcome": { "$ref": "#/$defs/outcome" },
            "label": { "enum": ["SUPPORTS", "PARTIAL", "CONTRADICTS", "UNRELATED", null] },
            "confidence": { "type": ["number", "null"], "minimum": 0, "maximum": 1 },
            "independence": { "enum": ["DIFFERENT_FAMILY", "SAME_FAMILY", "RULE_PROVENANCE", "HUMAN", null] },
            "modelId": { "type": ["string", "null"] },
            "reason": { "type": ["string", "null"], "maxLength": 200 }
          }
        }
      }
    },
    "reasonCodes": { "type": "array", "items": { "$ref": "#/$defs/reasonCode" } },
    "verifiedAt": { "type": "string", "format": "date-time" },
    "corpusRelease": { "type": "string" }
  },
  "$defs": {
    "outcome": { "enum": ["PASS", "FAIL", "SKIPPED", "ERROR"] },
    "reasonCode": {
      "enum": [
        "SECTION_NOT_FOUND", "ACT_MISMATCH", "SECTION_NOT_CURRENT", "ACT_NOT_IN_PACK", "NOT_IN_CANDIDATES",
        "QUOTE_TOO_SHORT", "QUOTE_TOO_LONG", "QUOTE_NOT_FOUND", "QUOTE_BELOW_THRESHOLD", "QUOTE_WRONG_LANGUAGE",
        "SUPPORT_PARTIAL", "SUPPORT_LOW_CONFIDENCE", "SUPPORT_CONTRADICTS", "SUPPORT_UNRELATED",
        "VERIFIER_UNAVAILABLE"
      ]
    }
  }
}
```

### 5.7 `VerifiedCitation`

The only citation shape in ShromikAI's user-facing outputs. Constructed only by module `verify`.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:verified-citation:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["citationId", "sectionKey", "actCode", "actTitleBn", "actTitleEn", "sectionNo", "headingBn", "headingEn", "quote", "quoteLanguage", "translation", "sourceUrl", "versionLabel", "effectiveFrom", "verification"],
  "properties": {
    "citationId": { "type": "string", "format": "uuid" },
    "sectionKey": { "type": "string" },
    "actCode": { "type": "string" },
    "actTitleBn": { "type": ["string", "null"] },
    "actTitleEn": { "type": ["string", "null"] },
    "sectionNo": { "type": "string" },
    "headingBn": { "type": ["string", "null"] },
    "headingEn": { "type": ["string", "null"] },
    "quote": { "type": "string", "description": "Exact text of the authoritative version as stored in the corpus" },
    "quoteLanguage": { "enum": ["bn", "en"] },
    "translation": {
      "type": ["object", "null"],
      "additionalProperties": false,
      "required": ["lang", "text", "official"],
      "properties": {
        "lang": { "enum": ["bn", "en"] },
        "text": { "type": "string" },
        "official": { "type": "boolean" }
      }
    },
    "sourceUrl": { "type": "string", "format": "uri" },
    "versionLabel": { "type": "string" },
    "effectiveFrom": { "type": ["string", "null"], "format": "date" },
    "verification": {
      "allOf": [
        { "$ref": "urn:shromikai:schema:verification-result:1" },
        { "properties": { "status": { "const": "VERIFIED" } } }
      ]
    }
  }
}
```

### 5.8 `RiskFinding`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:risk-finding:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "title", "kind", "severity", "nonObvious", "source", "clauseType", "clauseIds", "explanation", "whyItMatters", "sideNote", "verificationStatus", "citations", "iloReferences", "ruleId", "playbookRuleId", "redlineId", "reviewItemId", "confidence"],
  "properties": {
    "id": { "type": "string", "pattern": "^f[0-9]{1,3}$" },
    "title": { "type": "string", "minLength": 5, "maxLength": 120 },
    "kind": { "enum": ["CONFLICT", "OMISSION", "AMBIGUITY", "INTERACTION", "POLICY_DEVIATION"] },
    "severity": { "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW"] },
    "nonObvious": { "type": "boolean" },
    "source": { "enum": ["RULE_ENGINE", "LLM_ANALYSIS", "PLAYBOOK"] },
    "clauseType": { "$ref": "urn:shromikai:schema:clause-type:1" },
    "clauseIds": { "type": "array", "maxItems": 5, "items": { "type": "string", "pattern": "^c[0-9]{1,3}$" } },
    "explanation": { "type": "string", "minLength": 20, "maxLength": 1200, "description": "Short user-facing rationale in plain language, in outputLanguage; no reasoning dump" },
    "whyItMatters": { "type": "string", "maxLength": 600 },
    "sideNote": { "type": ["string", "null"], "maxLength": 400 },
    "verificationStatus": { "enum": ["VERIFIED", "UNCERTAIN", "POLICY_ONLY"] },
    "citations": { "type": "array", "maxItems": 4, "items": { "$ref": "urn:shromikai:schema:verified-citation:1" } },
    "iloReferences": { "type": "array", "maxItems": 2, "items": { "$ref": "urn:shromikai:schema:verified-citation:1" } },
    "ruleId": { "type": ["string", "null"] },
    "playbookRuleId": { "type": ["string", "null"], "format": "uuid" },
    "redlineId": { "type": ["string", "null"], "pattern": "^r[0-9]{1,3}$" },
    "reviewItemId": { "type": ["string", "null"], "format": "uuid" },
    "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
  },
  "allOf": [
    { "if": { "properties": { "verificationStatus": { "const": "VERIFIED" } } }, "then": { "properties": { "citations": { "minItems": 1 } } } },
    { "if": { "properties": { "verificationStatus": { "const": "UNCERTAIN" } } }, "then": { "properties": { "citations": { "maxItems": 0 }, "reviewItemId": { "type": "string" } } } },
    { "if": { "properties": { "kind": { "not": { "const": "OMISSION" } } } }, "then": { "properties": { "clauseIds": { "minItems": 1 } } } }
  ]
}
```

### 5.9 `Redline`

Built from the counter-offer agent's proposal and a java-diff-utils word-level diff.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:redline:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "findingIds", "clauseId", "action", "originalText", "proposedText", "diff", "rationale", "citations", "ruleCheck", "language"],
  "properties": {
    "id": { "type": "string", "pattern": "^r[0-9]{1,3}$" },
    "findingIds": { "type": "array", "minItems": 1, "items": { "type": "string", "pattern": "^f[0-9]{1,3}$" } },
    "clauseId": { "type": ["string", "null"], "description": "null for INSERT of a missing clause" },
    "action": { "enum": ["REPLACE", "INSERT", "DELETE"] },
    "originalText": { "type": ["string", "null"], "maxLength": 8000 },
    "proposedText": { "type": ["string", "null"], "maxLength": 4000, "description": "null only for DELETE" },
    "diff": {
      "type": "array",
      "items": {
        "type": "object", "additionalProperties": false, "required": ["op", "text"],
        "properties": { "op": { "enum": ["EQUAL", "INSERT", "DELETE"] }, "text": { "type": "string" } }
      }
    },
    "rationale": { "type": "string", "maxLength": 600, "description": "Why the revision resolves the risk and stays reasonable for both sides" },
    "citations": { "type": "array", "items": { "$ref": "urn:shromikai:schema:verified-citation:1" } },
    "ruleCheck": {
      "type": "object", "additionalProperties": false, "required": ["passed", "ruleIds", "attempts"],
      "properties": {
        "passed": { "const": true },
        "ruleIds": { "type": "array", "items": { "type": "string" } },
        "attempts": { "type": "integer", "enum": [1, 2] }
      }
    },
    "language": { "enum": ["bn", "en"] }
  }
}
```

### 5.10 `ObligationItem`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:obligation-item:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "party", "obligation", "trigger", "deadline", "source", "status", "clauseIds", "citations"],
  "properties": {
    "id": { "type": "string", "pattern": "^o[0-9]{1,3}$" },
    "party": { "enum": ["EMPLOYER", "WORKER", "BOTH"] },
    "obligation": { "type": "string", "minLength": 5, "maxLength": 300 },
    "trigger": { "type": ["string", "null"], "maxLength": 200 },
    "deadline": {
      "type": ["object", "null"], "additionalProperties": false, "required": ["value", "unit"],
      "properties": { "value": { "type": "number", "minimum": 0 }, "unit": { "enum": ["DAYS", "WEEKS", "MONTHS"] } }
    },
    "source": { "enum": ["CONTRACT", "STATUTE", "BOTH"] },
    "status": { "enum": ["STATED_IN_CONTRACT", "STATUTORY_NOT_IN_CONTRACT", "CONFLICTS_WITH_CONTRACT"] },
    "clauseIds": { "type": "array", "items": { "type": "string", "pattern": "^c[0-9]{1,3}$" } },
    "citations": { "type": "array", "items": { "$ref": "urn:shromikai:schema:verified-citation:1" } }
  },
  "allOf": [
    { "if": { "properties": { "source": { "enum": ["STATUTE", "BOTH"] } } }, "then": { "properties": { "citations": { "minItems": 1 } } } }
  ]
}
```

### 5.11 `Memo`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:memo:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "analysisId", "language", "readingLevel", "title", "generatedAt", "sections", "verificationSummary", "disclaimer", "footer"],
  "properties": {
    "id": { "type": "string", "format": "uuid" },
    "analysisId": { "type": "string", "format": "uuid" },
    "language": { "enum": ["bn", "en"] },
    "readingLevel": { "enum": ["BASIC", "STANDARD"] },
    "title": { "type": "string", "maxLength": 160 },
    "generatedAt": { "type": "string", "format": "date-time" },
    "sections": {
      "type": "array",
      "minItems": 8,
      "items": {
        "type": "object", "additionalProperties": false, "required": ["kind", "heading", "blocks"],
        "properties": {
          "kind": { "enum": ["SUMMARY", "RISK_TABLE", "RISK_DETAILS", "REVISED_CLAUSES", "OBLIGATIONS", "NEXT_STEPS", "VERIFICATION_AND_METHOD", "DISCLAIMER"] },
          "heading": { "type": "string", "maxLength": 120 },
          "blocks": {
            "type": "array",
            "items": {
              "oneOf": [
                { "type": "object", "additionalProperties": false, "required": ["type", "text"], "properties": { "type": { "const": "paragraph" }, "text": { "type": "string", "maxLength": 2000 } } },
                { "type": "object", "additionalProperties": false, "required": ["type", "items"], "properties": { "type": { "const": "list" }, "items": { "type": "array", "items": { "type": "string", "maxLength": 400 } } } },
                { "type": "object", "additionalProperties": false, "required": ["type", "findingIds"], "properties": { "type": { "const": "riskTable" }, "findingIds": { "type": "array", "items": { "type": "string" } } } },
                { "type": "object", "additionalProperties": false, "required": ["type", "findingId"], "properties": { "type": { "const": "findingRef" }, "findingId": { "type": "string" } } },
                { "type": "object", "additionalProperties": false, "required": ["type", "citationId"], "properties": { "type": { "const": "citationChip" }, "citationId": { "type": "string" } } },
                { "type": "object", "additionalProperties": false, "required": ["type", "redlineId"], "properties": { "type": { "const": "redlineRef" }, "redlineId": { "type": "string" } } },
                { "type": "object", "additionalProperties": false, "required": ["type", "obligationIds"], "properties": { "type": { "const": "obligationTable" }, "obligationIds": { "type": "array", "items": { "type": "string" } } } }
              ]
            }
          }
        }
      }
    },
    "verificationSummary": {
      "type": "object", "additionalProperties": false,
      "required": ["verifiedCitations", "uncertainFindings", "policyFindings", "independence"],
      "properties": {
        "verifiedCitations": { "type": "integer", "minimum": 0 },
        "uncertainFindings": { "type": "integer", "minimum": 0 },
        "policyFindings": { "type": "integer", "minimum": 0 },
        "independence": { "enum": ["DIFFERENT_FAMILY", "SAME_FAMILY", "MIXED", "NONE"] }
      }
    },
    "disclaimer": { "type": "string", "minLength": 20 },
    "footer": {
      "type": "object", "additionalProperties": false,
      "required": ["analysisId", "corpusRelease", "pipelineVersion", "keyId"],
      "properties": {
        "analysisId": { "type": "string" },
        "corpusRelease": { "type": "string" },
        "pipelineVersion": { "type": "string" },
        "keyId": { "type": "string" }
      }
    }
  }
}
```

A `citationChip` may reference only a `VerifiedCitation` present in the result
(`MemoComposerContractTest`).

### 5.12 `AnalysisResult`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:analysis-result:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["analysisId", "status", "createdAt", "completedAt", "expiresAt", "side", "outputLanguage", "profile", "input", "documentType", "clauses", "findings", "redlines", "obligations", "memo", "summary", "reviewTicket", "warnings", "provenance", "costUsd", "disclaimer", "envelope"],
  "properties": {
    "analysisId": { "type": "string", "format": "uuid" },
    "status": { "const": "SUCCEEDED" },
    "createdAt": { "type": "string", "format": "date-time" },
    "completedAt": { "type": "string", "format": "date-time" },
    "expiresAt": { "type": "string", "format": "date-time", "description": "completedAt + 30 minutes" },
    "side": { "enum": ["WORKER", "EMPLOYER"] },
    "outputLanguage": { "enum": ["bn", "en"] },
    "profile": { "type": ["object", "null"] },
    "input": {
      "type": "object", "additionalProperties": false,
      "required": ["kind", "pageCount", "detectedLanguage", "ocrMeanConfidence", "legacyFontConverted", "sample"],
      "properties": {
        "kind": { "enum": ["PDF_TEXT", "PDF_SCAN", "IMAGE", "TEXT"] },
        "pageCount": { "type": "integer", "minimum": 1, "maximum": 15 },
        "detectedLanguage": { "enum": ["bn", "en", "mixed"] },
        "ocrMeanConfidence": { "type": ["number", "null"], "minimum": 0, "maximum": 100 },
        "legacyFontConverted": { "type": "boolean" },
        "sample": { "type": "boolean", "description": "true when the bundled synthetic sample contract was analysed; the UI then labels the result 'synthetic sample'" }
      }
    },
    "documentType": { "const": "EMPLOYMENT_CONTRACT" },
    "clauses": { "type": "array", "items": { "$ref": "urn:shromikai:schema:clause:1" } },
    "findings": { "type": "array", "items": { "$ref": "urn:shromikai:schema:risk-finding:1" } },
    "redlines": { "type": "array", "items": { "$ref": "urn:shromikai:schema:redline:1" } },
    "obligations": { "type": "array", "items": { "$ref": "urn:shromikai:schema:obligation-item:1" } },
    "memo": { "$ref": "urn:shromikai:schema:memo:1" },
    "summary": {
      "type": "object", "additionalProperties": false,
      "required": ["overallSeverity", "findingsBySeverity", "nonObviousFindings", "verifiedFindings", "uncertainFindings", "policyFindings", "verifiedCitations", "coverageNote"],
      "properties": {
        "overallSeverity": { "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW", null] },
        "findingsBySeverity": {
          "type": "object", "additionalProperties": false, "required": ["CRITICAL", "HIGH", "MEDIUM", "LOW"],
          "properties": {
            "CRITICAL": { "type": "integer", "minimum": 0 },
            "HIGH": { "type": "integer", "minimum": 0 },
            "MEDIUM": { "type": "integer", "minimum": 0 },
            "LOW": { "type": "integer", "minimum": 0 }
          }
        },
        "nonObviousFindings": { "type": "integer", "minimum": 0 },
        "verifiedFindings": { "type": "integer", "minimum": 0 },
        "uncertainFindings": { "type": "integer", "minimum": 0 },
        "policyFindings": { "type": "integer", "minimum": 0 },
        "verifiedCitations": { "type": "integer", "minimum": 0 },
        "coverageNote": { "type": ["string", "null"], "maxLength": 400, "description": "Required when fewer than three findings were found (FR-38)" }
      }
    },
    "reviewTicket": {
      "type": ["object", "null"], "additionalProperties": false, "required": ["ticketId", "token", "expiresAt"],
      "properties": {
        "ticketId": { "type": "string", "format": "uuid" },
        "token": { "type": "string", "minLength": 32, "maxLength": 64, "description": "Shown once; stored only as SHA-256" },
        "expiresAt": { "type": "string", "format": "date-time" }
      }
    },
    "warnings": {
      "type": "array",
      "items": {
        "type": "object", "additionalProperties": false, "required": ["code", "message"],
        "properties": {
          "code": { "enum": ["counter-offer-dropped", "low-ocr-confidence-page", "retrieval-keyword-only", "verifier-same-family", "fewer-than-three-findings", "translation-unofficial", "legacy-font-converted", "instruction-like-text"] },
          "message": { "type": "string", "maxLength": 300 }
        }
      }
    },
    "provenance": {
      "type": "object", "additionalProperties": false,
      "required": ["pipelineVersion", "corpusRelease", "promptVersions", "models", "verifierIndependence", "recorded"],
      "properties": {
        "pipelineVersion": { "type": "string" },
        "corpusRelease": { "type": "string" },
        "promptVersions": { "type": "object", "additionalProperties": { "type": "integer", "minimum": 1 } },
        "models": { "type": "object", "additionalProperties": { "type": "string" }, "description": "role → model ID actually used, including refusal fallbacks" },
        "verifierIndependence": { "enum": ["DIFFERENT_FAMILY", "SAME_FAMILY", "MIXED", "NONE"] },
        "recorded": { "type": "boolean", "description": "true in the fake-models profile; the UI then shows 'recorded'" }
      }
    },
    "costUsd": { "type": "number", "minimum": 0 },
    "disclaimer": { "type": "string", "minLength": 20 },
    "envelope": {
      "type": "object", "additionalProperties": false, "required": ["keyId", "algorithm", "expiresAt", "signature"],
      "properties": {
        "keyId": { "type": "string" },
        "algorithm": { "const": "HMAC-SHA256" },
        "expiresAt": { "type": "string", "format": "date-time" },
        "signature": { "type": "string", "pattern": "^[A-Za-z0-9_-]{43}$" }
      }
    }
  }
}
```

### 5.13 `ReviewItem`

Admin view. Contains no contract text (R-02).

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:review-item:1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "kind", "analysisId", "findingId", "createdAt", "expiresAt", "status", "clauseType", "findingKind", "severity", "side", "riskSummary", "facts", "claimedCitation", "verification", "reasonCodes", "candidateSectionKeys", "corpusChange", "assigneeId", "decision"],
  "properties": {
    "id": { "type": "string", "format": "uuid" },
    "kind": { "enum": ["FINDING", "CORPUS_CHANGE"] },
    "analysisId": { "type": ["string", "null"], "format": "uuid" },
    "findingId": { "type": ["string", "null"] },
    "createdAt": { "type": "string", "format": "date-time" },
    "expiresAt": { "type": "string", "format": "date-time" },
    "status": { "enum": ["OPEN", "IN_REVIEW", "APPROVED", "REJECTED", "EXPIRED"] },
    "clauseType": { "oneOf": [{ "$ref": "urn:shromikai:schema:clause-type:1" }, { "type": "null" }] },
    "findingKind": { "enum": ["CONFLICT", "OMISSION", "AMBIGUITY", "INTERACTION", "POLICY_DEVIATION", null] },
    "severity": { "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW", null] },
    "side": { "enum": ["WORKER", "EMPLOYER", null] },
    "riskSummary": { "type": ["string", "null"], "maxLength": 400, "description": "Redacted paraphrase; shares no 8-token sequence with the source document" },
    "facts": { "type": "array", "items": { "$ref": "urn:shromikai:schema:clause-fact:1" } },
    "claimedCitation": {
      "type": ["object", "null"], "additionalProperties": false, "required": ["sectionKey", "quote", "claim", "claimReduced"],
      "properties": {
        "sectionKey": { "type": "string" },
        "quote": { "type": "string", "maxLength": 300 },
        "claim": { "type": "string", "maxLength": 600, "description": "Redacted claim; shares no 8-token sequence with the source document" },
        "claimReduced": { "type": "boolean", "description": "true when the agent's claim overlapped the contract and was replaced by the facts-only form" }
      }
    },
    "verification": { "oneOf": [{ "$ref": "urn:shromikai:schema:verification-result:1" }, { "type": "null" }] },
    "reasonCodes": { "type": "array", "items": { "type": "string" }, "description": "VerificationResult reason codes plus RULE_LLM_CONFLICT" },
    "candidateSectionKeys": { "type": "array", "maxItems": 40, "items": { "type": "string" } },
    "corpusChange": {
      "type": ["object", "null"], "additionalProperties": false, "required": ["sourceUrl", "storedSha256", "newSha256", "ingestRunId"],
      "properties": {
        "sourceUrl": { "type": "string", "format": "uri" },
        "storedSha256": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
        "newSha256": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
        "ingestRunId": { "type": ["string", "null"] }
      }
    },
    "assigneeId": { "type": ["string", "null"], "format": "uuid" },
    "decision": {
      "type": ["object", "null"], "additionalProperties": false, "required": ["decision", "citation", "note", "decidedBy", "decidedAt"],
      "properties": {
        "decision": { "enum": ["APPROVED_WITH_CITATION", "APPROVED_WITHOUT_CITATION", "REJECTED"] },
        "citation": { "oneOf": [{ "$ref": "urn:shromikai:schema:verified-citation:1" }, { "type": "null" }] },
        "note": { "type": ["string", "null"], "maxLength": 1000 },
        "decidedBy": { "type": "string", "format": "uuid" },
        "decidedAt": { "type": "string", "format": "date-time" }
      }
    }
  }
}
```

Transitions: `OPEN → IN_REVIEW` (claim), `IN_REVIEW → OPEN` (release), `IN_REVIEW → APPROVED`
(decision `APPROVED_*`), `IN_REVIEW → REJECTED`, `OPEN | IN_REVIEW → EXPIRED` (retention job when
`expiresAt` passes: 30 days after creation for undecided `FINDING` items; `CORPUS_CHANGE` items do
not expire while undecided). A decision sets `expiresAt` to `decidedAt` + 30 days for `FINDING`
items and + 12 months for `CORPUS_CHANGE` items (architecture §14). Anything else returns `422`
`invalid-transition`.

`claimedCitation.claim` is written by `ReviewItemFactory`: if the agent's claim shares an 8-token
sequence with the source document (`OverlapChecker`), it is replaced by the facts-only form
`"{clauseType}: {parameter} = {value} {unit} → {sectionKey}"` (one line per fact, ≤ 600 characters)
and `claimReduced` is `true`.

### 5.14 `Problem`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:shromikai:schema:problem:1",
  "type": "object",
  "required": ["type", "title", "status", "instance", "code", "traceId"],
  "properties": {
    "type": { "type": "string", "pattern": "^urn:shromikai:problem:[a-z0-9-]+$" },
    "title": { "type": "string", "description": "Localised by Accept-Language" },
    "status": { "type": "integer", "minimum": 400, "maximum": 599 },
    "detail": { "type": ["string", "null"] },
    "instance": { "type": "string", "description": "Request path template plus request ID" },
    "code": { "type": "string", "description": "Stable machine code; equals the last segment of type" },
    "traceId": { "type": "string" },
    "errors": {
      "type": "array",
      "items": {
        "type": "object", "required": ["pointer", "message"],
        "properties": { "pointer": { "type": "string" }, "message": { "type": "string" } }
      }
    },
    "retryAfterSeconds": { "type": ["integer", "null"], "minimum": 0 },
    "pages": { "type": "array", "items": { "type": "integer", "minimum": 1 }, "description": "unreadable-document only" }
  }
}
```

## 6. Enums

### 6.1 Core enums

| Enum | Values |
| --- | --- |
| `Side` | `WORKER`, `EMPLOYER` |
| `Role` | `WORKER`, `HR`, `ADMIN` |
| `Language` | `bn`, `en` |
| `ReadingLevel` | `BASIC`, `STANDARD` |
| `Sector` | `READY_MADE_GARMENTS`, `TEXTILES`, `CONSTRUCTION`, `IT_SOFTWARE`, `RETAIL`, `DOMESTIC_WORK`, `SECURITY_SERVICES`, `NGO`, `BANKING_FINANCE`, `OTHER` |
| `InputKind` | `PDF_TEXT`, `PDF_SCAN`, `IMAGE`, `TEXT` |
| `AnalysisStatus` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `EXPIRED` |
| `Stage` | `INTAKE`, `OCR`, `REDACT`, `CLASSIFY`, `RETRIEVE`, `RULES`, `RISK`, `VERIFY`, `CORRECT`, `COMPOSE`, `COMPLETE` |
| `Severity` | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` (rubric in PRD FR-39) |
| `FindingKind` | `CONFLICT`, `OMISSION`, `AMBIGUITY`, `INTERACTION`, `POLICY_DEVIATION` |
| `FindingSource` | `RULE_ENGINE`, `LLM_ANALYSIS`, `PLAYBOOK` |
| `FindingVerification` | `VERIFIED`, `UNCERTAIN`, `POLICY_ONLY` |
| `VerificationStatus` | `VERIFIED`, `UNCERTAIN`, `REJECTED` |
| `SupportLabel` | `SUPPORTS`, `PARTIAL`, `CONTRADICTS`, `UNRELATED` |
| `VerifierIndependence` | `DIFFERENT_FAMILY`, `SAME_FAMILY`, `RULE_PROVENANCE`, `HUMAN` |
| `CitationOrigin` | `LLM`, `RULE`, `MAPPING`, `REVIEWER`, `EXTERNAL`, `BASELINE` (architecture §7.1) |
| `RetrievalOrigin` | `SEARCH`, `GRAPH`, `TOPIC_MAP` |
| `EdgeKind` | `REFERS_TO`, `USES_DEFINITION`, `AMENDED_BY`, `ALIGNS_WITH` |
| `Comparator` | `MIN`, `MAX`, `EQ`, `REQUIRED`, `PROHIBITED` |
| `ReviewStatus` | `OPEN`, `IN_REVIEW`, `APPROVED`, `REJECTED`, `EXPIRED` |
| `ReviewDecision` | `APPROVED_WITH_CITATION`, `APPROVED_WITHOUT_CITATION`, `REJECTED` |
| `ReviewKind` | `FINDING`, `CORPUS_CHANGE` |
| `ApiKeyScope` | `analyses`, `laws`, `mcp`, `automation` |
| `OrgKind` | `SME`, `HR`, `NGO`, `LAW_FIRM`, `JOB_PORTAL`, `PLATFORM` |
| `Plan` | `FREE`, `TEAM`, `API`, `LEGAL_AID` |
| `UsageKind` | `ANALYSIS`, `COMPARE`, `MEMO_RENDER`, `API_CALL`, `MCP_CALL` |
| `ClientKind` | `MOBILE_WEB`, `DESKTOP_WEB`, `API`, `MCP` |
| `AuditAction` | `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `DEMO_LOGIN`, `ACCESS_DENIED`, `PRIVACY_BLOCK`, `REVIEW_DECISION`, `ADMIN_ACTION`, `API_KEY_CREATED`, `API_KEY_REVOKED` |
| `ClauseFlag` | `INSTRUCTION_LIKE_TEXT`, `LOW_OCR_CONFIDENCE`, `LEGACY_FONT_CONVERTED` |

### 6.2 `PiiType` and placeholders

| `PiiType` | Placeholder | Found by |
| --- | --- | --- |
| `PERSON` | `[PERSON_n]` | local model |
| `ORG` | `[ORG_n]` | local model |
| `ADDRESS` | `[ADDRESS_n]` | local model |
| `EMPLOYEE_ID` | `[EMPLOYEE_ID_n]` | local model |
| `NID` | `[NID_n]` | detector |
| `PHONE` | `[PHONE_n]` | detector |
| `EMAIL` | `[EMAIL_n]` | detector |
| `BANK_ACCOUNT` | `[BANK_ACCOUNT_n]` | detector |
| `CARD_NUMBER` | `[CARD_NUMBER_n]` | detector |
| `MOBILE_WALLET` | `[MOBILE_WALLET_n]` | detector |
| `DOB` | `[DOB_n]` | detector |
| `PASSPORT` | `[PASSPORT_n]` | detector |
| `BIRTH_REG` | `[BIRTH_REG_n]` | detector |
| `TIN` | `[TIN_n]` | detector |

### 6.3 `Unit`

`DAYS`, `WEEKS`, `MONTHS`, `HOURS_PER_DAY`, `HOURS_PER_WEEK`, `MULTIPLIER`, `PERCENT`,
`BDT_PER_MONTH`, `COUNT`, `BOOLEAN`.

### 6.4 `FactParameter` and allowed units

These are fact types read from a contract. They say nothing about what the law requires; legal
limits exist only in `data/rules/*.yaml`, derived from the ingested statute in P3.

| `FactParameter` | Allowed units | Meaning in the contract |
| --- | --- | --- |
| `PROBATION_LENGTH` | `MONTHS`, `DAYS` | Stated probation length |
| `PROBATION_EXTENSION` | `MONTHS`, `DAYS` | Stated possible extension of probation |
| `CONTRACT_DURATION` | `MONTHS` | Fixed term of the contract |
| `NOTICE_BY_EMPLOYER` | `DAYS`, `WEEKS` | Notice the employer gives to terminate |
| `NOTICE_BY_WORKER` | `DAYS`, `WEEKS` | Notice the worker gives to resign |
| `PAY_IN_LIEU_OF_NOTICE_ALLOWED` | `BOOLEAN` | Payment instead of notice allowed |
| `DAILY_WORKING_HOURS` | `HOURS_PER_DAY` | Ordinary daily hours |
| `WEEKLY_WORKING_HOURS` | `HOURS_PER_WEEK` | Ordinary weekly hours |
| `OVERTIME_RATE` | `MULTIPLIER` | Overtime pay as a multiple of the ordinary rate |
| `OVERTIME_DAILY_HOURS` | `HOURS_PER_DAY` | Maximum overtime hours per day stated |
| `OVERTIME_COMPULSORY` | `BOOLEAN` | Overtime made mandatory |
| `WEEKLY_REST_DAYS` | `COUNT` | Rest days per week |
| `ANNUAL_LEAVE` | `DAYS` | Annual (earned) leave per year |
| `CASUAL_LEAVE` | `DAYS` | Casual leave per year |
| `SICK_LEAVE` | `DAYS` | Sick leave per year |
| `FESTIVAL_HOLIDAYS` | `DAYS` | Festival holidays per year |
| `MATERNITY_LEAVE` | `DAYS`, `WEEKS` | Maternity leave |
| `WAGE_PERIOD` | `DAYS` | Length of the wage period |
| `WAGE_PAYMENT_DEADLINE` | `DAYS` | Days after the period ends by which wages are paid |
| `DEDUCTION_LIMIT` | `PERCENT` | Maximum deduction from wages stated |
| `BASIC_WAGE` | `BDT_PER_MONTH` | Basic wage |
| `GROSS_WAGE` | `BDT_PER_MONTH` | Gross wage |
| `TERMINATION_WITHOUT_NOTICE_ALLOWED` | `BOOLEAN` | Termination without notice or pay allowed |
| `TERMINATION_BENEFIT_STATED` | `BOOLEAN` | A termination or service benefit is stated |
| `NON_COMPETE_DURATION` | `MONTHS` | Post-employment non-compete period |
| `DISPUTE_FORUM_STATED` | `BOOLEAN` | A dispute forum is named |

Adding a parameter is a schema change (`fact-parameter:2`) and needs an ADR when it breaks
clients.

## 7. Error model

All errors use `application/problem+json` per RFC 9457 with the `Problem` schema;
`type` is `urn:shromikai:problem:{code}`.

| Code | Status | Title (en) | Title (bn) | When |
| --- | --- | --- | --- | --- |
| `validation-failed` | 400 | Request is invalid | অনুরোধটি সঠিক নয় | Schema or constraint violation; `errors[]` lists pointers |
| `signature-invalid` | 400 | Memo envelope signature is invalid | মেমোর স্বাক্ষর সঠিক নয় | Tampered or unknown key |
| `invalid-credentials` | 401 | Email or password is wrong | ইমেইল বা পাসওয়ার্ড ভুল | Login failed |
| `invalid-token` | 401 | Sign-in has expired | সাইন-ইনের মেয়াদ শেষ | Missing, expired or tampered token; revoked refresh token |
| `forbidden` | 403 | Not allowed | অনুমতি নেই | Role or scope insufficient |
| `demo-account-restricted` | 403 | Not available for demo accounts | ডেমো অ্যাকাউন্টে এটি করা যাবে না | Password change or deletion by a demo user |
| `not-found` | 404 | Not found | পাওয়া যায়নি | Unknown or not owned resource; demo endpoint outside `demo` |
| `method-not-allowed` | 405 | Method not allowed for this resource | এই পদ্ধতি অনুমোদিত নয় | HTTP method not supported by the route; `Allow` header set (added P1) |
| `not-acceptable` | 406 | Response format not available | এই ফরম্যাটে উত্তর দেওয়া যায় না | `Accept` header matches no response type (added P1) |
| `email-taken` | 409 | Email already registered | এই ইমেইল আগেই নিবন্ধিত | Admin user creation |
| `idempotency-key-reused` | 409 | Idempotency key reused with a different request | একই কী ভিন্ন অনুরোধে ব্যবহৃত | E-12, E-18 |
| `already-claimed` | 409 | Review item already claimed | অন্য কেউ পর্যালোচনা করছেন | E-25 |
| `already-decided` | 409 | Review item already decided | সিদ্ধান্ত আগেই হয়েছে | E-25 |
| `ingest-running` | 409 | Ingestion already running | আইন সংগ্রহ চলছে | E-41 |
| `analysis-expired` | 410 | Result has expired | ফলাফলের মেয়াদ শেষ | After 30 minutes or `DELETE` |
| `envelope-expired` | 410 | Memo link has expired | মেমোর মেয়াদ শেষ | Envelope older than 24 hours |
| `ticket-expired` | 410 | Review ticket has expired | পর্যালোচনা টিকিটের মেয়াদ শেষ | After the last of its review items expires |
| `payload-too-large` | 413 | File is too large | ফাইলটি অনেক বড় | Over 10 MB |
| `unsupported-media-type` | 415 | File type not supported | এই ধরনের ফাইল গ্রহণযোগ্য নয় | Not PDF, JPEG, PNG or text by magic bytes |
| `too-many-pages` | 422 | Too many pages | পৃষ্ঠা সংখ্যা বেশি | Over 15 pages or images |
| `unreadable-document` | 422 | Some pages could not be read | কিছু পৃষ্ঠা পড়া যায়নি | OCR confidence below 60; `pages[]` set |
| `not-an-employment-contract` | 422 | This does not look like an employment contract | এটি চাকরির চুক্তি বলে মনে হচ্ছে না | FR-33 |
| `citation-not-verifiable` | 422 | Citation failed existence or quote check | উদ্ধৃতি যাচাই করা যায়নি | Review decision |
| `invalid-transition` | 422 | Status change not allowed | অবস্থা পরিবর্তন অনুমোদিত নয় | Review item |
| `playbook-limit-reached` | 422 | Playbook limit reached | প্লেবুকের সীমা পূর্ণ | More than 10 active playbooks |
| `account-locked` | 423 | Account temporarily locked | অ্যাকাউন্ট সাময়িকভাবে বন্ধ | 5 failed logins; `retryAfterSeconds` set |
| `quota-exceeded` | 429 | Daily limit reached | আজকের সীমা শেষ | Guest quota; `retryAfterSeconds` until midnight Asia/Dhaka |
| `concurrency-limit` | 429 | An analysis is already running | একটি বিশ্লেষণ চলছে | Concurrency limit |
| `rate-limited` | 429 | Too many requests | অনেক বেশি অনুরোধ | Rate limits (§8) |
| `privacy-guard-blocked` | 500 | Stopped to protect personal data | ব্যক্তিগত তথ্য রক্ষায় থামানো হয়েছে | FR-30 |
| `internal-error` | 500 | Unexpected error | অপ্রত্যাশিত ত্রুটি | Unhandled exception (no stack trace in body) |
| `model-output-invalid` | 502 | Analysis could not be completed | বিশ্লেষণ সম্পন্ন করা যায়নি | Schema-invalid model output after repair |
| `model-refused` | 502 | Analysis could not be completed | বিশ্লেষণ সম্পন্ন করা যায়নি | Refusal in a stopping step |
| `local-model-unavailable` | 503 | Privacy service unavailable; nothing was sent | গোপনীয়তা সেবা বন্ধ; কিছুই পাঠানো হয়নি | FR-31 |
| `upstream-unavailable` | 503 | AI service temporarily unavailable | AI সেবা সাময়িকভাবে বন্ধ | Provider failure; `Retry-After` |
| `renderer-unavailable` | 503 | Memo could not be rendered | মেমো তৈরি করা যায়নি | Gotenberg failure |
| `budget-exceeded` | 503 | Analysis exceeded its cost limit | খরচের সীমা অতিক্রম | FR-62 |
| `capacity-exhausted` | 503 | Service is at capacity today | আজকের সক্ষমতা শেষ | Daily cost cap |
| `instance-lost` | 503 | Analysis was interrupted; please resubmit | বিশ্লেষণ বিঘ্নিত; আবার জমা দিন | Owner instance died |
| `analysis-timeout` | 504 | Analysis took too long | বিশ্লেষণে বেশি সময় লেগেছে | Step timeout or 120 s |

## 8. Rate limits and quotas

| Subject | Limit | Response |
| --- | --- | --- |
| Auth endpoints | 20 requests per minute per IP; 10 per minute per user; lockout after 5 failed logins for 15 minutes | `429` `rate-limited`; `423` `account-locked` |
| Guest | 3 analyses per device token per day (a compare counts 2), 10 per IP per day, 1 concurrent | `429` `quota-exceeded` / `concurrency-limit` |
| Signed-in `WORKER` | 10 analyses per day, 1 concurrent | `429` |
| Organisation (HR users and API keys) | 5 concurrent analyses; plan allowance per month (configuration) | `429` |
| API key | 60 requests per minute across REST and MCP | `429` / MCP tool error |
| `/laws/search` | 60 per minute per subject | `429` |

Responses include `Retry-After` and `retryAfterSeconds`. Limits use Bucket4j token buckets per
instance plus `usage_counter` for daily quotas.

## 9. MCP tool contracts

- `/mcp` with the Spring AI MCP server starter for WebMVC (`spring-ai-starter-mcp-server-webmvc`, Spring AI 1.1.x pinned in ADR-0001), profile `mcp`. Transport: **Streamable HTTP** (`spring.ai.mcp.server.protocol=STREAMABLE`); the deprecated SSE transport is not offered. `docs/mcp.md` states the transport and the pinned version.
- Authentication: `Authorization: Bearer shk_...` with scope `mcp`; per-key limit 60 calls per minute; every call writes a `usage_event` (`MCP_CALL`) and denied calls write an audit event.
- Results: `structuredContent` matching the tool's output schema, plus one `text` item with the same JSON. Errors: `isError: true` with a `Problem` as `structuredContent`.
- Every result includes `disclaimer` and `corpus_release`. Arguments and results use `snake_case`.

| Tool | Description given to clients | Input | Output |
| --- | --- | --- | --- |
| `search_bd_law` | Search Bangladeshi labour law and the ingested ILO conventions in Bangla or English with hybrid retrieval on ShromikAI's own servers (no cloud model sees the query). Returns section keys and official source URLs. | `query` string 1..500 (required); `top_k` int 1..20 (default 8); `language` `bn` \| `en` \| `any` (default `any`) | `{ results: [{ section_key, act_code, section_no, heading_bn, heading_en, snippet, source_url, fused_score, origin }], corpus_release, disclaimer }` |
| `get_law_section` | Get the current official text of one statute section in Bangla and English, with version and source. | `act_code` string (required); `section_no` string, Latin or Bangla digits (required) | `{ section_key, act_code, act_title_bn, act_title_en, section_no, section_no_original, heading_bn, heading_en, authoritative_lang, text_bn, text_en, translation_official, version_label, effective_from, is_current, source_url, fetched_at, related: [{ section_key, kind, reviewed }], corpus_release, disclaimer }` |
| `verify_legal_citation` | Run ShromikAI's 3-check verifier (existence, quote match, independent support) on a citation before showing it to anyone. Personal data in the claim is removed on the server before the support model sees it. | `act_code`, `section_no`, `quoted_text` (20..300), `claim` (20..600) — all required; verified as origin `EXTERNAL` (no candidate-set check), claim redacted locally first (architecture §10.7) | `{ status: VERIFIED \| UNCERTAIN \| REJECTED, checks: { existence, quote_match: { outcome, similarity }, support: { outcome, label, confidence, independence, reason } }, reason_codes, verified_citation \| null, corpus_release, disclaimer }` |
| `analyze_employment_contract` | Analyse one employment contract with the full ShromikAI pipeline. Personal data is removed on the server before any cloud model sees it; the contract is neither stored nor echoed. | `text` string 200..60,000 (required); `side` `WORKER` \| `EMPLOYER` (required); `output_language` `bn` \| `en` (default `bn`) | `{ analysis_id, findings: [{ id, title, kind, severity, non_obvious, explanation, verification_status, citations: [VerifiedCitation in snake_case], suggested_clause \| null }], summary, review_ticket \| null, provenance, disclaimer }`; all texts keep placeholders and never contain input identifiers. Every field except `suggested_clause` contains no 8-token span of the input; `suggested_clause` (the revised clause from the counter-offer agent, only for `VERIFIED` findings) is exempt from the 8-token rule because a revision naturally repeats parts of the clause it revises, but it still uses placeholders. Nothing is stored |
| `list_jurisdiction_packs` | List available jurisdiction packs and their coverage. | none | `{ packs: [{ code, name, acts: [{ act_code, title_en, title_bn, section_count, authoritative_lang }], ilo_conventions: [{ code, ratification_status }], active_rules, reviewed_ilo_mappings, corpus_release }], disclaimer }` |

| MCP feature | Contract |
| --- | --- |
| Resource template `law://bd/{act_code}/{section_no}` | `text/markdown`: heading, authoritative text, translation (labelled official or unofficial), version, effective date, source URL |
| Prompt `review-employment-contract` | Arguments `side` (`WORKER` \| `EMPLOYER`) and `language` (`bn` \| `en`). Messages instruct the client agent to call `analyze_employment_contract`, present only verified citations, call `verify_legal_citation` before citing anything else, and say "uncertain" instead of guessing |

`McpToolSchemaTest` asserts that the registered schemas match this table.

## 10. Automation endpoints used by n8n

n8n holds an API key with scopes `automation` and `laws` and calls only E-31 (to read source URLs)
and E-41 to E-44. It never receives or sends contract content.

| Step of `legislative-watch` | Call |
| --- | --- |
| Weekly cron | n8n schedule trigger (Sunday 02:00 Asia/Dhaka) |
| Fetch watched pages | n8n HTTP GET on the source URLs from `GET /api/v1/laws` (`sourceUrl`) |
| Compare hashes | `POST /api/v1/admin/laws/check-hashes` |
| On change, re-ingest | `POST /api/v1/admin/laws/ingest` with the changed acts |
| Retrieval smoke | `POST /api/v1/admin/laws/retrieval-smoke` |
| Notify | Email or Telegram node with act codes, URLs, hashes and smoke metrics only |
| Review task | `POST /api/v1/admin/review-items` with `kind: CORPUS_CHANGE` |

## 11. Versioning and deprecation

- Additive changes (new optional fields, new endpoints, new response-only enum values) stay in `v1`.
- Breaking changes require `/api/v2`, an ADR and at least 30 days during which both versions run.
- Deprecated endpoints send `Deprecation` and `Sunset` headers (RFC 8594).
- MCP tools follow the same rule: a breaking change gets a new tool name with suffix `_v2`.

| Date | Change |
| --- | --- |
| 2026-11-10 | v1 specification (`spec-v1`), aligned with playbook v1.1 |
| 2026-11-10 | v1.1 (`spec-v1.1`): signed envelope holds the re-identified result; SSE Bearer authentication and no query-string tokens; `rewrittenQueries` removed from free-text search; E-43 moved to `/admin/laws/retrieval-smoke`; `Meta.models.judge`; `input.sample`; `CitationOrigin` adds `MAPPING` and `EXTERNAL`; MCP claim 20..600, Streamable HTTP, `suggested_clause` exemption; review-item and ticket expiry; `claimedCitation.claimReduced`. No implemented client is affected (nothing shipped before v1.1) |
| 2026-11-10 | P1 (additive): problem codes `method-not-allowed` (405) and `not-acceptable` (406); `Problem.instance` is the route template plus `#` and the request ID, template braces percent-encoded (`/api/v1/analyses/%7Bid%7D#<requestId>`; `/unmatched#<requestId>` for unknown routes); OpenAPI document at `/v3/api-docs` (OpenAPI 3.0) from P1, every response property listed as required, nullable ones marked `nullable` |
| 2026-11-11 | P1 review fixes (additive): the OpenAPI document carries the `Problem` schema of §5.14 (plus `ProblemError`) as the `default` response of every operation; `Meta.audit` and `Meta.audit.latestEval` are typed (`MetaAudit`, `MetaLatestEval`); framework errors whose status has no specific code keep that status (for example `409`) with code `validation-failed` (4xx) or `internal-error` (5xx); provider 4xx other than 408/429 map to `internal-error`; `upstream-unavailable` passes the provider's `Retry-After` on 429/503; model reason codes never appear in `detail` |
