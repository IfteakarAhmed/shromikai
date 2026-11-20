# ADR-0028: Brand mark, icons and the logo social image

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-20 |
| Deciders | Project owner (picked mark C, the শ্র seal, from three candidates), Claude (author) |
| Related | ADR-0023 (fonts, render-scene; the social-image part is superseded here), ADR-0026, ADR-0027; rules R-11, R-12; PRD FR-68, FR-85 |

## Context

The header showed a generic shield icon from the UI icon set, the site had no favicon or touch icon,
and the social image was a still of the landing scene with no name on it. The product needs an
original mark that is calm and trustworthy, reads as Bangla-first, works on the ink-navy dark theme
and the pale light theme, and stays legible from 16 px to a 1200 × 630 card. Three hand-drawn
candidates were shown on a local preview page: a two-sided shield with a check, a balance whose
upright is a worker, and the conjunct শ্র on a shield with a verified badge. The owner chose the
last.

## Decision

1. **The mark.** A shield on a 64-unit grid in one ink, with the conjunct শ্র (the first syllable
   of শ্রমিক) knocked out and a round verified badge with a knocked-out check at its base. Because
   everything else is cut out, one colour draws the whole mark and the background shows through: brand
   green `#2ee6a6` on navy, `#006a40` on the light theme, white or near-black for monochrome.
2. **A small variant for 16 to 32 px.** At 16 px the conjunct and badge blur into the shield (checked
   at 16, 32 and 180 px), so favicons and other tiny uses take the shield with one bold knocked-out
   check. The full mark is used from about 36 px up, including the 180 px apple-touch-icon.
3. **Type.** Wordmarks use the display face Tiro Bangla: শ্রমিকAI and ShromikAI, with "AI" in the
   brand colour. In the app the wordmark is live text (`Wordmark`) so it follows the locale; the
   lockup files outline it.
4. **Hand-written geometry, outlined type, no font dependency.** Shield, badge and checks are written
   by hand in `frontend/scripts/brand/build-brand.py`. The letterforms (the conjunct, the wordmarks
   and the social-image lines in Tiro Bangla and Hind Siliguri) are shaped with HarfBuzz and outlined
   with fontTools from the self-hosted Fontsource files, once, in a throwaway virtual environment
   outside the repository. Neither tool is a project dependency; the generated SVGs and
   `src/components/brand/brandPaths.ts` are committed. No SVG contains `<text>`, a font reference, an
   image or an external link (`brandAssets.test.ts`).
5. **Assets.** `public/brand/`: `mark`, `mark-small`, `lockup-en` and `lockup-bn`, each in brand
   (dark), `-light`, `-mono-white` and `-mono-black`. `public/favicon.svg` follows the browser's
   colour scheme; `public/favicon-32.png`, `public/apple-touch-icon.png` (180 px, full-bleed navy) and
   `public/og-image.png` (1200 × 630) are rendered from `scripts/brand/*.source.svg` by
   `npm run render:brand` (headless Chromium from the existing Playwright dev dependency).
6. **The social image is the logo card.** `og-image.png` replaces the scene still `og-image.jpg`;
   `scripts/render-scene.mjs` no longer writes a social image. The card states "এটা আইনি পরামর্শ নয় ·
   Not legal advice" (R-12). `index.html` links the favicons and touch icon and adds `og:image:type`,
   `og:image:alt` and the twitter image tags; URLs stay absolute through `%SITE_URL%` (ADR-0026).
7. **In the app.** `BrandMark` draws the mark inline in `currentColor` with the theme's
   `text-primary`, so dark and light need no second asset; mask IDs come from `useId`, so several marks
   on one page do not collide. Header (one home link for desktop and mobile), landing hero and footer
   carry it in live and replay builds alike. A mark beside the visible name is decorative
   (`aria-hidden`); a standalone mark (hero, footer) is an image named by `brand.markAlt` in bn and en.

## Font licence

Tiro Bangla (Copyright 2020 The Indigo Project Authors) and Hind Siliguri are licensed under the SIL
Open Font License 1.1, as shipped in `@fontsource/tiro-bangla` and `@fontsource/hind-siliguri`. The
OFL restricts redistribution of the Font Software itself; artwork made with the font, including
outlined letterforms in a logo, is not Font Software and may be used, including commercially, without
the OFL applying to it (OFL 1.1 FAQ, "Can I use the font in a logo?"). No font file or modified font is
distributed by this change. Credit: the conjunct শ্র in the mark and the wordmarks are outlined from
Tiro Bangla by the Indigo Project / Tiro Typeworks; the social-image lines use Hind Siliguri.

## Consequences

- The brand lives in one script and one component; changing the geometry means re-running the script
  and `npm run render:brand`, then committing the outputs.
- The initial bundle grows only by the inline paths (about 2 KB before gzip); budgets unchanged.
- The social image no longer shows the landing scene; the scene still drives the lite posters.

## Alternatives considered

- **Two-sided shield with a check** and **worker-as-fulcrum balance**: both cleaner at 16 px, but less
  distinctive and not Bangla-first; the owner picked the শ্র seal.
- **Live `<text>` in the SVGs**: depends on the viewer having Tiro Bangla; rejected for files that
  leave the app.
- **A raster logo**: does not scale or theme; rejected.

## Verification

`BrandMark.test.tsx`, `brandAssets.test.ts`, `components/shell/brand.test.tsx`, the brand cases in
`LandingPage.test.tsx` and `siteUrl.test.ts`; manual check of the built replay at 390 px in dark and
light.
