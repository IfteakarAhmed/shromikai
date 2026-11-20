"""Builds the ShromikAI brand SVGs from hand-written geometry plus outlined type (ADR-0028).

The shield, badge and check are written by hand below on a 64-unit grid. The letterforms (the
conjunct শ্র in the mark, the wordmarks and the social-image lines) are outlined once from the
self-hosted OFL fonts (Tiro Bangla, Hind Siliguri) so no SVG depends on a font being installed.

Not part of the build and not a project dependency. Run it in a throwaway virtual environment
outside the repository, then render the PNGs with `node scripts/render-brand.mjs`:

    python3 -m venv /tmp/brand-venv
    /tmp/brand-venv/bin/pip install fonttools brotli uharfbuzz
    /tmp/brand-venv/bin/python scripts/brand/build-brand.py
    npx prettier --write src/components/brand/brandPaths.ts

Outputs: public/brand/*.svg, public/favicon.svg, scripts/brand/*.source.svg (PNG render sources) and
src/components/brand/brandPaths.ts (the same geometry for the inline React mark).
"""

import io
import os

import uharfbuzz as hb
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONTS = os.path.join(ROOT, "node_modules", "@fontsource")
PUBLIC = os.path.join(ROOT, "public")
BRAND = os.path.join(PUBLIC, "brand")
SOURCES = os.path.join(ROOT, "scripts", "brand")  # render sources for the PNGs; not shipped

# Brand tokens from src/index.css, converted from oklch to sRGB hex.
GREEN = "#2ee6a6"  # --primary, dark theme
GREEN_LIGHT = "#006a40"  # --primary, light theme
NAVY = "#040915"  # --background, dark theme
MIST = "#f2f6fb"  # --background, light theme
CREAM = "#f4f1ea"  # --foreground, dark theme
INK = "#131f35"  # --foreground, light theme
BLACK = "#0a0f1c"
WHITE = "#ffffff"

# ---------------------------------------------------------------- hand-written geometry (64 grid)
SHIELD = "M32 5L54 12.5V30C54 44 44.5 53.5 32 59C19.5 53.5 10 44 10 30V12.5Z"
BADGE_CX, BADGE_CY, BADGE_R, BADGE_GAP = 45.5, 45.5, 7.4, 1.4
BADGE = (
    f"M{BADGE_CX - BADGE_R} {BADGE_CY}a{BADGE_R} {BADGE_R} 0 1 0 {2 * BADGE_R} 0"
    f"a{BADGE_R} {BADGE_R} 0 1 0 {-2 * BADGE_R} 0Z"
)
RING_R = BADGE_R + BADGE_GAP
RING = (
    f"M{BADGE_CX - RING_R} {BADGE_CY}a{RING_R} {RING_R} 0 1 0 {2 * RING_R} 0"
    f"a{RING_R} {RING_R} 0 1 0 {-2 * RING_R} 0Z"
)
CHECK = "M42.3 45.7L44.7 48.1L48.9 43.5"
CHECK_WIDTH = 2.2
# The small variant (favicon, 16 to 32 px): the shield with a bold knocked-out check only; the
# conjunct and the badge do not survive 16 px (checked visually at 16, 32 and 180 px).
SMALL_CHECK = "M20.5 32.5L28.5 40.5L44 24.5"
SMALL_CHECK_WIDTH = 6.4


def num(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---------------------------------------------------------------- outlined type
_cache = {}


def _font(path):
    if path not in _cache:
        tt = TTFont(os.path.join(FONTS, path))
        tt.flavor = None
        buf = io.BytesIO()
        tt.save(buf)
        data = buf.getvalue()
        tt = TTFont(io.BytesIO(data))
        _cache[path] = (tt, hb.Font(hb.Face(data)))
    return _cache[path]


TIRO_BN = "tiro-bangla/files/tiro-bangla-bengali-400-normal.woff2"
TIRO_LA = "tiro-bangla/files/tiro-bangla-latin-400-normal.woff2"
HIND_BN = "hind-siliguri/files/hind-siliguri-bengali-{w}-normal.woff2"
HIND_LA = "hind-siliguri/files/hind-siliguri-latin-{w}-normal.woff2"


def _is_bengali(ch):
    return "ঀ" <= ch <= "৿" or ch in "‌‍"


def runs(text, bn_font, la_font):
    """Splits text into script runs; spaces join the run before them."""
    out = []
    for ch in text:
        font = bn_font if _is_bengali(ch) else la_font
        if ch == " " and out:
            font = out[-1][1]
        if out and out[-1][1] == font:
            out[-1][0] += ch
        else:
            out.append([ch, font])
    return [(t, f) for t, f in out]


def outline(segments, size, ox, oy):
    """Outlines (text, font) segments at font size `size`, baseline at (ox, oy).
    Returns (path d, advance width, (xmin, ymin, xmax, ymax))."""
    x = 0.0
    commands = []
    bounds = None
    for text, path in segments:
        tt, font = _font(path)
        upm = tt["head"].unitsPerEm
        scale = size / upm
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(font, buf)
        glyphs = tt.getGlyphSet()
        order = tt.getGlyphOrder()
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            name = order[info.codepoint]
            t = (scale, 0, 0, -scale, ox + x + pos.x_offset * scale, oy - pos.y_offset * scale)
            pen = SVGPathPen(glyphs, ntos=num)
            glyphs[name].draw(TransformPen(pen, t))
            commands.append(pen.getCommands())
            bp = BoundsPen(glyphs)
            glyphs[name].draw(TransformPen(bp, t))
            if bp.bounds:
                b = bp.bounds
                bounds = b if bounds is None else (
                    min(bounds[0], b[0]), min(bounds[1], b[1]),
                    max(bounds[2], b[2]), max(bounds[3], b[3]))
            x += pos.x_advance * scale
    return "".join(c for c in commands if c), x, bounds


def fit(segments, size, max_width):
    _, adv, _ = outline(segments, size, 0, 0)
    return size if adv <= max_width else size * max_width / adv


# The conjunct শ্র, centred on the shield's optical centre (30, 29) at 26 units.
_, _, gb = outline([("শ্র", TIRO_BN)], 26, 0, 0)
GLYPH, _, _ = outline(
    [("শ্র", TIRO_BN)], 26, 30 - (gb[0] + gb[2]) / 2, 29 - (gb[1] + gb[3]) / 2
)


# ---------------------------------------------------------------- mark builders
def mark_body(ink, uid):
    """The full mark in one ink: shield and badge, with the conjunct, ring and check knocked out."""
    return (
        f'<defs><mask id="{uid}" maskUnits="userSpaceOnUse" x="0" y="0" width="64" height="64">'
        f'<rect width="64" height="64" fill="#fff"/>'
        f'<path d="{GLYPH}" fill="#000"/>'
        f'<path d="{RING}" fill="#000"/><path d="{BADGE}" fill="#fff"/>'
        f'<path d="{CHECK}" fill="none" stroke="#000" stroke-width="{CHECK_WIDTH}" '
        f'stroke-linecap="round" stroke-linejoin="round"/></mask></defs>'
        f'<g fill="{ink}" mask="url(#{uid})"><path d="{SHIELD}"/><path d="{BADGE}"/></g>'
    )


def small_body(ink, uid):
    return (
        f'<defs><mask id="{uid}" maskUnits="userSpaceOnUse" x="0" y="0" width="64" height="64">'
        f'<rect width="64" height="64" fill="#fff"/>'
        f'<path d="{SMALL_CHECK}" fill="none" stroke="#000" stroke-width="{SMALL_CHECK_WIDTH}" '
        f'stroke-linecap="round" stroke-linejoin="round"/></mask></defs>'
        f'<path d="{SHIELD}" fill="{ink}" mask="url(#{uid})"/>'
    )


def svg(view_w, view_h, title, body, width=None, height=None, lang="en"):
    size = f' width="{width}" height="{height}"' if width else ""
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {num(view_w)} {num(view_h)}"{size} '
        f'role="img" aria-label="{title}"><title xml:lang="{lang}">{title}</title>{body}</svg>\n'
    )


MARK_TITLE = "ShromikAI logo: the Bangla letter শ্র on a shield with a verified check"


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"{os.path.relpath(path, ROOT)}: {len(content.encode())} bytes")


# ---------------------------------------------------------------- lockups
def lockup(locale, ink, accent, text_ink, uid):
    size = 34
    baseline = 43.5
    x0 = 78
    if locale == "bn":
        segs = [("শ্রমিক", TIRO_BN)]
    else:
        segs = [("Shromik", TIRO_LA)]
    name, adv, _ = outline(segs, size, x0, baseline)
    ai, adv_ai, _ = outline([("AI", TIRO_LA)], size, x0 + adv, baseline)
    width = x0 + adv + adv_ai + 2
    body = (
        mark_body(ink, uid)
        + f'<path d="{name}" fill="{text_ink}"/><path d="{ai}" fill="{accent}"/>'
    )
    title = "শ্রমিকAI" if locale == "bn" else "ShromikAI"
    return svg(width, 64, title, body, lang=locale)


# ---------------------------------------------------------------- social image (1200 x 630)
def og_image():
    W, H = 1200, 630
    x = 500
    max_w = W - x - 64
    parts = [
        f'<rect width="{W}" height="{H}" fill="{NAVY}"/>',
        '<defs><radialGradient id="g1" cx="270" cy="315" r="460" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{GREEN}" stop-opacity=".22"/>'
        f'<stop offset="1" stop-color="{GREEN}" stop-opacity="0"/></radialGradient>'
        '<radialGradient id="g2" cx="1080" cy="40" r="520" gradientUnits="userSpaceOnUse">'
        '<stop offset="0" stop-color="#3b3f9e" stop-opacity=".35"/>'
        '<stop offset="1" stop-color="#3b3f9e" stop-opacity="0"/></radialGradient></defs>',
        f'<rect width="{W}" height="{H}" fill="url(#g1)"/>',
        f'<rect width="{W}" height="{H}" fill="url(#g2)"/>',
        f'<g transform="translate(100 145) scale(5.3)">{mark_body(GREEN, "og-mark")}</g>',
    ]

    def line(segs, size, baseline, fill):
        size = fit(segs, size, max_w)
        d, adv, _ = outline(segs, size, x, baseline)
        return d, adv, size

    d, adv, size = line([("শ্রমিক", TIRO_BN)], 104, 262, CREAM)
    ai, _, _ = outline([("AI", TIRO_LA)], size, x + adv, 262)
    parts.append(f'<path d="{d}" fill="{CREAM}"/><path d="{ai}" fill="{GREEN}"/>')
    d, adv, size = line([("Shromik", TIRO_LA)], 46, 330, "#aab6cc")
    ai, _, _ = outline([("AI", TIRO_LA)], size, x + adv, 330)
    parts.append(f'<path d="{d}" fill="#aab6cc"/><path d="{ai}" fill="{GREEN}"/>')
    parts.append(f'<rect x="{x}" y="368" width="88" height="4" rx="2" fill="{GREEN}"/>')
    bn = HIND_BN.format(w=600)
    la = HIND_LA.format(w=600)
    d, _, _ = line(runs("যাচাইকৃত চাকরির চুক্তি বিশ্লেষণ", bn, la), 38, 432, CREAM)
    parts.append(f'<path d="{d}" fill="{CREAM}"/>')
    d, _, _ = line(
        runs("Verified employment-contract analysis for Bangladesh",
             HIND_BN.format(w=400), HIND_LA.format(w=400)), 27, 478, "#aab6cc")
    parts.append(f'<path d="{d}" fill="#aab6cc"/>')
    d, _, _ = line(
        runs("এটা আইনি পরামর্শ নয় · Not legal advice",
             HIND_BN.format(w=400), HIND_LA.format(w=400)), 21, 548, "#7f8aa3")
    parts.append(f'<path d="{d}" fill="#7f8aa3"/>')
    title = "শ্রমিকAI লোগো ও শিরোনাম: যাচাইকৃত চাকরির চুক্তি বিশ্লেষণ"
    return svg(W, H, title, "".join(parts), W, H, lang="bn")


# ---------------------------------------------------------------- favicon and touch icon
def favicon():
    # One file for light and dark browser chrome: deep green on light tabs, brand green on dark.
    body = (
        f"<style>path{{fill:{GREEN_LIGHT}}}"
        f"@media (prefers-color-scheme:dark){{path{{fill:{GREEN}}}}}</style>"
        f'<path d="{SHIELD}" mask="url(#fav)"/>'
        f'<defs><mask id="fav" maskUnits="userSpaceOnUse" x="0" y="0" width="64" height="64">'
        f'<rect width="64" height="64" fill="#fff"/>'
        f'<path d="{SMALL_CHECK}" style="fill:none" stroke="#000" '
        f'stroke-width="{SMALL_CHECK_WIDTH}" stroke-linecap="round" stroke-linejoin="round"/>'
        f"</mask></defs>"
    )
    return svg(64, 64, "ShromikAI", body)


def touch_icon():
    # Full-bleed navy square (iOS rounds the corners itself) with the full mark.
    body = (
        f'<rect width="180" height="180" fill="{NAVY}"/>'
        f'<g transform="translate(19 17) scale(2.22)">{mark_body(GREEN, "touch")}</g>'
    )
    return svg(180, 180, "ShromikAI", body, 180, 180)


def favicon_png_source():
    # The 32 px PNG is rendered from the small variant on a navy tile so it reads on any tab.
    body = (
        f'<rect width="64" height="64" rx="12" fill="{NAVY}"/>'
        f'<g transform="translate(3 2) scale(0.906)">{small_body(GREEN, "f32")}</g>'
    )
    return svg(64, 64, "ShromikAI", body, 32, 32)


# ---------------------------------------------------------------- TypeScript for the inline mark
def brand_paths_ts():
    return f"""// Generated by scripts/brand/build-brand.py (ADR-0028); do not edit by hand.
// The conjunct শ্র is outlined from Tiro Bangla (SIL Open Font License 1.1); the rest is hand-written
// geometry on a 64-unit grid.

/** The shield outline. */
export const SHIELD = '{SHIELD}'
/** The conjunct শ্র, knocked out of the shield. */
export const GLYPH = '{GLYPH}'
/** The verified badge, its knocked-out ring and its check. */
export const BADGE = '{BADGE}'
export const RING = '{RING}'
export const CHECK = '{CHECK}'
export const CHECK_WIDTH = {CHECK_WIDTH}
/** The small variant's bold check (favicon and 16 to 32 px uses). */
export const SMALL_CHECK = '{SMALL_CHECK}'
export const SMALL_CHECK_WIDTH = {SMALL_CHECK_WIDTH}
"""


def main():
    marks = {
        "mark.svg": GREEN,
        "mark-light.svg": GREEN_LIGHT,
        "mark-mono-white.svg": WHITE,
        "mark-mono-black.svg": BLACK,
    }
    for name, ink in marks.items():
        write(os.path.join(BRAND, name), svg(64, 64, MARK_TITLE, mark_body(ink, "m")))
    small = {"mark-small.svg": GREEN, "mark-small-light.svg": GREEN_LIGHT,
             "mark-small-mono-white.svg": WHITE, "mark-small-mono-black.svg": BLACK}
    for name, ink in small.items():
        write(os.path.join(BRAND, name), svg(64, 64, "ShromikAI", small_body(ink, "s")))
    variants = {
        "": (GREEN, GREEN, CREAM),
        "-light": (GREEN_LIGHT, GREEN_LIGHT, INK),
        "-mono-white": (WHITE, WHITE, WHITE),
        "-mono-black": (BLACK, BLACK, BLACK),
    }
    for locale in ("en", "bn"):
        for suffix, (ink, accent, text) in variants.items():
            write(os.path.join(BRAND, f"lockup-{locale}{suffix}.svg"),
                  lockup(locale, ink, accent, text, "m"))
    write(os.path.join(PUBLIC, "favicon.svg"), favicon())
    write(os.path.join(SOURCES, "favicon-32.source.svg"), favicon_png_source())
    write(os.path.join(SOURCES, "apple-touch-icon.source.svg"), touch_icon())
    write(os.path.join(SOURCES, "og-image.source.svg"), og_image())
    write(os.path.join(ROOT, "src", "components", "brand", "brandPaths.ts"), brand_paths_ts())


if __name__ == "__main__":
    main()
