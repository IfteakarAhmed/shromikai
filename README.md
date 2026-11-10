# ShromikAI

**Verified employment contract intelligence for Bangladesh.**

ShromikAI reads an employment contract (Bangla or English), finds hidden legal risks, and cites only sections of Bangladeshi law that it has verified against the official text. If it cannot verify a source, it says so and sends the risk to human review instead of inventing a citation.

Built by team **Cosmic Duo** for the International AI Builders Congress 2026 — JusticeSphere AI → Legal Copilot.

> ShromikAI is not legal advice. Talk to a qualified lawyer before making important decisions.

## Start here

- **[AGENTS.md](AGENTS.md)**: engineering constitution for people and AI coding agents. Read it first.
- **[Specification](docs/spec/)**: [PRD](docs/spec/PRD.md), [architecture](docs/spec/architecture.md), [API](docs/spec/api.md), [test strategy](docs/spec/test-strategy.md), [evaluation](docs/spec/evaluation.md), [traceability](docs/spec/traceability.md).
- **[Architecture decisions](docs/adr/)**: ADR-0001 to ADR-0005.
- **[Master prompt playbook](docs/playbook/master-prompt-playbook.md)**: phase prompts P1 to P12, reviewer prompt R and fix prompt F.

## Stack

| Part | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring AI 1.x, Gradle (Groovy DSL), YAML configuration |
| Database | PostgreSQL 17 + pgvector, Flyway migrations |
| Reasoning models | Claude Sonnet 5.5 and Claude Haiku 5.5 (Anthropic API) |
| Independent verifier | Gemini Flash (optional key; falls back to a separate Claude prompt) |
| Local models | Ollama: bge-m3 (embeddings), qwen2.5:7b-instruct (personal-data detection) |
| OCR | Tesseract 5 (Bangla + English), runs locally |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS |
| Deploy | Docker Compose; Gotenberg (memo PDF), n8n (automation) |

## Run locally

Prerequisites: Java 21, Node.js 20+, Docker Desktop, Git.

```bash
# 1. Secrets
cp .env.example .env          # then put your ANTHROPIC_API_KEY in .env

# 2. Database + local models
docker compose up -d
docker compose exec ollama ollama pull bge-m3
docker compose exec ollama ollama pull qwen2.5:7b-instruct   # from P1: ./scripts/dev-up.sh does both

# 3. Backend (new terminal)
cd backend
./gradlew bootRun             # Windows: gradlew.bat bootRun

# 4. Frontend (new terminal)
cd frontend
npm install
npm run dev                   # open http://localhost:5173
```

Check: `http://localhost:8080/api/v1/meta` returns JSON, and the web page shows "চালু".

The full list of commands, including tests and evaluation, is in [AGENTS.md §11](AGENTS.md#11-how-to-run-everything).

## Repository layout

```
backend/     Spring Boot API, AI pipeline, citation verifier
frontend/    React web app
data/        Jurisdiction packs, legal rules and mappings (data/packs, data/rules, data/mappings)
eval/        Synthetic contracts, labels, retrieval questions and evaluation reports
docs/spec/   Specification (source of truth for every build phase)
docs/adr/    Architecture decision records
docs/        Playbook, prompt library, tooling disclosure, team, dev log
```

## Documentation

- [Prompt library](docs/prompts/README.md)
- [Tooling disclosure](docs/tooling-disclosure.md)
- [Team](docs/team.md)
- [Development log](docs/dev-log.md)
