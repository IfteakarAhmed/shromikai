# Submission form map

Which part of the build answers each section of the submission form. Tick a box on the form only when the feature is actually built and in the repo — judges can check, and fabricated claims are against the event rules.

## AI-Native Build (110)

| Form section | Max | What we build | Phase |
| --- | --- | --- | --- |
| Prompt Usage | 10 | Versioned prompt files, JSON schemas, role + constraint prompts, citation-restricted prompting | 6–7 |
| Token Optimization | 10 | Anthropic prompt caching, cheap-model routing, structured outputs, query cache, PII-redacted short context | 7 |
| LLMs / Models Used | 15 | Claude (Sonnet + Haiku), Gemini (independent verifier), Qwen 2.5 (local PII), bge-m3 (embeddings), ChatGPT (plan review) | 4–6 |
| How & why LLMs | 5 | Role of each model and why | 7 |
| Retrieval & RAG | 12 | Vector DB (pgvector), Hybrid search, Knowledge graph, Corrective RAG, Query rewriting | 4, 6 |
| RAG architecture | 5 | One section = one chunk, RRF fusion, graph expansion, current-version filter | 4 |
| MCP Usage | 20 | ShromikAI MCP server (3 tools) + MCP servers used during development | 8 |
| Open Source Tools | 8 | Spring AI, pgvector, Ollama, Jsoup, PDFBox, Tesseract, n8n, Recharts | all |
| Agent Frameworks | 7 | Spring AI tool calling; risk agent + verifier with corrective retry | 6 |
| Fine-tuning | 5 | Not used (skip honestly) | — |
| Evaluation & Quality | 7 | Baseline vs ShromikAI, lawyer-reviewed labels, LLM-as-judge, regression runner | 10 |
| Guardrails & Privacy | 6 | Local PII redaction, no contract storage, schema validation, verifier, abstention | 5–6 |
| Frontend AI builders | 5 | Only the tools the designer actually uses (e.g. Lovable, v0, Claude Artifacts) | 9 |
| Workflow Automation | 4 (+2 n8n) | n8n weekly law-update workflow | 11 |
| Local LLMs | 8+ | Ollama runtime; Qwen 2.5 and bge-m3 models | 4–5 |
| AI-DLC | — | AGENTS.md, CLAUDE.md, phase specs (this repo's docs), review gate per phase | all |
| Live /docs module | rec. | /docs page in the app | 9 |

## Data Lifecycle (25)

| Form section | Our answer | Phase |
| --- | --- | --- |
| Data sources | Public web (official law portal), user uploads, open data (ILO), synthetic test contracts, internal DB | 3, 5, 10 |
| Acquisition | Web scraper (Jsoup), OCR (Tesseract), AI extraction (clause parsing), bulk upload, automated flow (n8n), MCP | 3, 5, 8, 11 |
| Parsing & formats | HTML, PDF, images, JSON; Bangla Unicode and digit normalisation; JSON Schema validation | 3, 5 |
| Storage | Relational (Postgres), Vector DB (pgvector); graph stored as edge tables | 1, 3 |
| Visualization | Recharts on /docs and results | 9 |
| Insights | LLM inference / RAG, rule engine (non-AI), statistical evaluation | 6, 10 |
| Pipelines | n8n + scheduled ingest | 11 |
| Outbound | REST API + OpenAPI, PDF export, MCP server | 7, 8 |
| Quality & governance | Corpus quality report, PII handling, trace (lineage), cost per analysis | 3, 5, 6, 7 |
| Tunneling | Cloudflare Tunnel for the MCP demo | 8 |

## Proof

| Item | Source |
| --- | --- |
| Test credentials for judges | Demo accounts from Phase 2 (worker / hr / admin) |
| Demo / live link | Phase 11 deploy |
| GitHub repo | This repository (make public before submitting) |
| YouTube video | Recorded after Phase 10 |
