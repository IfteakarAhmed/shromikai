import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../../i18n'
import { BrandMark } from './BrandMark'

afterEach(async () => {
  await i18n.changeLanguage('bn')
})

describe('FR-68 brand mark', () => {
  it('FR-68 a standalone mark is an image named in Bangla by default', async () => {
    await i18n.changeLanguage('bn')
    render(<BrandMark labelled />)
    const mark = screen.getByRole('img', {
      name: i18n.t('brand.markAlt', { name: i18n.t('app.name') }),
    })
    expect(mark.getAttribute('aria-label')).toMatch(/শ্রমিকAI/)
  })

  it('FR-68 the same mark is named in English when the locale is English', async () => {
    await i18n.changeLanguage('en')
    render(<BrandMark labelled />)
    expect(screen.getByRole('img', { name: /ShromikAI logo/ })).toBeInTheDocument()
  })

  it('FR-68 a mark beside the visible name is decorative, so the name is not read twice', () => {
    const { container } = render(<BrandMark />)
    const svg = container.querySelector('svg')
    expect(svg).toHaveAttribute('aria-hidden', 'true')
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('FR-68 draws in the theme colour, so dark and light themes need no second asset', () => {
    const { container } = render(<BrandMark />)
    expect(container.querySelector('svg')).toHaveClass('text-primary')
    expect(container.querySelector('g[mask]')).toHaveAttribute('fill', 'currentColor')
  })

  it('FR-68 two marks on one page use distinct mask ids', () => {
    const { container } = render(
      <>
        <BrandMark />
        <BrandMark />
      </>,
    )
    const ids = [...container.querySelectorAll('mask')].map((mask) => mask.id)
    expect(ids).toHaveLength(2)
    expect(new Set(ids).size).toBe(2)
    for (const id of ids) {
      expect(container.querySelector(`[mask="url(#${id})"]`)).not.toBeNull()
    }
  })

  it('FR-68 the small variant drops the letter and badge for 16 to 32 px uses', () => {
    const full = render(<BrandMark />).container.querySelectorAll('path').length
    const { container } = render(<BrandMark variant="small" />)
    expect(container.querySelectorAll('path').length).toBeLessThan(full)
  })
})
