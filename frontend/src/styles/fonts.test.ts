import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * The type pairing (C1, plan pairing C): Tiro Bangla for headings, Hind Siliguri for body text,
 * Space Grotesk for numerals and interface labels. All self-hosted Fontsource subsets; no runtime
 * request to a font CDN.
 */
const root = resolve(__dirname, '../..')
const fonts = readFileSync(resolve(root, 'src/fonts.ts'), 'utf8')
const imports = [...fonts.matchAll(/^import '([^']+)'/gm)].map((match) => match[1])

describe('fonts', () => {
  it('NFR-09 self-hosts exactly the subsets and weights of the chosen pairing', () => {
    expect(imports.sort()).toEqual(
      [
        '@fontsource/hind-siliguri/bengali-400.css',
        '@fontsource/hind-siliguri/bengali-600.css',
        '@fontsource/hind-siliguri/latin-400.css',
        '@fontsource/hind-siliguri/latin-600.css',
        '@fontsource/space-grotesk/latin-500.css',
        '@fontsource/space-grotesk/latin-700.css',
        '@fontsource/tiro-bangla/bengali-400.css',
        '@fontsource/tiro-bangla/latin-400.css',
      ].sort(),
    )
  })

  it('NFR-09 never asks a font CDN for anything', () => {
    for (const file of ['index.html', 'src/fonts.ts', 'src/index.css']) {
      expect(readFileSync(resolve(root, file), 'utf8'), file).not.toMatch(
        /fonts\.googleapis|fonts\.gstatic|use\.typekit|fonts\.bunny/,
      )
    }
  })

  it('NFR-09 names the three families in the type tokens, with real fallbacks', () => {
    const css = readFileSync(resolve(root, 'src/index.css'), 'utf8')
    expect(css).toMatch(/--font-display:\s*'Tiro Bangla',[^;]*serif;/)
    expect(css).toMatch(/--font-body:\s*'Hind Siliguri',[^;]*sans-serif;/)
    expect(css).toMatch(/--font-ui:\s*'Space Grotesk',\s*'Hind Siliguri',[^;]*sans-serif;/)
  })
})
