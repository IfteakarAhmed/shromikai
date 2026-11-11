# ShromikAI MCP server

ShromikAI exposes its legal knowledge and its citation verifier to other AI agents through the
[Model Context Protocol](https://modelcontextprotocol.io). The contracts are in
[`spec/api.md` §9](spec/api.md); the decisions are in [ADR-0021](adr/0021-p8-mcp-server-api-keys-usage.md).

> ShromikAI is not legal advice (এটা আইনি পরামর্শ নয়). Every result says so.

## At a glance

| | |
| --- | --- |
| Endpoint | `POST/GET/DELETE /mcp` on the backend (default `http://localhost:8080/mcp`) |
| Transport | **Streamable HTTP** (MCP spec revision 2025-06-18 and later). The deprecated SSE transport is not offered |
| Server library | Spring AI **1.1.8** `spring-ai-starter-mcp-server-webmvc` (pinned by the Spring AI BOM in `backend/build.gradle`), MCP Java SDK **0.18.3** |
| Switch | Spring profile `mcp`; without it there is no `/mcp` endpoint |
| Authentication | `Authorization: Bearer shk_...` API key with scope `mcp`; users' access tokens do not open `/mcp` |
| Limits | 60 tool calls and resource reads per minute per key (a refused call is a tool error with `retryAfterSeconds`) |
| Tools | 5: `search_bd_law`, `get_law_section`, `verify_legal_citation`, `analyze_employment_contract`, `list_jurisdiction_packs` |
| Resources | 1 template: `law://bd/{act_code}/{section_no}` (`text/markdown`) |
| Prompts | 1: `review-employment-contract` (arguments `side`, `language`) |
| Endpoint count | 1 MCP endpoint, 5 tools + 1 resource template + 1 prompt = 7 callable items |

### The five tools

| Tool | What it does | Cloud call? |
| --- | --- | --- |
| `search_bd_law(query, top_k, language)` | Hybrid retrieval (bge-m3 vectors + full text, RRF, graph expansion) over the statute corpus; returns section keys, snippets and official source URLs. No query rewriting | **No.** Runs on local Ollama and PostgreSQL; the query is never logged |
| `get_law_section(act_code, section_no)` | Current official text in Bangla and English with version, effective date and source URL. Latin or Bangla digits | No |
| `verify_legal_citation(act_code, section_no, quoted_text, claim)` | The 3-check verifier (existence and currency, quote match, independent support), origin `EXTERNAL`. The claim (20 to 600 characters) is redacted **locally** first | Only the support check, with the redacted claim (Claude today, ADR-0018) |
| `analyze_employment_contract(text, side, output_language)` | The full pipeline: local PII redaction, retrieval, rules, risk agent, verification, counter-offers. Up to 2 minutes. Returns findings, statuses, verified citations and suggested clauses with placeholders | Yes, redacted text only |
| `list_jurisdiction_packs()` | Packs and coverage: acts and section counts, ILO conventions, number of active rules and of reviewed ILO mappings | No |

Each tool has a JSON Schema for input and output, a precise description and an example. They are
data files: [`backend/src/main/resources/mcp/tools/`](../backend/src/main/resources/mcp/tools).
Results carry `structuredContent` and one `text` item with the same JSON, plus `corpus_release`
and `disclaimer`. Errors are `isError: true` with a problem document (`code`, `status`, `title`,
and `retryAfterSeconds` for `rate-limited`) as `structuredContent`.

## Run the server

Start the databases and local models once (see `AGENTS.md` §11), then:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev,demo,mcp DEMO_PASSWORD='choose-12-or-more-chars' ./gradlew bootRun
```

`demo` seeds the judge accounts (`admin@…` belongs to the demo organisation); `mcp` turns the
server on. The corpus must be ingested (`./gradlew ingestLaws`), otherwise search and lookups
return nothing.

> **Cost.** `search_bd_law`, `get_law_section`, `list_jurisdiction_packs` and the resource cost
> nothing. `verify_legal_citation` makes one small Claude call and `analyze_employment_contract`
> makes many (the cap is US$1.00 per analysis, `shromikai.budget.per-analysis-usd`; real costs are in
> `docs/dev-log.md`). Use a short synthetic contract from `eval/contracts/synthetic/`, never a
> real one, and keep to the team's live-spend cap.

### Create an API key

Keys are created by an ADMIN for one organisation, shown once and stored as SHA-256.

```bash
# 1. sign in as the demo admin (access token)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/demo/admin | jq -r .accessToken)

# 2. create a key with scope mcp (the secret is only in this response)
curl -s -X POST http://localhost:8080/api/v1/admin/api-keys \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Claude Desktop","scopes":["mcp"]}' | tee /tmp/shromikai-key.json
export SHK_KEY=$(jq -r .key /tmp/shromikai-key.json)

# list (never shows the secret) and revoke
curl -s http://localhost:8080/api/v1/admin/api-keys -H "Authorization: Bearer $TOKEN"
curl -s -X DELETE http://localhost:8080/api/v1/admin/api-keys/<id> -H "Authorization: Bearer $TOKEN"
```

Scopes are `analyses`, `laws`, `mcp`, `automation`. In P8 a key opens only `/mcp` (scope `mcp`);
REST use of keys is a known gap (`docs/known-gaps.md`).

## Connect from Claude Desktop

Claude Desktop reaches a local HTTP server through the `mcp-remote` bridge (Node.js 18+). Add this
to `claude_desktop_config.json`
(macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`), put your key in
`env`, and restart Claude Desktop:

```json
{
  "mcpServers": {
    "shromikai": {
      "command": "npx",
      "args": [
        "-y", "mcp-remote", "http://localhost:8080/mcp",
        "--header", "Authorization:${SHROMIKAI_AUTH}"
      ],
      "env": { "SHROMIKAI_AUTH": "Bearer shk_PASTE_THE_KEY_HERE" }
    }
  }
}
```

This configuration follows the `mcp-remote` documentation and has **not** been run by the author of
this document; the team's acceptance check (screenshots below) is what confirms it. The header is
passed through `env` so that no space appears inside an argument. Newer Claude Desktop versions
may also offer *Settings → Connectors → Add custom connector* with a URL; whether that form can
send a static `Authorization` header was not checked, so use the bridge for a key-protected
server.

### Screenshot steps for the team (Claude Desktop)

Save each as `docs/img/mcp-desktop-<name>.png`:

1. `mcp-desktop-connected`: the tools menu (hammer or plug icon) listing **shromikai** with 5 tools.
2. `mcp-desktop-search`: prompt *"Use search_bd_law to find what the corpus says about overtime."*
3. `mcp-desktop-section`: prompt *"Call get_law_section for the first result."*
4. `mcp-desktop-verify`: prompt *"Call verify_legal_citation for that section with a quote from it and the claim 'overtime must be paid at a higher rate'."* Expect VERIFIED or UNCERTAIN with reasons.
5. `mcp-desktop-fake`: same call with `section_no` `9999`. Expect `REJECTED` with `SECTION_NOT_FOUND`.
6. `mcp-desktop-analyze`: attach `eval/contracts/synthetic/syn-en-01.md` text and use the prompt template *review-employment-contract* (Attach from MCP menu, `side=WORKER`, `language=en`).
7. `mcp-desktop-packs`: prompt *"Call list_jurisdiction_packs."*

## MCP Inspector walkthrough

```bash
npx @modelcontextprotocol/inspector
```

Open the printed URL (it includes a session token), then:

1. **Transport Type**: *Streamable HTTP*. **URL**: `http://localhost:8080/mcp`.
2. Open **Authentication**, set header name `Authorization` and value `Bearer shk_...` (your key).
3. **Connect**. Screenshot `docs/img/mcp-inspector-connected.png` (server name `shromikai`, version `0.1.0`).
4. **Tools → List Tools**: 5 tools, each with description, input and output schema.
   Screenshot `mcp-inspector-tools.png`.
5. Run each tool (screenshots `mcp-inspector-<tool>.png`):
   - `search_bd_law`: `{"query":"overtime pay","top_k":5,"language":"en"}`
   - `get_law_section`: `{"act_code":"BLA-2006","section_no":"1"}` (any act and section the packs list shows)
   - `verify_legal_citation`: a real section with a quote copied from its text and a claim; then the same with `section_no` `9999`
   - `analyze_employment_contract`: text of `eval/contracts/synthetic/syn-en-01.md`, `side` `WORKER`, `output_language` `en` (takes up to two minutes; costs money, see above)
   - `list_jurisdiction_packs`: `{}`
6. **Resources → Resource Templates**: `law://bd/{act_code}/{section_no}`, read `law://bd/BLA-2006/1`.
   Screenshot `mcp-inspector-resource.png`.
7. **Prompts**: `review-employment-contract` with `side=WORKER`, `language=bn`.
   Screenshot `mcp-inspector-prompt.png`.
8. Negative checks (no screenshot needed): remove the header and Connect (401); use a revoked key (401);
   call a tool 61 times in a minute (the 61st returns `rate-limited`).

## Example session

> **What this is.** A real session recorded on 2026-11-11 with the official MCP Java SDK client
> against the integration-test stack: the **fictional** act `XV-LAB` (pack `xx`, invented text,
> not law, R-10) and **scripted stand-ins for the models** (not recorded model output, R-07), so
> the `support` judgement and the findings only show the shape and wiring. Fields marked `…` were
> trimmed. A run against the real corpus and real models is the team's acceptance check.

Client connects (`initialize`): server `shromikai` 0.1.0, protocol 2025-11-25, Streamable HTTP.

**1. `search_bd_law`** `{"query": "probation period", "top_k": 2}`: local retrieval, no cloud call.

```json
{ "results": [
    { "section_key": "XV-LAB:1@original", "act_code": "XV-LAB", "section_no": "1",
      "heading_en": "Fictional section 1",
      "snippet": "Fictional section on probation. A probation period shall not exceed six months. …",
      "source_url": "https://example.test/xv/1", "fused_score": 0.0164, "origin": "SEARCH" },
    { "section_key": "XV-LAB:2@original", "…": "…" } ],
  "disclaimer": "এটা আইনি পরামর্শ নয়। This is not legal advice." }
```

**2. `get_law_section`** `{"act_code": "XV-LAB", "section_no": "1"}`

```json
{ "section_key": "XV-LAB:1@original", "act_title_en": "Fictional Labour Act (tests only)",
  "authoritative_lang": "en", "text_en": "Fictional section on probation. A probation period shall not exceed six months. …",
  "version_label": "original", "is_current": true, "source_url": "https://example.test/xv/1",
  "related": [], "disclaimer": "…" }
```

**3. `verify_legal_citation`**, a real section with a supporting claim
`{"act_code": "XV-LAB", "section_no": "1", "quoted_text": "A probation period shall not exceed six months.", "claim": "A probation period longer than six months is not allowed under this section."}`

```json
{ "status": "VERIFIED",
  "checks": { "existence": { "outcome": "PASS" },
              "quote_match": { "outcome": "PASS", "similarity": 1.0, "matched_language": "en" },
              "support": { "outcome": "PASS", "label": "SUPPORTS", "confidence": 0.9,
                           "independence": "SAME_FAMILY", "model_id": "claude-haiku-5-5", "reason": "stand-in" } },
  "reason_codes": [],
  "verified_citation": { "section_key": "XV-LAB:1@original", "quote": "A probation period shall not exceed six months.",
                         "quote_language": "en", "source_url": "https://example.test/xv/1", "…": "…" },
  "disclaimer": "…" }
```

**4. `verify_legal_citation`**, a fabricated section (`"section_no": "99"`, same quote and claim)

```json
{ "status": "REJECTED",
  "checks": { "existence": { "outcome": "FAIL" },
              "quote_match": { "outcome": "SKIPPED" }, "support": { "outcome": "SKIPPED" } },
  "reason_codes": [ "SECTION_NOT_FOUND" ],
  "verified_citation": null, "disclaimer": "…" }
```

**5. `analyze_employment_contract`**, the synthetic contract `syn-en-01` (2,007 characters, invented
parties), `{"side": "WORKER", "output_language": "en"}`: seven findings, six `VERIFIED` with a
verified citation and a suggested clause, one `UNCERTAIN` with no citation (trimmed to the first
finding and the summary):

```json
{ "analysis_id": "f95ae527-…",
  "findings": [
    { "id": "f1", "title": "Check clause c3 (PROBATION)", "kind": "CONFLICT", "severity": "HIGH",
      "non_obvious": false, "verification_status": "VERIFIED",
      "citations": [ { "section_key": "XV-LAB:1@original", "quote": "…", "…": "…" } ],
      "suggested_clause": "The employer may end the contract on 14 days written notice.", "…": "…" },
    { "id": "f6", "verification_status": "UNCERTAIN", "citations": [], "suggested_clause": null, "…": "…" } ],
  "summary": { "overall_severity": "HIGH",
               "findings_by_severity": { "CRITICAL": 0, "HIGH": 6, "MEDIUM": 1, "LOW": 0 },
               "verified_findings": 6, "uncertain_findings": 1, "verified_citations": 6, "…": "…" },
  "review_ticket": { "ticket_id": "c0b43fed-…", "expires_at": "2026-12-11T17:59:06Z" },
  "provenance": { "pipeline_version": "p6-1", "verifier_independence": "SAME_FAMILY", "…": "…" },
  "warnings": [ { "code": "verifier-same-family", "message": "Citations were checked by a model of the same family." }, "…" ] }
```

None of the seven findings, the summary or the suggested clauses contains a name, NID, phone number or
e-mail address of the contract, and none repeats 8 consecutive words of it
(`McpPrivacyIT`); a name the model writes appears as a placeholder such as `[PERSON_1]`.

**6. `list_jurisdiction_packs`** `{}`

```json
{ "packs": [ { "code": "BD", "name": "Bangladesh", "name_bn": "বাংলাদেশ",
               "acts": [ { "act_code": "XV-LAB", "title_en": "Fictional Labour Act (tests only)", "section_count": 9, "authoritative_lang": "en" } ],
               "ilo_conventions": [], "active_rules": 0, "reviewed_ilo_mappings": 0 } ],
  "disclaimer": "…" }
```

Errors are tool errors, for example the 61st call in a minute (shape only; `McpRateLimitIT` proves the behaviour):

```json
{ "isError": true,
  "structuredContent": { "code": "rate-limited", "status": 429, "title": "Too many requests", "retryAfterSeconds": "<seconds until the next call is allowed>" } }
```

## Security posture

| Concern | Control | Proof |
| --- | --- | --- |
| Stolen or guessed key (T-12) | `shk_` + 40 base62 characters from `SecureRandom`; only SHA-256 stored; shown once; revocation takes effect on the next call; scopes; 60 calls per minute per key; denials audited (`ACCESS_DENIED`, reason `invalid_key`, `missing_scope` or `rate_limited`, never the key) | `ApiKeyGeneratorTest`, `ApiKeyAdminIT`, `McpAuthIT`, `McpRateLimitIT` |
| Key in a URL (T-23) | `api_key`, `token` and `access_token` query parameters are refused with 400 | `McpAuthIT` |
| Contract leaves the box (R-03) | `analyze_employment_contract` redacts on the server with deterministic detectors and the local model before any cloud call; if the local model is down the call fails (`local-model-unavailable`) and nothing is sent | `McpPrivacyIT`, `NoPiiLeavesTheBoxIT`, `EgressPlumbingIT`, `FailClosedIT` |
| Claim carries personal data | `verify_legal_citation` redacts the claim locally first; the quote must match the official text (92% similarity) before it can reach the support model | `NoPiiLeavesTheBoxIT`, `EgressPlumbingIT` |
| Query carries personal data | `search_bd_law` makes no cloud call and no query rewriting; queries are not logged | `NoPiiLeavesTheBoxIT` |
| Contract echoed or stored (R-02) | Results keep placeholders and are never re-identified; any text that would repeat 8 consecutive tokens of the contract (except `suggested_clause`) is replaced by a fixed notice and a `text-withheld` warning; the job is deleted from memory when the result is built; no contract text is written anywhere | `McpPrivacyIT`, `NoContractPersistenceIT` |
| Invented law (R-01) | Only `VERIFIED` citations are returned; an unverified finding is `UNCERTAIN` with no citation | `McpServerIT`, `AllResponsesCarryOnlyVerifiedCitationsIT` |
| Cross-organisation access (T-26) | Keys, usage events and MCP-started analyses belong to the key's organisation; another organisation's admin gets 404 | `CrossOrganisationIsolationIT` |
| Prompt injection through a contract (R-06) | Contract text only appears inside `<contract_clause>` elements; agents have no tools | `PromptInjectionTest` |

Known limits: `suggested_clause` repeats parts of the clause it revises by design (FR-66) and keeps
placeholders; the local model's recall of names is measured separately (M-16, a known gap); a
caller that sends a quote that matches the statute and carries extra text could still pass up to 8%
of foreign characters, which the egress guard scans for known identifiers and PII patterns.

### Cloudflare Tunnel for the demo (dev only)

For a live demo the dev server can be exposed with a quick tunnel; **the API key stays required**
and the tunnel is **dev-only**: it is not a deployment, has no SLA and must be closed after the
demo. Use only synthetic contracts.

```bash
brew install cloudflared                      # once
cloudflared tunnel --url http://localhost:8080  # prints https://<random>.trycloudflare.com
```

Use `https://<random>.trycloudflare.com/mcp` as the server URL and a key made for the demo (revoke it
afterwards). Do not run with the `demo` profile on a tunnel you leave open: it enables one-click
logins. Production hardening (HTTPS origin, HSTS, a named tunnel or Caddy) is P11.

## MCP servers used during development (honest list)

| Server | Used for | Evidence |
| --- | --- | --- |
| *none recorded* | The P8 build session used Claude Code with its built-in file, shell and search tools only; no external MCP server was called | this section |

The repository does not record any other MCP server the team used. **Team: add each server you
actually used during development (name, what for, which phase) before submission, and delete this
sentence.** Do not list a server you did not use (R-12).

## Tests

`McpToolSchemaTest` (schemas and descriptions), `McpServerIT` (official Java SDK client over
Streamable HTTP: lists 5 tools, VERIFIED and REJECTED citations, analysis, resource, prompt),
`McpAuthIT`, `McpRateLimitIT`, `McpPrivacyIT`, `NoPiiLeavesTheBoxIT`, `CrossOrganisationIsolationIT`
(extended), `ApiKeyAdminIT`, `ApiKeyGeneratorTest`, `ClaimRedactionServiceTest`, and the extension of
`EgressPlumbingIT`. Model answers in these tests are scripted stand-ins over the fictional pack
`xx`, not recorded model output (R-07).
