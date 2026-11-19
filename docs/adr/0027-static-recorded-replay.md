# ADR-0027: Static recorded replay of the sample flow (no server)

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-19 |
| Deciders | Project owner (chose the fallback and its scope), Claude (author) |
| Related | ADR-0026 (single-container Space, not deployable), ADR-0021, ADR-0023; rules R-01, R-02, R-07, R-12; PRD FR-85 (reduced), FR-69, FR-70, FR-74 |

## Context

ADR-0026 planned a one-container Hugging Face Space. On submission day the owner's account has Docker
Spaces locked behind a paid plan and no card, so nothing can host the backend, PostgreSQL, Ollama
and Gotenberg. A judge still needs a public link where the whole sample flow can be seen. Static
hosting (Vercel or Hugging Face Static) is free and needs no card, but has no server.

The recorded demo already replays one fixed request: the synthetic sample contract, the worker's
side, output language Bangla (`meta.recordedDemo`). Its answers are deterministic recordings, so the
real API responses of that flow can be saved once and served as files.

## Decision

1. **A build mode, not a fork.** `VITE_STATIC_REPLAY=1` (`npm run build:replay`) builds the same
   frontend with its API layer answered by files. `src/api/client.ts` hands every call to
   `src/api/replay.ts` in that mode; no page, hook or component knows the difference except the
   label and the disabled features below. Normal builds are unchanged and leave the replay files out
   of `dist` (a Vite plugin removes `dist/replay` unless the flag is set).
2. **The files are real responses.** `scripts/export-replay.mjs` (`npm run export:replay`) runs
   against a running demo backend (profiles `demo` and `fake-models`; it never stops or restarts it)
   and saves, for one fresh run of the sample: `meta` (bn, en), the demo-worker login response, the
   `202` answer of `POST /analyses`, the result (bn, en), the trace for the "Why?" sheet (bn, en),
   every cited section as the citation sheet reads it (bn, en; 16 sections) and the memo PDF the
   backend renders through Gotenberg (bn, en). It refuses a backend that is not on recorded models and
   a result that is not the recorded sample. Nothing is written by hand and no value is made up.
3. **Removals, each recorded.** A response is saved as it came, except: `meta.audit` becomes `null`
   (its counters are read from audit and review tables), `reviewTicket` becomes `null` (a one-time
   code from the development database), and the login `accessToken` becomes `static-replay`
   (a session token is never published). `assertNoSecrets` also stops the export if a file holds a
   JWT or a secret-looking field. `public/replay/manifest.json` lists every file with its request,
   size and SHA-256, the commit, the backend version and corpus release, and every removal.
4. **Integrity is tested.** `replayData.test.ts` fails if a file was edited by hand (SHA-256), is
   missing or unlisted, if a cited section has no file, if the result is not the recorded sample
   (`provenance.recorded`, `input.sample`), if a finding with citations is not `VERIFIED` (R-01), if a
   token or secret is present, or if a removal is not recorded. The files are committed because the
   static build has no backend; Prettier ignores them so the bytes stay exact.
5. **What the replay answers.** The demo worker's one-click login (a guest gets the same recorded
   session, since only one was recorded), the sample analysis, its result, trace, cited sections and
   the PDF memo, each in the reader's language. The result shows at once instead of through the
   progress screen: the recorded `GET` is already a finished result, and no event stream was kept.
   The interface switches between Bangla and English and between dark and light; the recorded
   answers themselves are Bangla, as in the live recorded demo (English output was never recorded).
6. **Everything else says so.** Any other call (another document, text, compare, playbooks, admin,
   profile, ticket, password login, the HR and admin demo logins, an HTML memo) gets the problem
   `static-replay-unavailable` (HTTP 501) with a message in both languages. Routes that only make sense
   with a server (`/login`, `/demo-hr`, `/demo-admin`, `/app/compare`, `/app/profile`, tickets,
   playbooks, `/admin`) show a page saying so, with a link to the sample. The upload page keeps its
   inputs disabled and adds a note.
7. **R-12 label on every screen.** The layout shows a banner, "Static recorded replay, not a live
   server" (bn: "স্ট্যাটিক রেকর্ডকৃত রিপ্লে, লাইভ সার্ভার নয়"), above the existing recorded-demo banner,
   on every route including the landing page and the docs. The audit page adds that its figures were
   saved at export, not read now.
8. **Docs data unchanged.** The docs pages and the evaluation page keep reading the same Markdown and
   `eval/reports/*.json` at build time. The Vercel build clones the whole repository, so they are
   present.
9. **Absolute social URLs.** `index.html` uses `%SITE_URL%`; the build fills it from `VITE_SITE_URL`,
   then `SITE_URL`, then Vercel's production host (`VERCEL_PROJECT_PRODUCTION_URL`); empty (relative)
   when none is set. `resolveSiteUrl` is tested.
10. **Hosting.** `vercel.json` at the repository root: install and build in `frontend/`, output
    `frontend/dist`, a rewrite of every path to `index.html` (Vercel serves existing files first),
    long caching for `/assets` and short for `/replay`. Hugging Face Static can serve the same
    `dist`.

## Consequences

- Cost: none; no card, no server, no secret, no API key, no model call. Startup is instant.
- It is a replay, not a product: one contract, one side, one language of output. No upload, compare,
  profile, admin, MCP, HR role or HTML memo. The label says so on every screen.
- The "live" claims of ADR-0026 (single container) are not exercised by this deployment. ADR-0026's
  design and files remain in the repository for a host that allows them.
- The exported files are a snapshot: after a change to the backend, the rules, the corpus or the
  recordings the export must be run again (`replayData.test.ts` will not notice, since it checks the
  files against their own manifest). The manifest records the commit and whether the tree was dirty.
- The static site sends nothing anywhere: no network call to a backend exists in the build, and the
  tests of the API layer fail on any request to `/api`.
- AGENTS §9 criteria: a new hosting target and where data is sent (nowhere) are the reasons for this
  ADR; no rule R-01 to R-12 is relaxed, and the module matrix is unchanged.

## Alternatives

- **Hand-written fixtures in the frontend:** rejected; invented data would break R-07 and R-12.
- **A mock service worker in production:** more moving parts than a small API-layer switch, and it
  needs the same files.
- **Pre-rendered HTML pages of the result:** loses the working interface (sheets, tabs, language and
  theme switch, memo download).
- **Wait for a paid host:** not possible before submission.

## Verification

- Frontend gates: lint, typecheck, unit tests (531 in 76 files), build, bundle budgets.
- The built replay was served with `vite preview` and driven in a browser: home, demo user, sample
  analysis, findings, "Why?", a cited section, redlines, duties, memo PDF download, Bangla and
  English, dark and light, `/docs/audit`; the page's resource list showed no `/api` request.
- The Playwright suite targets a live backend and was not run for this change.
