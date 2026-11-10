# ADR-0007: Citation origins `MAPPING` and `EXTERNAL`

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0002 (verification-first design); rules R-01, R-10; PRD FR-16, FR-42 to FR-45, FR-64; architecture §7.1, §7.4, §7.8; api.md §5.4, §9 |

## Context

ADR-0002 defined three origins for a `ClaimedCitation` (`LLM`, `RULE`, `REVIEWER`) plus `BASELINE`
for compare mode. Two citation sources in the specification had no defined path through the
verifier:

1. **ILO references** (`RiskFinding.iloReferences`) are derived from reviewed `ALIGNS_WITH` rows in
   `data/mappings/bd-ilo.yaml`, not from an LLM or a legal rule. The spec said they were
   `VerifiedCitation`s but not how they were verified.
2. **MCP `verify_legal_citation`** receives citations from an outside agent. There is no analysis
   and no candidate set, so the `LLM`-origin rule "the key must be in the candidate set" cannot
   apply, and the claim length (1,000) differed from every other claim (600).

This changes the verification algorithm (AGENTS.md §9, criterion 4).

## Decision

1. **`MAPPING` origin** for ILO references. A reviewed mapping row (`reviewed: true`, `reviewed_by` ≠ `authored_by`) carries the ILO section key, a verbatim `ilo_quote` and a `claim`. Check (a) requires the ILO section to exist, be current and belong to a convention listed in the pack's reviewed `ilo_conventions`; check (b) matches `ilo_quote` against the ILO text; check (c) is `RULE_PROVENANCE`, as for legal rules. The loader checks every reviewed row's quote at startup and refuses to start on a miss. Unreviewed rows never produce a reference. An ILO reference never changes a finding's severity or status.
2. **`EXTERNAL` origin** for MCP `verify_legal_citation`. Check (a) runs without a candidate-set requirement; checks (b) and (c) run as for `LLM` origin, with the support model (Gemini, or the same-family fallback). The claim is 20 to 600 characters and is redacted locally first (ADR-0006).
3. The decision table row for `RULE_PROVENANCE` now reads "`RULE` or `MAPPING` origin, source row active and two-person reviewed". No other row changes; there is still no `WEAK` status.

## Consequences

Positive:

- Every `VerifiedCitation` in every output has a defined, tested path; ILO references meet the same bar as statutory citations.
- MCP clients get the same verifier semantics as the product, with no fake candidate set.

Negative:

- Mapping rows need a verbatim quote and two people, which slows down building the ILO mapping; until rows are reviewed, findings show no ILO references.
- `EXTERNAL` citations can name any current section, so the support check carries more weight for them than for `LLM` citations; the MCP response shows the independence level so clients can judge.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Treat mapping rows as `RULE` origin | Rules and mappings have different files, loaders and reviewers; one origin would hide which was used in the trace |
| Show ILO references without verification, labelled "unreviewed" | Contradicts R-01: only `VERIFIED` citations reach users |
| Give `EXTERNAL` calls a candidate set from `search_bd_law` on the claim | Adds a retrieval step with its own failure modes and still would not bind what the caller cites |

## Verification

- `VerificationDecisionTableTest` gains `MAPPING` and `EXTERNAL` rows.
- `IloMappingLoaderTest`: unreviewed rows and rows reviewed by their author never produce a reference; a reviewed row whose quote is missing stops startup.
- `McpServerIT`: `verify_legal_citation` returns `VERIFIED` for a real section and supporting claim and `REJECTED` for a fake section, with origin `EXTERNAL` in the trace.
