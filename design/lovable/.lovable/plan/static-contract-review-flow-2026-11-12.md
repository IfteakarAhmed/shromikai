# Static Contract Review Flow

## Goal
Build a Bangla-first, frontend-only sample review journey at `/app`, using static mock findings from `src/mocks/`. Keep the app usable without authentication, network analysis, or persistence.

## User experience
- Replace the current single-finding demo with an upload/paste screen: local PDF/image/text selection, drag-and-drop, camera capture, side choice, clear file validation (supported formats, 10 MB maximum), privacy messaging, and a primary analysis action.
- Simulate the six requested review stages and live status copy with a cancellable/cleaned-up timer, then show the clearly labeled recorded sample report.
- Show severity-sorted findings and the requested counts; each finding includes its severity, non-obvious marker, explanation, verification state, citations, and a “why?” evidence drawer.
- Open citation chips in a detail side sheet with the mock source excerpt/highlight, law/version metadata, and official-source link. Uncertain findings must have no citation and say they were sent for human review.
- Add swipeable result tabs for risks, redlined suggested clauses, worker/employer checklists, and a printable memo; retain the disclaimer and add a mobile sticky action bar.
- Provide Bangla and English locale keys for every new visible string.

## Technical approach
- Keep sample contract, ten verified findings, one uncertain finding, citations, extracted evidence, and proposed changes in typed static mock modules under `src/mocks/`.
- Keep selected files and pasted text in component state only; do not transmit or persist them. Make the demo/report label explicit so mocked findings are not represented as analysis of the user’s supplied file.
- Use existing design-system controls, tokens, Badge/CitationChip/VerificationBadge/Disclaimer, and accessible dialog/sheet primitives. Add only focused UI components and tests for mock counts, citation safety, uncertainty behavior, and file-size/type validation.
- Add print-only styling for the memo and responsive behavior for 390px screens; respect reduced motion.

## Verification
Run focused tests, strict typecheck, lint, preview build checks, and browser flows covering validation, simulated progress, report tabs, citation/evidence sheets, print mode, and 390px layout.
