# ADR-0003: Local PII redaction and no contract text at rest

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | Rules R-02, R-03; PRD FR-09, FR-24 to FR-34, FR-50, FR-54, FR-55, FR-66; architecture §10, §14; playbook P5; ADR-0006 (free-text paths, signed re-identified result, review-item retention) |

## Context

Employment contracts contain names, national ID numbers, phone numbers, addresses, bank accounts
and salaries. The challenge's example prompt asks for a bot that "advises on sensitive employment
contracts without storing private document data on external servers". The event rules require
personal data to be anonymised. The reasoning models (Claude, Gemini) are cloud services. Workers
often use shared phones and should not need an account.

## Decision

1. **Everything that sees raw content runs locally.** Text extraction (PDFBox), OCR (Tesseract `ben+eng`) and PII detection run on the deployment's own hardware. No cloud vision model receives an uploaded image.
2. **Two-stage redaction.** Deterministic detectors (NID, phone, email, bank-account and card-like numbers, mobile wallet, date of birth, passport, birth registration, TIN), then `pii-extract` on qwen2.5:7b-instruct through Ollama for persons, organisations, addresses and employee IDs. Values become typed placeholders `[TYPE_n]` (for example `[PERSON_1]`), stable within a document; the map stays in the job's memory.
3. **Egress guard.** Every cloud-bound request body is re-scanned with the detectors and searched for every known PII value; a hit blocks the call, fails the analysis and writes a `PRIVACY_BLOCK` audit event.
4. **Fail closed.** If the local model is unavailable, the analysis stops; there is no unredacted fallback.
5. **Money, durations and employment dates are kept.** They are needed for wage, overtime, notice and probation reasoning and do not identify a person once names, identifiers, contacts, addresses and organisations are removed. `shromikai.privacy.redact-money` exists, defaults to `false`, and changing it requires an eval run (M-06, M-22).
6. **No contract text at rest.** "Contract text" is any span of 8 or more consecutive tokens from the input, or any PII value (`AGENTS.md` §2). Uploads stay in memory (multipart threshold above the limit; PDFBox memory-only stream cache; `ImageIO` cache off; heap dumps off). The redacted result and placeholder map live in an in-memory job store for at most 30 minutes; names are restored only in the response to the owning subject. The database stores only metadata, content-free traces and redacted review items. A log redaction filter is the second line of defence.
7. **Stateless memo download.** The memo PDF is rendered from a signed result envelope that the client sends back; the envelope holds the re-identified result (ADR-0006).
8. **Review without content.** Review items hold a redacted paraphrase of at most 400 characters with no 8-token overlap with the source, typed facts, the claimed citation and reason codes.
9. **No accounts needed, no browser persistence.** Workers use a guest token bound to a random device token; results are never stored in Web Storage, IndexedDB or caches.
10. **Untrusted input isolation.** Contract text is wrapped in `<contract_clause>`, declared untrusted, and scanned for instruction-like text that is flagged in the trace.

## Consequences

Positive:

- The privacy promise is testable on every CI run (`NoPiiLeavesTheBoxIT`, `NoContractPersistenceIT`; M-16, M-17).
- Cloud providers receive non-identifying text.
- No stored contracts means no breach of stored contracts.

Negative:

- A GPU is needed for the 60-second target (CPU-only reported separately).
- A small local model can miss rare Bangla names that neither stage recognises; mitigated by detectors, the egress check on known values and the M-25 targets, and stated in the privacy page and every eval report's limitations.
- Reviewers judge uncertain findings without the full clause text.
- Users must download the memo within 24 hours and cannot reopen past analyses after 30 minutes.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Cloud OCR, cloud vision or cloud DLP | Sends raw PII to a third party before redaction |
| Regex-only redaction | Cannot find names and addresses reliably |
| Storing encrypted contracts for history and review | Breaks the core promise; breach liability |
| Redacting all amounts | Breaks wage, deduction and overtime reasoning; kept as an off-by-default switch |
| Server-side result cache in Redis for memo download | Content at rest outside the JVM; the signed envelope removes the need |
| Mandatory worker accounts | Friction and more personal data for the most vulnerable persona |

## Verification

- `PiiDetectorTest`, `RedactionRoundTripTest`, `EgressGuardTest`, `NoPiiLeavesTheBoxIT`, `FailClosedIT`, `NoContractPersistenceIT`, `LogRedactionTest`, `McpPrivacyIT`, `StorageGuard.test.ts`.
- Evaluation hard gates M-16 = 0 and M-17 = 0; M-25 targets per PII type.
