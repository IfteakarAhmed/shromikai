# Prompt library

All prompts used by ShromikAI at runtime live in this folder, versioned. Each file states its purpose, model, input, output schema and version.

| Prompt | File | Status |
| --- | --- | --- |
| Clause classification | `classify-clauses.md` | Phase 4 |
| Risk analysis agent | `risk-analysis.md` | Phase 5 |
| Citation support check | `verify-support.md` | Phase 5 |
| Bangla memo | `memo-bn.md` | Phase 6 |
| Baseline (comparison only) | `baseline.md` | Phase 8 |

Rules for every prompt:

- Output is JSON that matches a schema and is validated in code.
- Temperature 0.
- The risk agent may only cite sections from the retrieved list it is given.
