import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ContainersDiagram, PipelineDiagram } from './ArchitectureDiagrams'

/**
 * C4: the two architecture pictures are redrawn for both themes and for phones. Each has a wide
 * drawing (from the lg breakpoint) and a tall one for narrow screens; both draw only with the
 * theme tokens, so dark and light read the same, and neither shrinks its text below 12 px.
 */
const WIDE_WIDTH = 918 // the card at the lg breakpoint (max-w-5xl minus gutters and padding)
const NARROW_WIDTH = 334 // the card on a 390 px phone

function drawings(ui: React.ReactElement) {
  const { container } = render(ui)
  const wide = container.querySelector<SVGSVGElement>('svg[data-variant="wide"]')
  const narrow = container.querySelector<SVGSVGElement>('svg[data-variant="narrow"]')
  return { container, wide, narrow }
}

const allowedPaint = /^(none|currentColor|var\(--[\w-]+\)|url\(#[\w-]+\)|transparent)$/

function viewBoxWidth(svg: SVGSVGElement): number {
  return Number((svg.getAttribute('viewBox') ?? '').split(' ')[2])
}

for (const [name, ui] of [
  ['containers', <ContainersDiagram key="c" />],
  ['pipeline', <PipelineDiagram key="p" />],
] as const) {
  describe(`${name} diagram`, () => {
    it('FR-74 has a wide and a tall drawing, each a labelled picture, only one shown at a time', () => {
      const { wide, narrow } = drawings(ui)
      expect(wide).not.toBeNull()
      expect(narrow).not.toBeNull()
      expect(wide?.getAttribute('class')).toMatch(/\bhidden\b/)
      expect(wide?.getAttribute('class')).toMatch(/\blg:block\b/)
      expect(narrow?.getAttribute('class')).toMatch(/\blg:hidden\b/)
      for (const svg of [wide, narrow]) {
        expect(svg?.getAttribute('role')).toBe('img')
        expect(svg?.querySelector('title')?.textContent).not.toBe('')
        expect(svg?.querySelector('desc')?.textContent).not.toBe('')
      }
    })

    it('FR-74 both drawings say the same things', () => {
      const { wide, narrow } = drawings(ui)
      // Lines break differently in the two widths, so compare the words, not the lines.
      const words = (svg: SVGSVGElement | null) =>
        (svg === null ? [] : [...svg.querySelectorAll('text')])
          .map((node) => node.textContent)
          .join(' ')
          .split(/\s+/)
          .sort()
      expect(words(narrow)).toEqual(words(wide))
    })

    it('NFR-08 every colour is a theme token, a gradient or a filter of the drawing: no fixed colour', () => {
      const { container } = drawings(ui)
      for (const element of container.querySelectorAll('svg *')) {
        for (const attribute of ['fill', 'stroke']) {
          const value = element.getAttribute(attribute)
          if (value !== null) {
            expect(value, `${element.tagName} ${attribute}`).toMatch(allowedPaint)
          }
        }
        const style = element.getAttribute('style') ?? ''
        expect(style, element.tagName).not.toMatch(/#[0-9a-f]{3,8}\b|rgb\(|hsl\(|oklch\(/i)
      }
    })

    it('NFR-08 box outlines use the 3:1 field edge, not the faint card border', () => {
      const { container } = drawings(ui)
      const strokes = [...container.querySelectorAll('rect[stroke]')].map((node) =>
        node.getAttribute('stroke'),
      )
      expect(strokes.length).toBeGreaterThan(0)
      expect(strokes).not.toContain('var(--border)')
    })

    it('NFR-08 no id is used twice on the page (gradients, markers and filters)', () => {
      const { container } = drawings(ui)
      const ids = [...container.querySelectorAll('[id]')].map((node) => node.id)
      expect(new Set(ids).size).toBe(ids.length)
    })

    it('NFR-08 text stays at 12 px or more on a phone and at the lg breakpoint', () => {
      const { wide, narrow } = drawings(ui)
      for (const [svg, width] of [
        [wide, WIDE_WIDTH],
        [narrow, NARROW_WIDTH],
      ] as const) {
        if (svg === null) {
          throw new Error('missing drawing')
        }
        const scale = width / viewBoxWidth(svg)
        const small = [...svg.querySelectorAll('text')]
          .map((node) => ({
            text: node.textContent,
            size: Number(node.getAttribute('font-size')) * scale,
          }))
          .filter(({ size }) => size < 11.9)
        expect(small, svg.getAttribute('data-variant') ?? '').toEqual([])
      }
    })

    it('FR-74 the tall drawing is no wider than a phone card, so it needs no sideways scroll', () => {
      const { narrow } = drawings(ui)
      expect(viewBoxWidth(narrow as SVGSVGElement)).toBeLessThanOrEqual(360)
    })
  })
}
