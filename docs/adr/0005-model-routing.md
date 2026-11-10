# ADR-0005: Model routing

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Backend/Data Engineer (TODO: name), Business Analyst/Data Scientist (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0002, ADR-0003; architecture §6; PRD NFR-01, NFR-03, FR-62; playbook P1, P6, P7 |

## Context

The pipeline has steps with very different needs: private entity extraction that must stay
local, cheap structured extraction and query rewriting, careful bilingual legal reasoning and
drafting, and an independent check of that reasoning. The fixed stack names qwen2.5:7b-instruct
and bge-m3 (local, via Ollama), Claude Sonnet and Claude Haiku, and Gemini Flash as an optional
verifier with a fallback to a separate Claude prompt. Current Claude models (Sonnet 5.5, Haiku
5.5) reject sampling parameters such as `temperature`, support structured outputs through
`output_config.format`, take an `effort` setting, and can stop with `stop_reason: refusal`.
Playbook v1.0 said "Spring AI adapters" for all models; the Phase 1 Spring AI Anthropic module
cannot express these features.

## Decision

1. **Route by role.** Prompts declare a `model_role`; `shromikai.models.*` maps roles to models. Changing a model is a configuration change plus an entry in this ADR's change log and an eval run on the affected metrics.

| Role | Model | Prompts | Reason |
| --- | --- | --- | --- |
| `LOCAL_PII` | qwen2.5:7b-instruct (Ollama) | `pii-extract` | Must run locally (ADR-0003); multilingual; fits a 16 GB GPU with bge-m3 |
| `EMBED` | bge-m3 (Ollama) | none | Bangla and English in one vector space; local |
| `FAST` | `claude-haiku-5-5` | `query-rewrite`, `clause-segment`, `clause-classify`, `clause-facts`, `obligations` | Structured extraction at the lowest price tier (US$0.10 / US$0.50 per million tokens, list price 2026-11-03) |
| `REASONING` | `claude-sonnet-5-5` | `risk-analysis`, `risk-citation-repair`, `counter-offer`, `memo-bn`, `memo-en`, `baseline` | Strong legal reasoning and Bangla drafting (US$2 / US$10 per million tokens) |
| `VERIFIER` | Gemini Flash, exact version in `GEMINI_MODEL` | `verify-support` | Different model family from the reasoning model, so errors are less correlated |
| `VERIFIER_FALLBACK` | `claude-haiku-5-5` | `verify-support-claude` | Works without a Gemini key; flagged `SAME_FAMILY` |
| `JUDGE` | Gemini Flash | `explanation-judge` (evaluation only) | Different family from the memo writer; used only after calibration (FR-81) |

2. **Ports and adapters.** `ChatPort` and `EmbeddingPort` hide providers; `ModelGateway` adds prompt rendering, egress checks, schema validation, one repair and the cost ledger.
   - `AnthropicChatAdapter` uses the official Anthropic Java SDK: structured outputs with the prompt's JSON Schema (reduced to the subset the API accepts; full validation runs locally), `output_config.effort` from front matter (`low` for `FAST`, `medium` for `risk-analysis` and repair, `low` for counter-offer and memos), no `temperature`, `top_p` or `top_k`, prompt caching on the stable prefix with cache-read tokens recorded.
   - `REASONING` calls opt into server-side refusal fallbacks (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`); the model actually used is recorded. Refusals on `FAST` make the step abstain or stop.
   - `GeminiChatAdapter` uses a `RestClient` with JSON response mode, a response schema, temperature 0 and the API key in a header.
   - `OllamaChatAdapter` and `OllamaEmbeddingAdapter` use Spring AI with `format` = JSON Schema and temperature 0.
3. **Prices in configuration** (`shromikai.pricing.*`) feed the cost ledger (FR-62) and M-20.

## Consequences

Positive:

- The expensive model runs on six prompts only; estimated US$0.41 per 10-page analysis (PRD §9.1), to be confirmed by M-20.
- Independent verification without making Gemini a hard dependency.
- Model changes are visible, reviewed and measured.

Negative:

- Three provider adapters to maintain; mitigated by `ChatPort`, `FakeChatPort` fixtures and WireMock contract tests.
- The fallback verifier is less independent; reported separately (`verifier-same-family`).
- A server-side refusal fallback can route a call to another model; provenance records it.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| One model for everything | Higher cost; generator and verifier errors correlated |
| Spring AI adapters for every provider (playbook v1.0) | The Spring AI Anthropic module in the Phase 1 build lacks structured outputs and `effort` and sends `temperature` |
| Claude Opus 5.5 for `risk-analysis` | About 2 × Sonnet 5.5's price; reconsider only if M-06 or M-07 misses its target on the dev split, measured as an ablation |
| Local model for reasoning | 7B-class models are weaker at bilingual legal reasoning |
| Haiku for the Bangla memo | Plain-Bangla quality is a core output (M-21); revisit with an eval if cost targets are missed |

## Change log

| Date | Change | Eval report |
| --- | --- | --- |
| 2026-11-10 | Initial routing | n/a (specification) |

## Verification

- WireMock contract tests (no sampling parameters, structured output present, refusal mapping) in `test-strategy.md` §6.2.
- `ProvenanceIT` asserts the models recorded per role; `CostLedgerTest` asserts per-model totals.
- Cost and latency per role in every eval report (M-19, M-20).
