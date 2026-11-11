# ADR-0015: Intelligence core (P6 implementation choices)

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | Rules R-01, R-03, R-04, R-05, R-06, R-10; PRD FR-35 to FR-50, NFR-01; architecture §5, §7, §9, §11; ADR-0002, ADR-0005, ADR-0007, ADR-0008 |

## Context

P6 builds the rule engine, the risk and counter-offer agents, the citation verifier and the
orchestrator states after `CLASSIFY`. The specification fixes the behaviour (decision table,
state machine, budgets). Six points were left open or collide with the module matrix or with the
phase order, and the team has only a small API balance, so live spend needs its own limit.

## Decision

1. **`ClauseFact`, `FactParameter` and `Unit` live in module `rules`.** The module matrix forbids
   `rules → analysis`, but the rule engine evaluates facts and `analysis` produces them. `analysis`
   depends on `rules`, so the types belong to `rules`. `ClauseType` stays in `analysis`; a rule file
   holds its clause type as a string checked against `rules.schema.json` (the api.md §5.1 list).
2. **`dynamic_enums` is implemented in `ModelGateway`.** The front-matter key of AGENTS.md §5.2 is
   read by `PromptRegistry`; the caller passes the allowed values per request and the gateway
   replaces the `enum` of every schema node the path selects (a copy; the prompt's own schema is
   not changed). The model, the validator and the fake port all see the restricted schema, so a key
   outside the candidates fails validation (`RiskAgentSchemaTest`).
3. **A minimal usage listener and a per-job budget ship in P6.** `ModelGateway` reports every call
   (prompt, model, tokens, latency) to a `LlmCallListener`; the analysis module prices it with
   `shromikai.pricing.*` and stops the job with `budget-exceeded` when `shromikai.budget.per-analysis-usd`
   (US$1.00) or the token budget is exceeded. The persistent cost ledger, daily cap and admin
   dashboard stay in P7 (FR-62).
4. **Live runs have their own cumulative cap.** Tests and tools that call real providers go through
   a `LiveSpendLedger` (test support, state in the git-ignored `.live-spend/ledger.json`). It prices
   each call from reported usage, refuses a call whose worst-case cost (input estimate plus
   `max_output_tokens`) would pass **US$3.00** in total, and never retries beyond the adapters' two
   retries. This is a development-time guard, not product behaviour.
5. **Cheapest suitable model per role.** `query-rewrite`, `clause-segment`, `clause-classify`,
   `clause-facts` and the verifier fallback use Haiku 5.5 (`FAST`, `VERIFIER_FALLBACK`); Sonnet 5.5
   is used only by `risk-analysis`, `risk-citation-repair` and `counter-offer`; `verify-support` uses
   Gemini Flash. Output caps are set as low as the schemas allow.
6. **Legal rules stay `draft` until a person other than the author reviews them.** The drafter
   (Claude) never sets `reviewed_by`. `docs/rules-review-checklist.md` lists, per rule, the section
   key, the verbatim quote and the official portal URL so the reviewer can check each one. Draft
   rules are never evaluated (architecture §9.1); the loader still validates their quotes.
7. **Review items and the overlap check.** `shared.text.OverlapChecker` implements the 8-token rule
   of the glossary; `verify.ReviewItemFactory` uses it so every stored paraphrase and claim shares
   no 8-token sequence with the source, falling back to the facts-only form (`claim_reduced`).
8. **Results are visible only through the `dev` endpoint in P6.** There is no public analysis API
   until P7, so `GET /api/v1/dev/analyses/{id}/findings` (E-45b) returns the redacted findings (verified
   citations only) and `POST /api/v1/dev/analyses?full=true&side=WORKER` runs the whole P6 pipeline. Re-identification and signing remain P7.

9. **Recordings are addressed by request hash, and some tests use stand-ins.** See architecture
   §18.6 C35 to C37: hash-addressed fixtures, scripted stand-ins on a fictional act for the
   pipeline integration tests, and an offline demo tool.

10. **Claude extended thinking is off.** A live run showed hidden thinking tokens consuming a
    prompt's `max_output_tokens` (architecture §18.6 C38), so the adapter sends `thinking: disabled`.
    This amends ADR-0005's "adaptive thinking"; the effort setting stays.

## Consequences

- No new runtime dependency (AGENTS.md §9 item 1 does not apply); the migration `V6__analysis.sql`
  stores metadata only (§11.2).
- Findings with rule-origin citations appear only after rules are reviewed; until then the
  Bangladesh pack produces LLM findings only. Tests use the fictional pack `xx`.
- The per-job budget uses configured prices, so a price change is a configuration change.
- Known gap: `expected_topics_missing` is passed to `risk-analysis` as an empty list. No data says
  which topics a contract of a worker category should cover (the topic map is clause type to
  sections), so omission findings come from the model's reading alone. Tracked in
  `docs/known-gaps.md` (P7 and later).
- Review-fix decisions: a failed repair or redline call (timeout, outage) degrades to `UNCERTAIN`
  or to a `counter-offer-dropped` warning; an egress block or budget stop still fails the job. A
  redline kept with no active rule to check it carries `counter-offer-unchecked`. Startup fails
  when a rule is active and no corpus release exists, since its quote was then never verified.

## Alternatives

- Put `ClauseFact` in `shared`: rejected, it is legal-domain vocabulary, not infrastructure.
- Let the model gateway enforce the budget: rejected, the gateway has no notion of a job.
- Activate rules on the author's own review: rejected, it breaks the two-person rule (FR-36).

## Verification

`RiskAgentSchemaTest`, `OrchestratorStateMachineTest` (budget exceeded), `RuleLoaderFailFastTest`,
`ReviewItemFactoryTest`, `LiveSpendLedgerTest`; the phase report states the measured cost of the one
live analysis.
