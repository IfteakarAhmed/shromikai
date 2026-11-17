> **NOT PRODUCT TEXT. DESIGN REFERENCE ONLY (R-10, R-07, R-12).**
> This folder is a Lovable visual prototype. Its look (tokens in `src/styles.css`, component shapes,
> screenshots) was the reference for the real frontend. Every legal sentence, section number,
> quote, statute name, finding, sample contract and number in `src/mocks/`, `src/locales/` and the
> components here was typed by a prototype generator, **not taken from the statute corpus**. None
> of it is verified law, none of it is shown to users, and nothing in `frontend/` or `backend/`
> imports from this folder. Real law text comes only from the ingested corpus with a source URL.

# Shromik Law Lens

Build the design foundation for ShromikAI (শ্রমিকAI), a Bangla-first legal-tech product that checks employment contracts in Bangladesh and shows only verified law citations. Stack: React + TypeScript (strict, no "any") + Vite + Tailwind. No backend, no Supabase, no auth: use static mock data in src/mocks/. Never store anything in localStorage.

Look and feel: premium, calm, trustworthy, "expensive" product design with real depth. Palette: deep ink-navy background (#070B14 to #0E1626), luminous emerald (#2EE6A6) for "verified", warm amber (#FFB547) for "uncertain", coral red (#FF5A6A) for rejected/critical, soft ivory text (#F4F1EA), muted slate for secondary text. Glass cards (backdrop blur, 1px inner highlight border, layered soft shadows, subtle noise), generous spacing, large confident typography, smooth 200-400ms motion with spring easing, tasteful parallax and hover tilt on cards. NOT generic purple gradients.

Typography: Hind Siliguri (Bangla) + Inter (Latin), self-hosted or via font subset, with system fallback. Bangla must be the default and look beautiful: line-height 1.7 for body.

Create design tokens as CSS variables + Tailwind theme (colors, radii, shadows, spacing, type scale), dark theme as default and a light theme. Create reusable components in src/components/: Button, GlassCard, Badge, SeverityTag, VerificationBadge (variants: verified "✓ যাচাইকৃত", uncertain "? অনিশ্চিত", rejected "✗ বাতিল"; there is NO "weak" or "partial" variant), CitationChip, Skeleton, EmptyState, LanguageToggle (bn default, en), Disclaimer (always shows "এটা আইনি পরামর্শ নয় / This is not legal advice").

i18n: use i18next with src/locales/bn.json and en.json; every visible string comes from these files; bn is default. Accessibility: semantic HTML, visible focus rings, 44px touch targets, WCAG AA contrast, respects prefers-reduced-motion. Mobile-first (390px) and fully responsive.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/59e64a6c-14a8-48b3-b5ad-eaeb5c1f5eae).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
