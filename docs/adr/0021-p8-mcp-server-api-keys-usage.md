# ADR-0021: P8 decisions: MCP server dependency, API keys, usage events, tool-level limits

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0001, ADR-0006, ADR-0007, ADR-0010, ADR-0020; rules R-01, R-02, R-03, R-12; PRD FR-64 to FR-67; architecture §10.7, §11 |

## Context

P8 exposes the legal corpus and the verifier to other agents over the Model Context Protocol. It
needs a runtime dependency, two tables and an authentication path that did not exist (AGENTS.md §9
items 1 and 3). A survey at the start of P8 found that spec text and code disagree: architecture
§11.2 lists `usage_event` as a V6 table and V7 as the owner of `usage_counter` and `usage_event`,
but no migration creates `usage_event` (V7 holds `daily_spend`, playbooks and cache columns). No
`api_key` table or key code existed either.

## Decision

1. **Dependency.** `spring-ai-starter-mcp-server-webmvc`, version from the Spring AI BOM (1.1.8,
   MCP Java SDK 0.18.3). Transport: Streamable HTTP, endpoint `/mcp`
   (`spring.ai.mcp.server.protocol=STREAMABLE`); no SSE transport. The server is switched on only by
   the `mcp` profile: `spring.ai.mcp.server.enabled=false` in `application.yml`, `true` in
   `application-mcp.yml`, and every MCP bean is `@Profile("mcp")`. Tools, resource and prompt are
   declared with the starter's annotations (`@McpTool`, `@McpResource`, `@McpPrompt`).
2. **Migration `V8__api_keys.sql`** creates `api_key` (as in architecture §11.2) and `usage_event`
   (`id`, `org_id`, `api_key_id`, `user_id`, `kind`, `units`, `cost_usd_micros`, `occurred_at`; no
   text, no identifiers other than IDs). `usage_counter` is not touched. Keys are stored as SHA-256
   of the full key plus an 8-character prefix; the key is shown once at creation.
3. **Keys:** `shk_` + 40 base62 characters from `SecureRandom`; scopes `analyses`, `laws`, `mcp`,
   `automation`; created and revoked by an ADMIN for one organisation through
   `/api/v1/admin/api-keys` (listing filters by `org_id`; another organisation gets 404, T-26).
   A key carries no role. In P8 a key authenticates **only `/mcp`** and needs scope `mcp`; use of
   keys on the REST API (scopes `analyses`, `laws`, `automation`) is not enabled and is recorded in
   `docs/known-gaps.md` (P11 enables `automation`).
4. **Authentication in `api`** (security filter), **policy in `auth`** (`ApiKeyService`: create,
   revoke, authenticate by hash). A missing, unknown or revoked key is 401; a key without scope
   `mcp` is 403 and writes an `ACCESS_DENIED` audit event with reason `missing_scope`; a rejected
   key writes `ACCESS_DENIED` with reason `invalid_key` (the key itself is never stored).
5. **Rate limit at tool level.** The limit of 60 per minute per key counts MCP *calls*
   (`tools/call` and resource reads), not protocol traffic such as `initialize` or `tools/list`,
   with one Bucket4j bucket per key. A refused call returns an MCP tool error (`isError: true`,
   problem `rate-limited`, `retryAfterSeconds`), as `api.md` §8 says, and writes an audit event.
   The REST half of "60 per minute across REST and MCP" follows when keys are enabled on REST.
6. **Usage.** Every MCP call writes one `usage_event` (`MCP_CALL`, units 1; `cost_usd_micros` of an
   analysis) for the key's organisation. Analyses started by a key belong to the key's
   organisation subject (`kind` `API_KEY`, hash of the key ID) and are deleted from the job store
   when the result has been built (FR-66: nothing is stored). Other usage kinds are still unwritten
   (known gap).
7. **Free text.** `verify_legal_citation` redacts the claim locally through
   `analysis.ClaimRedactionService` before the support check (architecture §10.7) and fails closed
   with `local-model-unavailable`; `search_bd_law` makes no cloud call and no query rewriting.
8. **Module matrix unchanged.** `mcp` already may depend on `shared`, `trace`, `auth`, `law`,
   `retrieval`, `rules`, `verify` and `analysis`.

## Consequences

- One new runtime dependency tree (Spring AI MCP server, MCP SDK, annotations); no new container.
- The spec text for `usage_event` is corrected in the same change.
- Keys cannot yet call REST endpoints; a key leaked from a demo can reach only the five MCP tools.

## Alternatives

- Counting every HTTP request to `/mcp` toward the limit: simpler, but a client's handshake and
  tool listing would use up the budget of real calls.
- SSE transport: deprecated in the MCP specification; ADR-0001 chose Streamable HTTP.
- Hand-written JSON-RPC: more code and no protocol conformance for free.

## Verification

`McpToolSchemaTest`, `McpServerIT`, `McpAuthIT`, `McpRateLimitIT`, `McpPrivacyIT`, `ApiKeyAdminIT`,
extended `CrossOrganisationIsolationIT`, `FailClosedIT`, `EgressPlumbingIT` and
`NoPiiLeavesTheBoxIT`.
