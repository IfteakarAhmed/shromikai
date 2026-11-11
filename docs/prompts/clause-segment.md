# `clause-segment` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/clause-segment/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/clause-segment.v1.schema.json` |
| Role | `FAST` (Claude Haiku, effort low) |
| Phase | P5 (FR-33) |
| Eval | set `contracts`, metric M-23 |

## Purpose

Splits unnumbered contract text into clauses when the rule-based splitter finds no numbering.

## Where it runs

- Step `CLASSIFY`, on **redacted** paragraphs only, after the egress guard.

## Input and output

- `paragraphs` (untrusted): redacted paragraphs `{ id, text }`, escaped inside `<contract_clause>`.
- Output: `{ status, reason, clauses: [{ paragraph_ids[] }] }`. Code checks that every ID exists and is used once; otherwise it falls back to one clause per paragraph.

## Failure handling

Invalid output after one repair, or an inconsistent grouping: one clause per paragraph (segmentation alone never fails an analysis).

## Safety

No tools; the output is IDs only. Contract text is escaped and declared untrusted (R-06).

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
