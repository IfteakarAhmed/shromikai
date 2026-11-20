import { describe, expect, it } from 'vitest'
import indexHtml from '../../../index.html?raw'

/** Every shipped brand SVG, as text. */
const svgs = import.meta.glob<string>(['/public/brand/*.svg', '/public/favicon.svg'], {
  query: '?raw',
  import: 'default',
  eager: true,
})
/** Every file in public/, by path (contents not loaded). */
const publicFiles = Object.keys(import.meta.glob('/public/**/*.{png,svg}'))

describe('FR-68 brand asset set', () => {
  it('FR-68 ships the mark, small mark and both lockups in brand, light and mono variants', () => {
    const names = Object.keys(svgs).map((path) => path.replace('/public/brand/', ''))
    for (const variant of ['', '-light', '-mono-white', '-mono-black']) {
      expect(names).toContain(`mark${variant}.svg`)
      expect(names).toContain(`mark-small${variant}.svg`)
      expect(names).toContain(`lockup-en${variant}.svg`)
      expect(names).toContain(`lockup-bn${variant}.svg`)
    }
  })

  it('FR-68 every SVG is self-contained: outlined type, no fonts, images or external links', () => {
    expect(Object.keys(svgs).length).toBeGreaterThan(16)
    for (const [path, svg] of Object.entries(svgs)) {
      expect(svg, path).toMatch(/^<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg" viewBox="/)
      expect(svg, path).toMatch(/<title[^>]*>[^<]+<\/title>/)
      expect(svg, path).not.toMatch(/<text|<image|font-family|@import|href="http/)
    }
  })

  it('FR-85 the favicons, touch icon and social image exist in public/', () => {
    for (const file of [
      '/public/favicon.svg',
      '/public/favicon-32.png',
      '/public/apple-touch-icon.png',
      '/public/og-image.png',
    ]) {
      expect(publicFiles).toContain(file)
    }
  })
})

describe('FR-85 index.html icons and social preview', () => {
  const tag = (pattern: RegExp): string => {
    const match = pattern.exec(indexHtml)
    if (match?.[1] === undefined) throw new Error(`no match for ${pattern.source}`)
    return match[1]
  }

  it('FR-85 links the SVG favicon, the 32 px PNG fallback and the apple-touch-icon', () => {
    expect(tag(/<link rel="icon" type="image\/svg\+xml" href="([^"]+)"/)).toBe('/favicon.svg')
    expect(tag(/<link rel="icon" type="image\/png" sizes="32x32" href="([^"]+)"/)).toBe(
      '/favicon-32.png',
    )
    expect(tag(/<link rel="apple-touch-icon" sizes="180x180" href="([^"]+)"/)).toBe(
      '/apple-touch-icon.png',
    )
  })

  it('FR-85 the social image is the absolute logo card with alt text and a twitter copy', () => {
    expect(tag(/<meta property="og:image" content="([^"]*)"/)).toBe('%SITE_URL%/og-image.png')
    expect(tag(/<meta property="og:image:type" content="([^"]*)"/)).toBe('image/png')
    expect(tag(/property="og:image:alt"\s+content="([^"]*)"/)).toMatch(/শ্রমিকAI/)
    expect(tag(/<meta name="twitter:image" content="([^"]*)"/)).toBe('%SITE_URL%/og-image.png')
    expect(tag(/name="twitter:image:alt"\s+content="([^"]*)"/)).toMatch(/শ্রমিকAI/)
    expect(indexHtml).not.toContain('og-image.jpg')
  })
})
