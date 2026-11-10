# Team

Team **Cosmic Duo**, International AI Builders Congress 2026, Team-Built Product, JusticeSphere AI
→ Legal Copilot.

**Composition rule** (participants guide §13.3, as summarised in playbook §4): a team has 3 to 5
members, each with a declared role. At least one female member earns a bonus; members from
polytechnic or madrasah backgrounds are encouraged; the NRB advisor's contribution must be
documented. One person may hold more than one role below, but the team must list at least three
named members. The playbook recommends a law student as the third member, who can also hold the
legal-reviewer role.

## Roles

| Role | Name | Responsibilities | Owns (artefacts) | Approves |
| --- | --- | --- | --- | --- |
| Team Leader | TODO: name | Scope and phase plan; final say on ADRs; judge and organiser contact; submission form; letters of interest; risk register | `AGENTS.md`, `docs/adr/`, `docs/spec/traceability.md`, phase tags | ADRs, phase exits (after Prompt R), submission |
| Business Analyst / Data Scientist | TODO: name | Personas, journeys and KPIs; synthetic contracts; eval dataset, labels and agreement statistics; baseline prompt review; pricing hypotheses; 5 to 10 user interviews in `docs/validation/` | `docs/spec/PRD.md`, `docs/spec/evaluation.md`, `eval/` | Eval reports, KPI definitions |
| UI/UX / Frontend | TODO: name | Bangla-first, phone-first UX; upload and camera flow; risk cards and "কেন?" drawer; compare mode; `/docs` hub; accessibility; screen designs (record any AI builder used in `docs/ai-tools.md`) | `frontend/`, `frontend/src/i18n/`, `frontend/e2e/` | UI changes, i18n strings |
| Backend / Data Engineer | TODO: name | Backend modules; statute ingestion and corpus quality; retrieval; verification; privacy shield; prompts; MCP server; infrastructure, CI and deployment | `backend/`, `data/`, `docs/spec/architecture.md`, `docs/spec/api.md`, `docs/spec/test-strategy.md`, `docs/prompts/`, `docs/mcp.md` | API and schema changes, migrations, prompt releases |
| Presentation Lead | TODO: name | Story, demo script that follows the honest-reporting rules, pitch deck, 3-minute video, judge credential handover | `docs/submission/` (P12) | Public claims (each backed by an eval report or a cited source, R-07) |
| NRB advisor | TODO: name | Knowledge of migrant-worker and diaspora employment contracts (Gulf corridor); partner and NGO introductions; review of the expansion plan and pricing hypotheses; access to NRB users for interviews | Contributions log below | Expansion plan (PRD §11) |
| Legal reviewer (lawyer or law student) | TODO: name | Realism review of synthetic contracts; review of eval labels (`reviewed_by`); review of legal rules and ILO mappings (`reviewed_by` in `data/`); review-queue decisions in demos | Reviewed label files; reviewed rows in `data/rules/` and `data/mappings/` | Labels, legal rules, ILO mappings (as reviewer) |

## Working agreements

1. Every phase follows the review loop in `AGENTS.md` §10: plan approval, test-first build, the human acceptance check on a team member's machine, and an independent Prompt R review before the phase tag.
2. Legal rules, ILO mappings and topic-map rows record an author and a different reviewer (FR-16, FR-36).
3. Claims in the pitch or submission are backed by an eval report or a cited source (R-07); the Presentation Lead runs the P12 fact-check.
4. API keys never go into chat or git; they live in `.env` or the IDE's run configuration.

## NRB advisor contributions log

Record each contribution when it happens. Do not back-fill estimates; if a date or duration is
uncertain, say so in the entry.

| Date | Contribution | Type | Artefact or link | Hours | Acknowledged by (role) |
| --- | --- | --- | --- | --- | --- |
| (no entries yet) | | | | | |

Types: `Market insight`, `Partner introduction`, `User research`, `Document review`,
`Expansion planning`, `Mentoring`, `Other` (describe).
