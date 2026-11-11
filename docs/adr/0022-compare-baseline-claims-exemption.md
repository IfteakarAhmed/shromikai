# ADR-0022: Compare mode shows rejected and uncertain baseline claims as labelled non-citations

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-12 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0002, ADR-0020; rules R-01, R-12; PRD FR-59; api.md §5.4 and `CompareResult`; found in the P7 review |

## Context

R-01 says only `VERIFIED` citations reach users. Compare mode (FR-59) puts a plain LLM answer (the
`baseline` prompt: no retrieval, no verifier) beside the ShromikAI result and runs every baseline
citation through the same verifier. The point of the screen is to show that an unverified model
invents or misquotes law: a rejected citation is shown with a cross and its reason code (api.md
`VerificationStatus`: "in compare mode the baseline's rejected citations show ✗ বাতিল / Rejected").
That output contains section keys and quotes whose status is `REJECTED` or `UNCERTAIN`, which
R-01 read literally forbids. The P7 review found the exemption in the code and in api.md but in no
ADR (AGENTS.md §9 item 6: changing or relaxing a rule needs one).

## Decision

1. **A narrow exemption.** In the `CompareResult` only, under `baseline.findings[].citations[]`, an
   entry may carry a model's *claim* whose verification status is `REJECTED` or `UNCERTAIN`.
2. **Never a citation.** The claim is returned as `claimed` (`sectionKey`, `quote`, `claim`) next to
   the verifier's `verification` object (status and reason codes). It has no `citation` object (no
   act title, heading, source URL or authoritative text), so nothing in it can be mistaken for, or
   copied as, a verified citation. A `VERIFIED` baseline entry also keeps the `claimed` shape.
3. **Labelled.** The UI shows the status and reason for every baseline claim, and the whole column
   under a heading that says it is an unverified model answer (R-12). The `summary` counts verified,
   uncertain and rejected claims.
4. **Nowhere else.** The `shromikai` half of the compare result, `GET /analyses/{id}`, the memo,
   MCP and every export obey R-01 unchanged. The baseline never feeds the memo or the findings.
5. **Enforced by test.** `VerifiedOnlyScanner.violations` stays strict.
   `VerifiedOnlyScanner.violationsAllowingLabelledBaselineClaims` allows only the shape in item 2
   (a `claimed` object whose parent carries `verification.status` and no `citation`), and
   `ApiResponsesCarryOnlyVerifiedCitationsIT` scans a real compare result with a rejected claim:
   the `shromikai` half under the strict rule, the whole result under the exemption, and checks the
   strict rule *does* find the baseline claim, so the scan has teeth.

## Consequences

- R-01 now has one documented exception; AGENTS.md R-01 should say "except labelled baseline claims
  in compare mode (ADR-0022)". That edit is a separate `docs(spec)` change (AGENTS.md §0); until
  then this ADR is the record.
- A change that adds a `citation` object, or any other field that looks like a citation, to a
  non-verified baseline entry fails the compare case of the R-01 test.

## Alternatives

- **Hide rejected baseline claims.** Safe for R-01 but removes the demonstration that makes compare
  mode worth having, and would show the baseline as better than it is.
- **Show them in prose without section keys.** Loses the checkable evidence (which section, which
  reason code).
- **A separate endpoint for the baseline.** Moves the same content; the exemption would remain.

## Verification

`ApiResponsesCarryOnlyVerifiedCitationsIT.aCompareResultShowsRejectedBaselineClaimsOnlyAsLabelledNonCitations`,
`CompareApiIT` (a fabricated section is `REJECTED` with `SECTION_NOT_FOUND`, and has no `citation`).
