# ADR-0023: Frontend dependencies of the P9 web experience

| Field | Value |
| --- | --- |
| Status | Accepted |
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
| `three`, `@react-three/fiber` | The procedural hero scene on `/` | A lazy chunk, fetched only when WebGL exists, motion is allowed and low-bandwidth mode is off |
| `recharts` | Admin usage and cost charts, `/docs/evaluation` charts | Lazy chunks of `/admin` and `/docs` |

Development dependencies added: `@playwright/test` and `@axe-core/playwright` (end-to-end and
accessibility checks), `axe-core` (component-level accessibility checks), `@types/three`.

Not adopted, on purpose: `@react-three/drei` (the hero needs no helper from it; dropped to stay
under the hero budget), TanStack Start and Router, bun, shadcn and Radix (dialogs use the native
`<dialog>` element, which gives a focus trap, `Esc` and an inert background), `lucide-react`
(a handful of inline SVG icons), `class-variance-authority`, `clsx`, `zod`, `react-hook-form`,
`sonner`, `vaul`, `lovable-error-reporting` and Supabase code. Markdown for the `/docs` hub is
rendered by a small in-house component that builds React elements, so no HTML is injected.

## Consequences

- The initial bundle does not grow: `three` and `recharts` are lazy chunks. `check:bundle` gates
  the initial JavaScript at 200 KB gzip and, from P9, the hero chunk at 200 KB gzip as well.
- The hero always has a static poster fallback (no WebGL, reduced motion, low-bandwidth mode, load
  failure), so the page never depends on the 3D chunk.
- Fewer dependencies mean more first-party UI code (dialogs, tabs, toasts) that the team tests with
  Vitest and axe.

## Alternatives

- **Keep drei and the shadcn/Radix kit** as in the prototype: faster to write, but several times
  the dependency count, a larger audit surface and a hero chunk over budget.
- **A CSS or SVG hero only**: smaller, but the prototype's 3D scene is a deliberate part of the
  design; it stays as a progressive enhancement over the same poster.
- **A Markdown library for `/docs`**: one more runtime dependency and an HTML-injection path to
  guard; the pages need only headings, lists, tables, code and links.

## Verification

`npm run check:bundle` (initial chunk and hero chunk budgets); `LandingHero` tests (poster
fallbacks, no 3D import in low-bandwidth mode); `npm audit --omit=dev` in the nightly workflow.
