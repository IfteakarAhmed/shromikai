# ShromikAI: Traceability Matrix

| Field | Value |
| --- | --- |
| Spec version | v1.1 (tag `spec-v1.1`), aligned with the master prompt playbook v1.2 |
| Purpose | How each judging criterion, event rule and submission-form section is met, by which requirements, and which artefact proves it |
| Status values | `Specified` (in the specification, `spec-v1.1`), `Implemented` (code merged), `Verified` (proof artefact exists and passes) |

IDs refer to [`PRD.md`](PRD.md) (FR, NFR, KPI), [`evaluation.md`](evaluation.md) (M),
[`architecture.md`](architecture.md) §13 (T) and [`AGENTS.md`](../../AGENTS.md) (R). Update the
status column at every phase exit; the P12 fact-check uses this file as its checklist.

## 1. Judging criteria

| Criterion (points) | What judges look for | Requirements | Proof artefact | Phase | Status |
| --- | --- | --- | --- | --- | --- |
| **Innovation (20)** | Verification-first legal AI | FR-42 to FR-47; ADR-0002 | `eval/reports/report.md` M-01, M-04, M-05; `scripts/demo-fake-citation.sh` | P6, P10 | Specified |
| | A generic LLM invents a section, ShromikAI catches it | FR-59, FR-72 | `/app/compare`; Playwright `j5-compare`; M-03 baseline vs ShromikAI | P7, P9 | Specified |
| | Citations restricted to retrieved sections; corrective agentic RAG | FR-21, FR-37, FR-42, FR-46 | `CitationOutsideRetrievedSetTest`, `FakeCitationInjectionTest` | P4, P6 | Specified |
| | Deterministic rule engine beside LLM reasoning | FR-35, FR-36, FR-41, FR-49 | `RuleEngineTest`; `no-rules` ablation | P6, P10 | Specified |
| | Bilingual statute reasoning with ILO alignment | FR-12, FR-16, FR-21 | M-13; reviewed `ALIGNS_WITH` rows; `retrieval.md` | P3, P4, P10 | Specified |
| | Local-first privacy | FR-28 to FR-32; ADR-0003 | M-16, M-17; `EgressPlumbingIT` | P5 | Specified |
| | MCP server for any AI agent | FR-64 to FR-67 | `McpServerIT`; `docs/mcp.md`; screenshots `docs/img/mcp-*.png` | P8 | Specified |
| **Technical Execution (20)** | Input → processing → output pipeline as an explicit state machine | FR-48; architecture §5 | `OrchestratorStateMachineTest`; trace viewer | P6, P9 | Specified |
| | Model choice with reasons | ADR-0005; FR-62 | architecture §6; `/docs/architecture` | P0, P9 | Specified |
| | Hybrid RAG + knowledge graph | FR-18 to FR-23 | `eval/reports/retrieval.md` ablation | P4 | Specified |
| | Explainability trace | FR-48, FR-70, FR-73 | "কেন?" drawer; admin trace viewer | P6, P9 | Specified |
| | Tests, CI and coverage | NFR-15; `test-strategy.md` §16 | CI badge, JaCoCo report, `requirementsReport` | P1 onward | Specified |
| | Performance and cost | NFR-01 to NFR-03; FR-62 | M-19, M-20; `docs/perf.md` | P10, P11 | Specified |
| | Production-grade safeguards (top band) | R-01 to R-12; architecture §10, §13 | This spec set; privacy and verifier tests; threat-model test column | P0 onward | Specified |
| **Business Model + Global Readiness (20)** | Who pays and why | PRD §11; FR-09, FR-57, FR-65 | `/docs/pitch`; `docs/submission/pitch-deck.md` | P7, P12 | Specified |
| | Unit economics from measured cost | NFR-03; PRD §9.1, §11 | M-20 in `report.md`; cost dashboard | P9, P10 | Specified |
| | Market validation | PRD §11 | `docs/validation/` interviews and letters of interest | P12 | Specified |
| | API and MCP channel for law firms, NGOs, job portals | FR-58, FR-63 to FR-66 | `/swagger-ui`; `docs/mcp.md` | P7, P8 | Specified |
| | Expansion through jurisdiction packs | NFR-17; architecture §11.4 | `JurisdictionPackIT`; `list_jurisdiction_packs` | P8, P11 | Specified |
| | Localisation | R-11, FR-75, NFR-09 | i18n parity; Playwright `locale` | P9 | Specified |
| **Real-World Impact + Ethical AI (20)** | Workers on phones, low bandwidth, no account | J1; FR-09, FR-69, FR-76, NFR-10, NFR-11 | Playwright `j1-worker`, `perf-landing`; screenshots in `docs/img/` | P2, P9 | Specified |
| | Plain-Bangla explanations | FR-51, FR-56 | M-21; `PlainLanguageLintTest` | P7, P10 | Specified |
| | Actionable outputs: redlines and obligations checklist | FR-41, FR-52, FR-53 | M-27, M-28 in `report.md`; `CounterOfferRuleCheckIT`, `ObligationsExportIT` | P6, P7, P10 | Specified |
| | Bias testing by language and side | FR-40; NFR-21 | M-13, M-14 in `report.md` | P6, P10 | Specified |
| | Honest uncertainty and human in the loop | FR-45 to FR-47, FR-50, FR-60, FR-61 | Playwright `j3-admin`; M-11, M-12 | P6, P7, P9 | Specified |
| | Privacy by design | NFR-05, NFR-06; ADR-0003 | M-16, M-17; `/docs/security` | P5, P9 | Specified |
| | Audit and transparency | FR-10, FR-74, FR-77 | `/docs/audit` from live data; `/docs/prompts` | P2, P9 | Specified |
| | Accessibility | NFR-08 | axe-core results; screen-reader notes in the P9 report | P9 | Specified |
| | Security and abuse resistance | FR-05 to FR-11, NFR-07, NFR-20; T-01 to T-22 | `RoleAccessIT`, `RateLimitIT`, `AbuseLimitsIT`; Dependency-Check | P2, P7, P11 | Specified |
| **Scalability + NRB collaboration (10)** | Docker, stateless backend, multi-tenant | NFR-12; architecture §16 | `docker-compose.prod.yml`; `docs/scalability.md`; k6 in `docs/perf.md` | P11 | Specified |
| | Jurisdiction packs and ILO standards | NFR-17; FR-12, FR-16 | `list_jurisdiction_packs`; reviewed ILO mapping | P3, P8 | Specified |
| | NRB advisor contribution | PRD §11 expansion item 2 | `docs/team.md` contributions log (dated entries, never back-filled) | All | Specified |
| **Presentation (10)** | 3-minute video: problem → solution → demo → AI → impact | FR-89 | `docs/submission/video-script.md`; video URL in `proof.md` | P12 | Specified |
| | Live trace explains AI decisions | FR-70, FR-73 | Demo of the "কেন?" drawer and trace viewer | P9, P12 | Specified |
| | Every claim backed by evidence | R-07, R-12; evaluation §11 | P12 fact-check table | P12 | Specified |

## 2. Event rules

| Rule | Requirements | Proof artefact | Status |
| --- | --- | --- | --- |
| No fabricated demos or results | R-07, R-12; evaluation §11 | Eval reports with config and git SHA; `recorded` label on replays; P12 fact-check | Specified |
| Lawfully sourced data | FR-12 (official sources, `robots.txt`, 1 request per second), FR-17; evaluation §2.1 | `data/packs/bd.yaml`; `corpus-quality.md`; contract `.meta.yml` source and licence | Specified |
| Personal data anonymised | FR-28 to FR-32, FR-50, FR-66; NFR-05, NFR-06 | M-16, M-17 = 0; `EgressPlumbingIT`, `NoContractPersistenceIT` | Specified |
| All AI usage explainable and documented | FR-48, FR-74, FR-77; R-05; ADR-0005 | `docs/prompts/`, `docs/tooling-disclosure.md`, `docs/dev-log.md`, trace viewer | Specified |
| Bangla localisation | R-11, FR-51, FR-75, NFR-09 | Bangla UI, memos and SSE messages; M-21 | Specified |
| Judges get test credentials | FR-08, FR-85 | `/demo-user`, `/demo-hr`, `/demo-admin`; credentials table in `docs/submission/proof.md` (passwords never committed) | Specified |
| Team of 3 to 5 named members with roles | PRD RK-12 | `docs/team.md` | Specified |

### 2.1 Team bonuses (participants guide §13.3, as summarised in playbook §4)

Bonuses are claimed only for facts the team can show; `docs/team.md` records them and the P12
checklist ticks a bonus only when its evidence exists (R-12).

| Bonus | Requirement | Proof artefact | Owner | Status |
| --- | --- | --- | --- | --- |
| At least one female team member | PRD RK-12; `docs/team.md` composition rule | `docs/team.md` role table (name and role); declared in the submission form's Team section | Team Leader | Open (names are `TODO`) |
| Member from a polytechnic or madrasah background | `docs/team.md` composition rule | `docs/team.md` role table with the member's institution type, stated only with the member's consent | Team Leader | Open |
| NRB advisor contribution documented | PRD §11 expansion item 2 | `docs/team.md` NRB advisor contributions log (date, type, artefact, hours, acknowledged by) | Team Leader, NRB advisor | Open (no entries yet) |

## 3. Submission-form sections

### 3.1 Story, Proof, Prompt Library, Team

| Section | Content | Requirements | Source artefacts |
| --- | --- | --- | --- |
| **Story** | Name, pitch (≤ 300 characters), problem with sourced statistics, solution | PRD §1 to §4, §7; FR-89 | `docs/submission/story.md` |
| **Proof** | Demo URL, judge credentials, GitHub URL, video URL, measured results vs baseline | FR-08, FR-85, FR-89; KPI-01 to KPI-11 | `docs/submission/proof.md`; `eval/reports/report.md`; CI run |
| **Prompt Library** | Runtime prompts with front matter, schema, eval link and history; build prompts with dates | R-05; AGENTS §5 | `docs/prompts/`; `docs/playbook/master-prompt-playbook.md`; `docs/submission/prompt-library.md` |
| **Team** | Named roles, NRB advisor contributions | PRD RK-12 | `docs/team.md` |

### 3.2 AI-Native Build

| Form section | Our answer | Requirements | Evidence | Phase |
| --- | --- | --- | --- | --- |
| Prompt Usage | Versioned XML-structured prompts with JSON schemas, citation-restricted prompting, abstention instructions | R-05, R-06; FR-37 | `backend/src/main/resources/prompts/`, `promptLint` | P6, P7 |
| Token Optimization | Prompt caching, cheap-model routing, structured outputs, retrieval cache, short redacted context, cost ledger | FR-21, FR-62 | Trace cost and cache-read tokens; M-20 | P4, P7 |
| LLMs / Models | Claude Sonnet 5.5 and Haiku 5.5, Gemini Flash, Qwen 2.5 7B (local), bge-m3 (local); ChatGPT for plan review | ADR-0005 | `docs/tooling-disclosure.md` | P4 to P6 |
| How and why LLMs | Role of each model and why | ADR-0005 | architecture §6; `/docs/architecture` | P7, P9 |
| Retrieval and RAG | pgvector, hybrid search, knowledge graph, corrective RAG, query rewriting | FR-18 to FR-21, FR-46 | `retrieval.md` | P4, P6 |
| RAG architecture | One section = one chunk, RRF fusion, graph expansion, current-version filter | FR-18 to FR-20 | architecture §8 | P4 |
| MCP Usage | ShromikAI MCP server (5 tools, 1 resource template, 1 prompt template; Streamable HTTP; API-key auth) | FR-64 to FR-66 | `McpServerIT`, `McpToolSchemaTest`; `docs/mcp.md` | P8 |
| MCP Usage: servers used during development | An honest list of the MCP servers the team actually used while building (for example the coding assistant's browser, documentation or repository servers), each with what it was used for and in which phase; nothing listed that was not used | FR-67; R-12 | `docs/mcp.md` "MCP servers used during development" section; `docs/tooling-disclosure.md`; `docs/dev-log.md` entries | P8, updated at P12 |
| Open Source Tools | Spring AI, pgvector, Ollama, jsoup, PDFBox, Tesseract, n8n, Recharts, java-diff-utils, Gotenberg | ADR-0001 | `build.gradle`, `package.json`, compose files | All |
| Agent Frameworks | Spring AI (Ollama models, MCP server); code-orchestrated state machine with risk agent, counter-offer agent and verifier; agents have no tools | FR-48; R-06 | architecture §5; `OrchestratorStateMachineTest` | P6 |
| Fine-tuning | Not used | PRD §12 item 13 | n/a | n/a |
| Evaluation and Quality | Baseline vs ShromikAI, reviewed labels, calibrated LLM-as-judge, CI eval-smoke | FR-79 to FR-83 | `eval/reports/report.md` | P10 |
| Guardrails and Privacy | Local PII redaction, egress guard, prompt-injection defence and tests, schema validation, verifier, abstention | FR-28 to FR-34, FR-42 to FR-47 | Privacy and safety tests (test-strategy §10) | P5, P6 |
| Frontend AI builders | Only tools the designer actually uses | FR-78 | `docs/ai-tools.md` | P9 |
| Workflow Automation | n8n `legislative-watch` | FR-84 | `automation/n8n/legislative-watch.json`; run screenshot | P11 |
| Local LLMs | Ollama runtime with Qwen 2.5 and bge-m3 | FR-18, FR-29 | compose files; `scripts/dev-up.sh` | P1, P5 |
| AI-DLC | `AGENTS.md`, `CLAUDE.md`, spec-driven development (`docs/spec/`), ADRs, playbook with reviewer gate per phase | AGENTS §7, §10 | tag `spec-v1`; phase tags; `docs/dev-log.md` | P0 onward |

### 3.3 Data Lifecycle

| Form section | Our answer | Requirements | Evidence | Phase |
| --- | --- | --- | --- | --- |
| Data sources | Official law portal, ILO NORMLEX, user uploads (memory only), synthetic contracts and public templates, internal DB | FR-12, FR-24, FR-79 | `data/packs/bd.yaml`; `eval/contracts/*.meta.yml` | P3, P5, P10 |
| Acquisition | Polite scraper, OCR, AI extraction, n8n, MCP | FR-12, FR-27, FR-35, FR-64, FR-84 | `ScraperPolicyTest`; workflow JSON | P3, P5, P8, P11 |
| Parsing and cleaning | HTML, PDF, images, JSON; Bangla Unicode and digit normalisation; legacy-font conversion; JSON Schema validation | FR-13, FR-14, FR-25, FR-26; R-04 | `SectionParserGoldenTest`, `BanglaNormalizerPropertyTest` | P3, P5 |
| Storage | PostgreSQL, pgvector, graph as edge tables; no contract text | ADR-0004; R-02 | architecture §11 | P1, P3 |
| Visualisation and insight | Recharts dashboards; LLM + RAG; rule engine (non-AI); statistical evaluation | FR-73, FR-74, FR-36, FR-80 | `/admin/usage`, `/docs/evaluation` | P6, P9, P10 |
| Pipelines | Scheduled ingestion through n8n; analysis state machine | FR-48, FR-84 | architecture §5, §14 | P6, P11 |
| Outbound | REST API + OpenAPI, PDF and JSON export, MCP server | FR-53, FR-55, FR-63, FR-64 | `/swagger-ui`; `docs/mcp.md` | P7, P8 |
| Quality and governance | Corpus quality report, lineage (source URL, hash, release), trace, cost per analysis, retention schedule | FR-15, FR-17, FR-48, FR-62; NFR-16 | `corpus-quality.md`; architecture §14 | P3, P6, P7 |
| Tunnelling | Cloudflare Tunnel for the MCP demo (dev only) | FR-67 | `docs/mcp.md` | P8 |

## 4. Requirement index

Every FR and NFR maps to at least one judging criterion. "Proof test" names the test or measurement
that proves the requirement (from its acceptance criterion in `PRD.md` and test-strategy §16); the
status becomes `Verified` only when that proof exists and passes.

| ID | Short name | Primary criterion | Phase | Proof test | Status |
| --- | --- | --- | --- | --- | --- |
| FR-01 | Meta endpoint | Technical Execution | P1 | `MetaControllerIT` | Specified |
| FR-02 | Problem+json errors | Technical Execution | P1 | `ProblemDetailsIT` | Specified |
| FR-03 | Request IDs, structured logs, log redaction | Ethical AI | P1 | `RequestIdFilterTest`, `LogRedactionTest`, `LogAllowlistTest` | Specified |
| FR-04 | Health indicators | Technical Execution | P1 | `HealthIndicatorsIT` | Specified |
| FR-05 | JWT authentication | Ethical AI (security) | P2 | `AuthControllerIT`, `JwtServiceTest` | Specified |
| FR-06 | Roles and access rules | Ethical AI (security) | P2 | `RoleAccessIT` | Specified |
| FR-07 | Lockout and rate limits | Ethical AI (security) | P2 | `AuthControllerIT` (lockout), `RateLimitIT` | Specified |
| FR-08 | One-click demo access | Presentation | P2 | `DemoLoginIT` | Specified |
| FR-09 | Guest access and quotas | Real-World Impact | P2 | `GuestTokenIT` | Specified |
| FR-10 | Audit events | Ethical AI | P2 | `AuditEventIT` | Specified |
| FR-11 | Security headers and CORS | Technical Execution | P2 | `SecurityHeadersIT` | Specified |
| FR-12 | Official sources, polite scraper | Ethical AI (lawful data) | P3 | `ScraperPolicyTest` | Specified |
| FR-13 | Section parsing and versions | Technical Execution | P3 | `SectionParserGoldenTest`, `AmendmentParserTest` | Specified |
| FR-14 | BanglaNormalizer | Innovation | P3 | `BanglaNormalizerPropertyTest` | Specified |
| FR-15 | Idempotent ingestion, corpus releases | Technical Execution | P3 | `IngestionIdempotencyIT` | Specified |
| FR-16 | Knowledge graph and reviewed ILO mapping | Innovation | P3 | `CrossReferenceExtractorTest`, `IloMappingLoaderTest` | Specified |
| FR-17 | Corpus quality report | Technical Execution | P3 | `QualityReportTest`; manual 5-section comparison | Specified |
| FR-18 | Section embeddings | Technical Execution | P4 | `EmbeddingBackfillIT` | Specified |
| FR-19 | Hybrid search with RRF | Technical Execution | P4 | `RrfFusionTest`, `HybridSearchIT` | Specified |
| FR-20 | Graph expansion and topic map | Innovation | P4 | `GraphExpansionIT` | Specified |
| FR-21 | Query rewriting and cache | Technical Execution | P4 | `QueryRewriteContractTest`, `RetrievalCacheTest` | Specified |
| FR-22 | Law endpoints | Business Model | P4 | `LawApiIT` | Specified |
| FR-23 | Retrieval evaluation and ablation | Technical Execution | P4 | `RetrievalEvalRunnerIT`; `eval/reports/retrieval.md` | Specified |
| FR-24 | Accepted inputs and magic bytes | Technical Execution | P5 | `MagicBytesTest` | Specified |
| FR-25 | Text extraction and cleaning | Technical Execution | P5 | `PdfTextExtractorIT` | Specified |
| FR-26 | Legacy Bijoy conversion | Real-World Impact | P5 | `BijoyConverterTest` | Specified |
| FR-27 | Local OCR with quality gate | Real-World Impact | P5 | `OcrIT` | Specified |
| FR-28 | Deterministic PII detectors | Ethical AI | P5 | `PiiDetectorTest` | Specified |
| FR-29 | Local entity extraction and placeholders | Ethical AI | P5 | `RedactionRoundTripTest`; M-25 | Specified |
| FR-30 | Egress guard | Ethical AI | P5 | `EgressGuardTest`, `EgressPlumbingIT` | Specified |
| FR-31 | Fail closed | Ethical AI | P5 | `FailClosedIT` | Specified |
| FR-32 | No contract persistence | Ethical AI | P5 | `NoContractPersistenceIT` | Specified |
| FR-33 | Clause segmentation and classification | Technical Execution | P5 | `ClauseSegmenterTest`, `DocumentGateIT`, `DevEndpointIT` | Specified |
| FR-34 | Prompt-injection defence | Ethical AI | P5 | `PromptInjectionTest`, `PromptRendererTest` | Specified |
| FR-35 | Fact extraction with regex cross-check | Innovation | P6 | `FactExtractorTest`; M-22 | Specified |
| FR-36 | Rule engine with fail-fast YAML | Innovation | P6 | `RuleLoaderFailFastTest`, `RuleEngineTest` | Specified |
| FR-37 | Risk agent with constrained citations | Innovation | P6 | `RiskAgentSchemaTest`, `GoldenAnalysisIT`; M-06, M-07 | Specified |
| FR-38 | Non-obvious coverage, never padded | Real-World Impact | P6 | `compliant-contract` fixture in `GoldenAnalysisIT`; M-08, M-09 | Specified |
| FR-39 | Severity rubric | Real-World Impact | P6 | Prompt golden file `golden/prompts/risk-analysis.v1.txt`; M-10 | Specified |
| FR-40 | Side symmetry | Ethical AI (bias) | P6 | `SideSymmetryIT`; M-14 | Specified |
| FR-41 | Counter-offer agent | Real-World Impact | P6 | `CounterOfferRuleCheckIT`; M-27 | Specified |
| FR-42 | Existence and candidate check | Innovation | P6 | `CitationOutsideRetrievedSetTest`, `ExistenceCheckTest` | Specified |
| FR-43 | Quote match | Innovation | P6 | `QuoteMatchTest` | Specified |
| FR-44 | Independent support check | Innovation | P6 | `SupportCheckContractTest`, `VerifierInputTest` | Specified |
| FR-45 | Decision table (no WEAK) | Innovation | P6 | `VerificationDecisionTableTest` | Specified |
| FR-46 | Corrective loop | Innovation | P6 | `FakeCitationInjectionTest`; `scripts/demo-fake-citation.sh` | Specified |
| FR-47 | Only verified citations shown | Innovation | P6 | `AllResponsesCarryOnlyVerifiedCitationsIT`, `ArchitectureTest`, `LiveSmokeIT`; M-01 | Specified |
| FR-48 | Orchestrator state machine | Technical Execution | P6 | `OrchestratorStateMachineTest` | Specified |
| FR-49 | Finding merge | Technical Execution | P6 | `FindingMergeTest` | Specified |
| FR-50 | Review items and ticket | Ethical AI | P6 | `ReviewItemFactoryTest` | Specified |
| FR-51 | Memo composer | Real-World Impact | P7 | `MemoComposerContractTest`, `PlainLanguageLintTest`; M-21 | Specified |
| FR-52 | Redline diff | Real-World Impact | P7 | `RedlineDiffTest` | Specified |
| FR-53 | Obligations checklist and export | Real-World Impact | P7 | `ObligationsExportIT`; M-28 | Specified |
| FR-54 | 30-minute in-memory result | Ethical AI | P7 | `ResultTtlIT`, `ReidentificationTest`, `AnalysisAccessIT` | Specified |
| FR-55 | Memo PDF and signed envelope | Technical Execution | P7 | `MemoPdfIT`, `MemoEnvelopeTest`, `MemoFromRealResponseIT`, `MemoTemplateTest` | Specified |
| FR-56 | Personalisation profile | Real-World Impact | P7 | `ProfilePersonalisationTest` | Specified |
| FR-57 | HR playbooks | Business Model | P7 | `PlaybookRuleTest`, `PlaybookApiIT`, `CrossOrganisationIsolationIT` | Specified |
| FR-58 | Analyses API and SSE | Technical Execution | P7 | `AnalysesApiIT`, `sse.test.ts` | Specified |
| FR-59 | Compare API | Innovation | P7 | `CompareApiIT` | Specified |
| FR-60 | Review API | Ethical AI | P7 | `ReviewDecisionIT` | Specified |
| FR-61 | Review ticket status | Ethical AI | P7 | `ReviewTicketIT` | Specified |
| FR-62 | Cost and latency control | Business Model | P7 | `CostLedgerTest`, `BudgetIT` | Specified |
| FR-63 | OpenAPI | Business Model | P7 | `OpenApiContractTest` | Specified |
| FR-64 | MCP tools, resources, prompt | Innovation | P8 | `McpToolSchemaTest`, `McpServerIT` | Specified |
| FR-65 | API keys and metering | Business Model | P8 | `McpAuthIT`, `McpRateLimitIT`, `CrossOrganisationIsolationIT` | Specified |
| FR-66 | MCP analysis privacy | Ethical AI | P8 | `McpPrivacyIT` | Specified |
| FR-67 | MCP documentation and demo tunnel | Presentation | P8 | Manual: MCP Inspector and Claude Desktop; `docs/img/mcp-*.png` | Specified |
| FR-68 | Routes and navigation | Real-World Impact | P9 | Playwright `j1-worker`, protected-route test | Specified |
| FR-69 | Upload flow and compression | Real-World Impact | P9 | Vitest `UploadForm`, image compression, MSW SSE order | Specified |
| FR-70 | Risk cards, badges, citation chips, why drawer | Ethical AI | P9 | Vitest `VerificationBadge`, `RiskCard`, `CitationChip` | Specified |
| FR-71 | Redline view, print, share | Real-World Impact | P9 | Playwright memo print view; share-button test | Specified |
| FR-72 | Compare mode UI | Innovation | P9 | Playwright `j5-compare` | Specified |
| FR-73 | Admin console | Technical Execution | P9 | Playwright `j3-admin`, `AdminUsersIT`, `UsageKpiIT` | Specified |
| FR-74 | `/docs` hub | Presentation | P9 | MSW `/docs/audit` test; CI grep for hard-coded metrics | Specified |
| FR-75 | Bilingual UI | Global Readiness | P9 | i18n parity check; Playwright `locale` | Specified |
| FR-76 | Low-bandwidth mode and read-aloud | Real-World Impact | P9 | Vitest `LowBandwidth`, read-aloud visibility | Specified |
| FR-77 | Disclaimer and AI transparency | Ethical AI | P9 | UI snapshot tests; memo golden file; `McpServerIT` disclaimer assertion | Specified |
| FR-78 | Account deletion, AI-builder record | Ethical AI | P9 | `AccountDeletionIT` | Specified |
| FR-79 | Eval dataset and labels | Technical Execution | P10 | `LabelSchemaTest` | Specified |
| FR-80 | Evaluation runner and reports | Technical Execution | P10 | `MetricFormulaTest`, `EvalRunnerSmokeIT`, `ReportGeneratorTest` | Specified |
| FR-81 | Calibrated LLM-as-judge | Technical Execution | P10 | `JudgeCalibrationTest` | Specified |
| FR-82 | Observability | Technical Execution | P10 | `MetricsIT` | Specified |
| FR-83 | CI eval-smoke | Technical Execution | P10 | CI `eval-smoke` job (seeded regression turns it red) | Specified |
| FR-84 | n8n legislative watch | Scalability | P11 | `N8nWorkflowContractTest`, `ReingestOnChangeIT`, `RetrievalSmokeServiceIT` | Specified |
| FR-85 | Production deployment with demo profile | Presentation | P11 | Manual: public HTTPS URL and one-click demo logins | Specified |
| FR-86 | Hardening | Ethical AI (security) | P11 | CI Dependency-Check and secret scanning; restore drill in `docs/runbook.md` | Specified |
| FR-87 | Load test | Scalability | P11 | k6 thresholds; `docs/perf.md` | Specified |
| FR-88 | Runbook and scalability docs | Scalability | P11 | Section checklist for `docs/runbook.md` and `docs/scalability.md` | Specified |
| FR-89 | Submission package with fact-check | Presentation | P12 | P12 fact-check table | Specified |
| NFR-01 | Latency | Technical Execution | P6, P10 | M-19 per input kind and stage | Specified |
| NFR-02 | Responsiveness | Technical Execution | P11 | k6 (FR-87) | Specified |
| NFR-03 | Cost | Business Model | P7, P10 | `CostLedgerTest`; M-20 | Specified |
| NFR-04 | Availability | Scalability | P11 | Uptime probe (n8n `uptime-probe` or provider monitor) | Specified |
| NFR-05 | No content at rest | Ethical AI | P5 | `NoContractPersistenceIT`; M-17 | Specified |
| NFR-06 | No PII egress | Ethical AI | P5 | `EgressPlumbingIT`; M-16 | Specified |
| NFR-07 | Security | Technical Execution | P11 | Dependency-Check, `npm audit`; `docs/security/asvs-l1.md` | Specified |
| NFR-08 | Accessibility | Real-World Impact | P9 | Playwright `a11y`, `keyboard-j1` | Specified |
| NFR-09 | Bangla first | Global Readiness | P9 | i18n parity check; snapshot tests | Specified |
| NFR-10 | Low bandwidth | Real-World Impact | P9 | CI bundle report; Playwright `perf-landing` | Specified |
| NFR-11 | Device support | Real-World Impact | P9 | Playwright browser projects | Specified |
| NFR-12 | Scalability | Scalability | P11 | k6 (FR-87); `CrossOrganisationIsolationIT` | Specified |
| NFR-13 | Reproducibility | Technical Execution | P7 | `ProvenanceIT` | Specified |
| NFR-14 | Explainability | Ethical AI | P9 | `TraceCoverageIT` | Specified |
| NFR-15 | Maintainability | Technical Execution | P1 | `ArchitectureTest`, `TestTaggingRulesTest`, JaCoCo gates | Specified |
| NFR-16 | Data retention | Ethical AI | P7 | `RetentionJobIT` | Specified |
| NFR-17 | Global readiness (packs) | Global Readiness | P11 | `JurisdictionPackIT` | Specified |
| NFR-18 | Observability | Technical Execution | P1, P10 | `LogAllowlistTest`, `MetricsIT` | Specified |
| NFR-19 | Determinism | Technical Execution | P7 | `DeterminismIT` | Specified |
| NFR-20 | Abuse resistance | Scalability | P7 | `AbuseLimitsIT` | Specified |
| NFR-21 | Fairness (language and side) | Ethical AI | P10 | M-13, M-14 | Specified |
