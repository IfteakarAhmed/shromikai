# ADR-0010: Identity stack: Spring Security, Nimbus JWT, Bucket4j, React Router

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | FR-05 to FR-11; NFR-07; architecture T-13, T-17, T-23; api.md §2; P2 |

## Context

P2 adds authentication, roles, guest and demo access and an audit trail (FR-05 to FR-11). AGENTS.md
§9 requires an ADR when a change adds a runtime dependency. The project allows no third-party
identity provider, so tokens are issued and checked in the application. Four libraries are new:

| Library | Used for |
| --- | --- |
| `spring-boot-starter-security` (Spring Security 6.5, managed by the Boot BOM) | Filter chain, authorisation rules, BCrypt, header writers |
| `com.nimbusds:nimbus-jose-jwt` 10.4 | HS256 signing and verification of access tokens |
| `com.bucket4j:bucket4j_jdk17-core` 8.14.0 | Per-IP and per-user rate limits on auth endpoints |
| `react-router-dom` 7 (frontend) | Routes, protected routes, one-click demo routes |

## Decision

1. **Stateless access tokens.** HS256 JWTs, issuer `shromikai`, 15 minutes (guests 2 hours). The
   secret comes from `JWT_SECRET` and must be at least 32 bytes; startup fails otherwise. The
   parser accepts HS256 only, so `alg: none` and other algorithms are refused, and it always
   checks issuer and expiry.
2. **Rotating refresh tokens.** 32 random bytes, stored as SHA-256 only, 7 days, sent in an
   `HttpOnly; SameSite=Strict; Path=/api/v1/auth` cookie. Each use rotates the token inside one
   transaction. Reuse of a rotated token revokes its whole family. Logout and a password change
   revoke refresh tokens.
3. **No CSRF token.** Nothing authenticates by cookie except `POST /auth/refresh`, which only
   returns a token to the caller that holds the cookie; the cookie is `SameSite=Strict` and CORS
   allows only configured origins. Every other endpoint needs the `Authorization` header, which a
   cross-site form cannot set.
4. **Authorisation by URL pattern**, in one `SecurityFilterChain` that mirrors the api.md §2
   matrix, with `anyRequest().authenticated()` as the default. There is no method security, so the
   whole matrix is readable in one file and `RoleAccessIT` covers it.
5. **Credentials only in headers.** A filter rejects `access_token`, `token` and `api_key` query
   parameters with `400` before authentication (T-23).
6. **Rate limits in memory.** Bucket4j buckets per hashed IP and per hashed user key, 20 and 10
   requests per minute. A single instance holds them; multi-instance deployment (P11) would need a
   shared store, and this is recorded as a known gap.
7. **Guest quotas in memory for P2**, in `GuestQuotaService`, behind the call the analyses endpoint
   will make. P7 moves the counters to `usage_counter` (V6, architecture §11.2).
8. **Demo access cannot reach production.** The demo controller and seeder carry
   `@Profile("demo & !prod")`, and `StartupGuards` refuses to start with both profiles.
9. **Audit.** Every audit detail is validated against a closed JSON Schema
   (`schemas/audit/audit-detail.v1.schema.json`), so no token, password or e-mail address can be
   written by accident. Unknown e-mail addresses are never recorded.
10. **Frontend session in memory.** The access token lives in React state only; a silent refresh
    runs at start and one minute before expiry. Concurrent refresh calls share one request,
    because a second call with the same cookie would look like token reuse.

## Consequences

- No identity provider, no session store: the only server state is `refresh_token` rows.
- A revoked account keeps a valid access token for up to 15 minutes. Disabled users cannot log in
  or refresh.
- `server.forward-headers-strategy` stays unset, so the rate limit sees the proxy's address until
  P11 configures the trusted proxy. Trusting forwarded headers earlier would let a client choose
  its own address.
- The security chain's order matters: the query-parameter filter runs first, then token parsing,
  then authorisation, then the admin audit filter.

## Alternatives

- **Spring Authorization Server or OAuth2 resource server.** Rejected: more moving parts than a
  single-tenant JWT; the brief rules out an external identity provider.
- **jjwt or java-jwt.** Rejected: Nimbus is what Spring Security itself uses.
- **Server-side sessions.** Rejected: stateless tokens match the horizontal-scaling plan.
- **Redis-backed rate limits.** Deferred to P11 with the multi-instance decision.

## Verification

- `JwtServiceTest`: expiry, tampered signature or payload, wrong issuer, wrong secret, `alg: none`,
  HS512.
- `AuthControllerIT`, `RoleAccessIT`, `GuestTokenIT`, `RateLimitIT`, `RateLimitPerUserIT`,
  `DemoLoginIT`, `DemoLoginDisabledIT`, `AuditEventIT`, `SecurityHeadersIT`.
- `DemoProfileGuardTest`: `prod` with `demo` refuses to start; a short secret refuses to start.
- Frontend: `LoginForm`, demo login, `ProtectedRoute`, logout and silent refresh tests.
