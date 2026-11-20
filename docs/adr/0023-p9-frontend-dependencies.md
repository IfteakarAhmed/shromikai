# ADR-0023: Frontend dependencies of the P9 web experience

| Field | Value |
| --- | --- |
| Status | Accepted (amended 2026-11-16 for the cinematic landing, Increment D) |
| Date | 2026-11-15 |
| Deciders | Presentation Lead (TODO: name), Team Leader (TODO: name) |
| Related | AGENTS.md §6 and §9 item 1; PRD FR-68 to FR-78, NFR-10; architecture §17 |

## Context

P9 builds the whole web experience: a landing page with a 3D hero, the worker flow, compare, the
admin console and the `/docs` hub. AGENTS.md §9 item 1 requires an ADR for every new runtime
dependency, NFR-10 limits the initial JavaScript to 200 KB gzip, and the PRD fixes Recharts for the
charts. The visual reference (`design/lovable/`) was generated with TanStack Start, bun and shadcn;
none of that stack is adopted, only the visual design.

## Decision

Runtime dependencies added:

| Package | Used for | Where it loads |
| --- | --- | --- |
| `three` | The procedural hero scene on `/`, written against plain three.js with named imports | A lazy chunk (about 120 KB gzip), fetched only when WebGL exists, motion is allowed and low-bandwidth mode is off |
| `recharts` | Admin usage and cost charts, `/docs/evaluation` charts | Lazy chunks of `/admin` and `/docs` |

Development dependencies added: `@playwright/test` and `@axe-core/playwright` (end-to-end and
accessibility checks), `axe-core` (component-level accessibility checks), `@types/three`.

Not adopted, on purpose: `@react-three/fiber` and `@react-three/drei`. React Three Fiber registers
the whole of three.js, which made the scene chunk 234 KB gzip (measured with a first version of the
scene) against a 200 KB budget; plain three with named imports is 120 KB. drei was never needed, TanStack Start and Router, bun, shadcn and Radix (dialogs use the native
`<dialog>` element, which gives a focus trap, `Esc` and an inert background), `lucide-react`
(a handful of inline SVG icons), `class-variance-authority`, `clsx`, `zod`, `react-hook-form`,
`sonner`, `vaul`, `lovable-error-reporting` and Supabase code. Markdown for the `/docs` hub is
rendered by a small in-house component that builds React elements, so no HTML is injected.

## Consequences

- The initial bundle does not grow: `three` and `recharts` are lazy chunks. `check:bundle` gates
  the initial JavaScript at 200 KB gzip and the hero (scene chunk plus poster) at 200 KB as well.
- The hero always has a static poster fallback (no WebGL, reduced motion, low-bandwidth mode, load
  failure), so the page never depends on the 3D chunk.
- Fewer dependencies mean more first-party UI code (dialogs, tabs, toasts) that the team tests with
  Vitest and axe.

## Amendment 2026-11-16: the cinematic landing (P9 Increment D)

**Context.** The landing becomes a scroll-driven story in six chapters with one fixed WebGL scene
behind real HTML chapters (FR-68). The scene is larger than the framed hero, so it needs its own
budget, and phones and slow connections must not pay for it (FR-76, NFR-10).

**Decision.**

- **No new dependency.** The scene is plain `three` with hand-written GLSL, runtime canvas
  textures and particles. Bloom is a small custom pass (bright pass, blur at half and quarter
  size, composite); `EffectComposer` and the post-processing add-ons are not used. No external
  asset: no video, model, downloaded texture or font.
- **Three tiers, chosen by pure tested functions** (`features/landing/cinematic/tier.ts`):
  *cinematic* (WebGL2, at least 4 cores, at least 4 GB memory when known, width at least 1024, no
  data saver, `effectiveType` 4g or unknown, no reduced motion, low-bandwidth mode off);
  *standard* (the framed hero scene); *lite* (the poster, no WebGL). Width under 768, a connection
  other than 4g, data saver, reduced motion and low-bandwidth mode default to lite. A visible
  switch turns the scene on ("দৃশ্য চালু করুন") or off ("স্থির ছবি") at any time; the choice lives
  in memory only, so the storage allowlist does not change. `?scene=lite|standard|cinematic`
  forces a tier for review and screenshots.
- **Native scroll, no hijacking.** The chapters are ordinary sections with sticky text; the
  middle of the viewport among them is the timeline value, damped per frame. The canvas pauses
  when the tab is hidden or the story has scrolled away, caps the pixel ratio at 1.5, and lowers
  quality (particles, then bloom, then pixel ratio) when frames stay slow; it never raises it
  again in the same visit.
- **Posters from the scene.** `scripts/render-scene.mjs` renders the scene with headless Chromium
  and SwiftShader into the lite poster (`src/assets/contract-poster.webp`, replacing the
  prototype picture) and the social image (`public/og-image.jpg`; replaced by the logo card
  `public/og-image.png` in ADR-0028).
- **Budgets.** Initial JavaScript stays under 200 KB gzip (it grew by about 1.6 KB for the tier
  code and the chapter strings; the scene code is not in it). The cinematic scene, three.js
  included, is a lazy chunk of at most **300 KB gzip**, fetched only on the cinematic tier. The
  standard hero (its chunk, the shared three.js chunk and the poster) stays at 200 KB. The lite
  landing stays under **1.5 MB** of total transfer.

**Consequences.** three.js is now a shared lazy chunk used by both scenes. The landing keeps the
ink palette in either theme (`data-surface="ink"`), because the scene is drawn on ink. Headless
tests can draw the scene only through SwiftShader, which is slow; the adaptive quality lowers it
there, so the report pictures show the reduced levels.

## Amendment 2026-11-16 (C1): self-hosted type pairing and the app-wide visual language

**Decision.** Two runtime dependencies are added, both font files only: `@fontsource/tiro-bangla`
(headings, Bengali and Latin subsets, weight 400, its only weight) and `@fontsource/space-grotesk`
(numerals and interface labels, Latin 500 and 700). Hind Siliguri stays for body text, now 400
and 600 only (700 is dropped; bold body text uses 600). All are woff2 subsets bundled by Vite;
no page requests a font CDN. Headings use Tiro Bangla from 24 px up, with `font-synthesis: none`
so the browser never fakes a bold; smaller headings use Hind Siliguri 600, because Tiro Bangla is
thin at about 20 px.

The cinematic look of the app pages (layered ink-navy surfaces, glass, glow orbs, scroll reveals,
route transitions) is CSS and SVG only. Route transitions use the browser's View Transitions API
directly (`document.startViewTransition` with `flushSync`) because the app uses `BrowserRouter`,
where React Router's `viewTransition` prop does not apply; no new library. Reveals animate
transform only, never opacity, so content stays readable at rest and for contrast checks. Reduced
motion and low-bandwidth mode switch all of it off; low-bandwidth mode also removes the orbs.

**Budget.** `check:bundle` limits the bundled woff2 files to 320 KB and rejects subsets or weights
outside the pairing (measured 302.8 KB in 8 files; a page fetches only the subsets it uses).

**Alternatives.** Pairing A (Anek Bangla, Hind Siliguri, Inter) and pairing B (Noto Serif Bengali,
Noto Sans Bengali, Inter) were shown to the team on a specimen page; the team chose C.

## Alternatives

- **Keep r3f, drei and the shadcn/Radix kit** as in the prototype: faster to write, but several
  times the dependency count, a larger audit surface and a hero chunk over budget (measured).
- **A CSS or SVG hero only**: smaller, but the prototype's 3D scene is a deliberate part of the
  design; it stays as a progressive enhancement over the same poster.
- **A Markdown library for `/docs`**: one more runtime dependency and an HTML-injection path to
  guard; the pages need only headings, lists, tables, code and links.

## Verification

`npm run check:bundle` (initial JavaScript, the standard hero and the cinematic scene, following
lazy chunks through the Vite manifest); `npm run check:lite` (lite landing transfer, measured in
Chromium against the production build); `HeroFrame` and `LandingPage` tests (poster fallbacks,
no scene import on the lite tier, the switch, nothing stored); `tier`, `timeline`, `damp`,
`quality` and `capabilities` unit tests; `e2e/cinematic.spec.ts` (WebGL2 canvas, native scroll,
keyboard only, axe in every tier); `npm audit --omit=dev` in the nightly workflow.
