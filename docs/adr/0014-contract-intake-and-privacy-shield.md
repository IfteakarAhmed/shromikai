# ADR-0014: Contract intake and privacy shield (P5 implementation choices)

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | Rules R-02, R-03, R-06; PRD FR-24 to FR-34, NFR-05, NFR-06, NFR-20; architecture §5, §10; ADR-0003, ADR-0005, ADR-0006 |

## Context

ADR-0003 fixed the privacy design. Phase 5 implements it and has to choose how: how OCR runs
without writing a file, how the egress guard learns the placeholder map of the job a call belongs
to, and which data stays in the redacted text. Architecture §10.1 named `tess4j` for OCR.

## Decision

1. **Money, durations and employment dates stay in the redacted text.** The legal analysis needs
   wages, overtime hours, notice periods and probation lengths. Once names, identifiers, contacts,
   addresses and organisations are replaced they do not identify a person. Dates are redacted only
   when they sit next to a birth keyword (`DOB`). This restates ADR-0003 decision 5 for the code:
   `PiiDetectorTest` and `RedactionRoundTripTest` assert that `BDT 25,000`, `6 months` and
   `01/01/2026` survive. `shromikai.privacy.redact-money` stays `false`.
2. **Tesseract runs as a child process, not through `tess4j`.** The page image goes to the
   process as PNG on stdin and the word table (`tsv`) comes back on stdout, so no file is created
   and no JNA library has to be present in the JVM. The command is configuration
   (`shromikai.intake.tesseract-command`), so a deployment can use the system binary or a container
   (`docker run -i … tesseract stdin stdout -l ben+eng tsv`). This avoids a new runtime dependency
   (AGENTS.md §9 item 1); the production image installs `tesseract-ocr` with the `ben` and `eng`
   data. Architecture §10.1 is updated.
3. **The egress guard gets the job's placeholder values through the request.** `ChatRequest`
   has an optional `EgressContext` (interface in `shared.ai`, implemented by the privacy module's
   `PlaceholderMap`). `ModelGateway.call` takes the context of the analysis and passes it on; every
   cloud adapter hands the whole request to `EgressGuard.check`. `PrivacyEgressGuard` (module
   `privacy`) replaces the fail-closed stand-in: it re-runs the detectors on system and user turn
   and searches the payload for every protected value with an Aho–Corasick automaton, case
   insensitively. A request without a context still gets the detectors. There is no thread-local
   state, so virtual threads and parallel batches cannot mix up two jobs.
4. **Blocks are audited without content.** The `PRIVACY_BLOCK` audit detail holds a reason code
   (`pii_detected`, `known_value_found`) and the analysis ID as target, never the value; the audit
   detail schema already forbids anything else. If the audit write fails, the call is still blocked.
5. **Fail closed means two attempts.** `pii-extract` is tried twice per chunk; a second failure
   throws `local-model-unavailable` before any cloud adapter is reached.
6. **The pipeline ends at `CLASSIFY` in P5.** A job that finished classification has status
   `CLASSIFIED`; P6 continues from there. Jobs, placeholder maps and redacted clauses live in an
   in-memory `JobStore` for at most 30 minutes. Trace events are kept in memory only in P5 (the
   `analysis_trace` table arrives with the P6 migration).
7. **The debug endpoint creates jobs too.** E-45 only reads. For the P5 acceptance check there has
   to be a way to start the pipeline before the public analysis API (P7) exists, so the `dev`
   profile also exposes `POST /api/v1/dev/analyses` (admin only, same bean, same 404 outside `dev`).

## Consequences

- No new runtime dependency; OCR needs the `tesseract` binary (or a container command) where
  `slowTest` and the application run.
- `ChatRequest` gains a field; the 11-argument constructor stays for calls outside an analysis.
- Names that appear in the text but are not returned by the local model are not caught by the value
  search (RK-02). Detectors still catch identifiers with a pattern, and eval metric M-25 will
  report recall per type; this is a known limit, not a hidden one.
- The legacy-font converter covers letters, vowel signs, reph, ya-phala, ra-phala and the `ক্ষ` glyph.
  Pages whose converted text is below 0.90 Bengali letters fall back to OCR (FR-26), so rarer
  conjunct glyphs cost accuracy, not correctness of the gate.

## Alternatives

- `tess4j`: in-process, but a JNA dependency and native libraries in the JVM image; rejected.
- A thread-local "current job" for the guard: simpler signatures, but wrong with virtual threads and
  parallel batches; rejected for an explicit field.
- Redacting money: rejected in ADR-0003; an eval run would be needed to change it.

## Verification

`PiiDetectorTest`, `PiiDetectorPropertyTest`, `RedactionRoundTripTest`, `EgressGuardTest`,
`NoPiiLeavesTheBoxIT` (real Claude adapter against WireMock, five contracts), `FailClosedIT`,
`NoContractPersistenceIT`, `MagicBytesTest`, `PdfTextExtractorIT`, `OcrIT`.
