# Design and build provenance of the web screens

Which AI tool produced which screen (FR-78, architecture §17). The team reviews, runs and is
responsible for all of it. The tools used for the whole project are in
[tooling-disclosure.md](tooling-disclosure.md).

| Screen or part | Tool | What came from the tool, and what was done with it |
| --- | --- | --- |
| Design tokens, glass panels, severity and verification colours, light and dark themes | Lovable (visual prototype in [`design/lovable/`](../design/lovable/)) | The look only. The tokens were re-written as plain Tailwind CSS in `frontend/src/index.css`; the prototype's code, mock data and Supabase code were not used |
| Landing page layout and Bangla copy, the 3D hero scene, the poster picture | Lovable | The section order, the wording (re-checked: the privacy sentence of the prototype was wrong for this product and was rewritten; its hard-coded statute quote was dropped, R-10) and the scene design. The scene was re-written in plain three.js; the poster picture `frontend/src/assets/contract-poster.webp` is the prototype's |
| Upload, progress, results, citation sheet and the "why" drawer | Lovable (layout and states) and Claude Code (implementation) | The prototype's flow was rebuilt in TypeScript against the real API, with the real SSE stream, real findings and real verification objects |
| Redline, duty list and memo tabs | Lovable (layout) and Claude Code | As above |
| `/app/compare`, `/app/tickets`, `/app/profile`, `/admin`, `/docs` | Claude Code | No prototype existed; built with the same tokens and components |
| All tests, the SSE client, storage allowlist, API client and generated types | Claude Code | Test-first, reviewed by the team |

The prototype's TanStack Start, bun and shadcn setup was not adopted ([ADR-0023](adr/0023-p9-frontend-dependencies.md)).
