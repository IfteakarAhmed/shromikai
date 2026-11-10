# ShromikAI

[![CI](https://github.com/IfteakarAhmed/shromikai/actions/workflows/ci.yml/badge.svg)](https://github.com/IfteakarAhmed/shromikai/actions/workflows/ci.yml)

**Verified employment contract intelligence for Bangladesh.**

ShromikAI reads an employment contract (Bangla or English), finds hidden legal risks, and cites only sections of Bangladeshi law that it has verified against the official text. If it cannot verify a source, it says so and sends the risk to human review instead of inventing a citation.

Built by team **Cosmic Duo** for the International AI Builders Congress 2026 — JusticeSphere AI → Legal Copilot.

> ShromikAI is not legal advice. Talk to a qualified lawyer before making important decisions.

## Start here

- **[AGENTS.md](AGENTS.md)**: engineering constitution for people and AI coding agents. Read it first.
- **[Specification](docs/spec/)**: [PRD](docs/spec/PRD.md), [architecture](docs/spec/architecture.md), [API](docs/spec/api.md), [test strategy](docs/spec/test-strategy.md), [evaluation](docs/spec/evaluation.md), [traceability](docs/spec/traceability.md).
- **[Architecture decisions](docs/adr/)**: ADR-0001 to ADR-0008.
- **[Master prompt playbook](docs/playbook/master-prompt-playbook.md)**: phase prompts P1 to P12, reviewer prompt R and fix prompt F.

## Stack

| Part | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Spring AI 1.1.8 (Ollama), Anthropic Java SDK (Claude), Gradle 8.14 (Groovy DSL), YAML configuration |
| Database | PostgreSQL 17 + pgvector, Flyway migrations |
| Reasoning models | Claude Sonnet 5.5 and Claude Haiku 5.5 (Anthropic API) |
| Independent verifier | Gemini Flash (optional key; falls back to a separate Claude prompt) |
| Local models | Ollama: bge-m3 (embeddings), qwen2.5:7b-instruct (personal-data detection) |
| OCR | Tesseract 5 (Bangla + English), runs locally |
| Frontend | React 19, TypeScript (strict), Vite, Tailwind CSS, TanStack Query, i18next (Bangla first) |
| Deploy | Docker Compose; Gotenberg (memo PDF), n8n (automation) |

## Run locally (macOS)

Prerequisites:

- **JDK 21** (for example `brew install --cask temurin@21`). Gradle 8.14 does not run on JDK 25,
  so the Gradle JVM must be 21 even if a newer JDK is your default.
- **Node.js 24** (`frontend/.nvmrc`; for example `brew install node@24` or `nvm use`).
- **Docker Desktop**, running.
- IntelliJ IDEA for the backend, VS Code for the frontend.

### 1. Services and environment

```bash
cp .env.example .env
```

```bash
./scripts/dev-up.sh
```

`dev-up.sh` starts PostgreSQL (pgvector) and Ollama, waits for both health checks and pulls
`bge-m3` and `qwen2.5:7b-instruct`. The first pull downloads several GB. Until both models are
present, `/actuator/health` reports `ollama` as `DOWN`; the rest of the app still runs.

No API key is needed in P1: cloud calls stay blocked until the privacy shield lands in P5.

### 2. Backend in IntelliJ IDEA

1. **File → Open** and choose the `backend/` folder, then trust the Gradle project.
2. **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**: pick JDK 21.
   **File → Project Structure → Project SDK**: JDK 21 as well.
3. **Run → Edit Configurations… → + → Gradle**. Name it `bootRun`, set *Tasks and arguments* to
   `bootRun` and *Gradle project* to `shromikai-backend`.
4. Environment variables go in the same dialog, in the **Environment variables** field (click the
   list icon to edit them one per line). Copy the values from your `.env`, for example
   `ANTHROPIC_API_KEY=…;GEMINI_API_KEY=…;JWT_SECRET=…`. IntelliJ does not read `.env` by itself.
   Leave a model key empty if you do not have it yet, but **`JWT_SECRET` is required from P2**
   (at least 32 bytes; `openssl rand -base64 48`), or the backend refuses to start. To use the
   one-click judge accounts locally, also set `SPRING_PROFILES_ACTIVE=demo,dev` and
   `DEMO_PASSWORD` (12 or more characters). Never commit the run configuration with secrets.
5. Run `bootRun`. The backend listens on <http://localhost:8080>; check
   <http://localhost:8080/api/v1/meta> and <http://localhost:8080/actuator/health>.

From a terminal instead (make sure `JAVA_HOME` points to JDK 21):

```bash
cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew bootRun
```

### 3. Frontend in VS Code

1. **File → Open Folder…** and choose `frontend/`. Install the suggested *ESLint*, *Prettier* and
   *Tailwind CSS IntelliSense* extensions.
2. In the integrated terminal (**Terminal → New Terminal**):

```bash
npm install
```

```bash
npm run dev
```

3. Open <http://localhost:5173>. Vite forwards `/api` to the backend. The page shows
   **চালু (সংস্করণ 0.1.0)** when the backend is running, and a Bangla error message with a retry
   button when it is not.

### Quality gates (what CI runs)

```bash
cd backend && ./gradlew check
```

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

`./gradlew check` runs unit and integration tests (Testcontainers, so Docker must be running),
ArchUnit module rules, the JUnit tag rules, the log-key allowlist, `promptLint`, the fixture
scanner, Spotless and the JaCoCo coverage gates. After an API change, refresh the OpenAPI snapshot
and the generated frontend types:

```bash
cd backend && ./gradlew integrationTest --tests '*OpenApiSnapshotIT' -PupdateOpenApi
```

```bash
cd frontend && npm run gen:api
```

The full list of commands, including tests and evaluation, is in
[AGENTS.md §11](AGENTS.md#11-how-to-run-everything).

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
