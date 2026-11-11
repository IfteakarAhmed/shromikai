# `pii-extract` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/pii-extract/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/pii-extract.v1.schema.json` |
| Role | `LOCAL_PII` (qwen2.5:7b-instruct on Ollama, temperature 0) |
| Phase | P5 (FR-29) |
| Eval | set `pii`, metric M-25 (recall per PII type) |

## Purpose

Finds names, organisations, addresses and employee IDs that regular expressions cannot find, so the redactor can replace them with placeholders before any cloud call.

## Where it runs

- Step `REDACT`, on **original** text, only on our own Ollama. It is a local call: the egress guard does not apply, and no cloud adapter ever receives its input or output.
- Also used on free-text claims from MCP `verify_legal_citation` (P8).

## Input and output

- `text` (untrusted): one chunk of at most 1,500 characters (200-character overlap), XML-escaped inside `<contract_document>`.
- Output: `{ status, reason, persons[], organisations[], addresses[], employee_ids[] }`. The redactor replaces every exact occurrence of each returned string in the whole document; strings that do not occur are ignored.

## Failure handling

One schema repair attempt through `ModelGateway`. Ollama unreachable, a timeout or an invalid answer: the chunk is retried once, and a second failure fails the analysis with `local-model-unavailable` (FR-31). Nothing is sent to the cloud.

## Safety

No tools. The output is only a list of strings matched against the text. An injected instruction can at worst hide a value from the local model, which is why the deterministic detectors run first and the egress guard checks every outgoing payload again (R-03).

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
