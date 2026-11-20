import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../../i18n'
import { renderApp } from '../../test/render'
import { Footer } from './Footer'
import { Header } from './Header'

afterEach(async () => {
  await i18n.changeLanguage('bn')
})

describe('FR-68 brand in the header and footer', () => {
  it.each(['bn', 'en'])(
    'FR-68 the home link shows the mark and the %s wordmark at every width',
    async (locale) => {
      await i18n.changeLanguage(locale)
      renderApp(<Header />)
      const home = screen.getByRole('link', { name: i18n.t('app.name') })
      expect(home).toHaveAttribute('href', '/')
      // One link for desktop and mobile: never hidden behind a breakpoint.
      expect(home.className).not.toMatch(/(^|\s)(hidden|xl:hidden|xl:flex)(\s|$)/)
      const mark = home.querySelector('svg')
      expect(mark).not.toBeNull()
      expect(mark).toHaveAttribute('aria-hidden', 'true')
      expect(home).toHaveTextContent(i18n.t('app.name'))
    },
  )

  it.each(['bn', 'en'])('FR-68 the footer carries the named mark in %s', async (locale) => {
    await i18n.changeLanguage(locale)
    renderApp(<Footer />)
    const footer = screen.getByRole('contentinfo')
    expect(
      within(footer).getByRole('img', {
        name: i18n.t('brand.markAlt', { name: i18n.t('app.name') }),
      }),
    ).toBeInTheDocument()
    expect(footer).toHaveTextContent(i18n.t('app.name'))
  })
})
