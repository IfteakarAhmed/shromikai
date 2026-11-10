# Tooling disclosure

ShromikAI was built with the help of AI tools. The team reviews, runs and is responsible for all
AI-generated code, documents and output.

## Tools used to build ShromikAI

| Tool | Used for |
| --- | --- |
| Claude (Anthropic), including Claude Code | Project planning, the master prompt playbook, the specification (P0), architecture design, backend and frontend code, documentation |
| ChatGPT (OpenAI) | Second-opinion review of the project plan |

The key development prompts and their results are recorded in [dev-log.md](dev-log.md). The
rules AI coding agents must follow are in [AGENTS.md](../AGENTS.md).

## AI used at runtime

| Model | Where it runs | Used for | What it receives |
| --- | --- | --- | --- |
| qwen2.5:7b-instruct (via Ollama) | Our own server | Finding personal data (names, organisations, addresses, employee IDs) before anything leaves the server | Unredacted contract text chunks and MCP `verify_legal_citation` claims (never leave the server) |
| bge-m3 (via Ollama) | Our own server | Multilingual embeddings for statute search | Statute text, redacted clause queries, and free-text law-search queries (search never calls a cloud model) |
| Tesseract 5 OCR (Bangla + English) | Our own server | Reading photos and scanned pages | Page images (never leave the server) |
| Claude Haiku 5.5 (Anthropic API) | Cloud | Query rewriting, clause classification and fallback segmentation, clause facts, obligations checklist, fallback support check | Redacted text only |
| Claude Sonnet 5.5 (Anthropic API) | Cloud | Risk analysis, citation repair, counter-offers, Bangla and English memos, the plain-LLM baseline in compare mode | Redacted text and statute sections |
| Gemini Flash (Google Gemini API, optional) | Cloud | Independent check that a statute section supports a claim; clarity judge in evaluation (only after calibration) | The claim (redacted), the quote and the statute text; never contract clauses or search queries |

Model roles, versions and the reasons for each choice: [ADR-0005](adr/0005-model-routing.md).
Runtime prompts: [prompts/README.md](prompts/README.md). Provider data-retention settings for
the production accounts are recorded here at P11.
