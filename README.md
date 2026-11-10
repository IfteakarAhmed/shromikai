# ShromikAI

**Verified employment contract intelligence for Bangladesh.**

ShromikAI reads an employment contract (Bangla or English), finds hidden legal risks, and cites only sections of Bangladeshi law that it has verified against the official text. If it cannot verify a source, it says so and sends the risk to human review instead of inventing a citation.

Built by team **Cosmic Duo** for the International AI Builders Congress 2026 — JusticeSphere AI → Legal Copilot.

> ShromikAI is not legal advice. Talk to a qualified lawyer before making important decisions.

## Stack

| Part | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring AI, Gradle |
| Database | PostgreSQL 17 + pgvector, Flyway migrations |
| LLM | Claude (Anthropic API) |
| Embeddings | bge-m3 via Ollama (local, multilingual) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS |
| Deploy | Docker |

## Run locally

Prerequisites: Java 21, Node.js 20+, Docker Desktop, Git.

```bash
# 1. Secrets
cp .env.example .env          # then put your ANTHROPIC_API_KEY in .env

# 2. Database + embedding model
docker compose up -d
docker compose exec ollama ollama pull bge-m3

# 3. Backend (new terminal)
cd backend
./gradlew bootRun             # Windows: gradlew.bat bootRun

# 4. Frontend (new terminal)
cd frontend
npm install
npm run dev                   # open http://localhost:5173
```

Check: `http://localhost:8080/api/v1/meta` returns JSON, and the web page shows "চালু".

## Repository layout

```
backend/    Spring Boot API, AI pipeline, citation verifier
frontend/   React web app
data/       Law corpus scripts and source data
eval/       Test contracts and evaluation results
docs/       Architecture, prompt library, tooling disclosure, dev log
```

## Documentation

- [Architecture](docs/architecture.md)
- [Prompt library](docs/prompts/README.md)
- [Tooling disclosure](docs/tooling-disclosure.md)
- [Development log](docs/dev-log.md)
