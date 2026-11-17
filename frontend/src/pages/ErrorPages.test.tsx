import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderApp } from '../test/render'
import { ForbiddenPage } from './ForbiddenPage'
import { NotFoundPage } from './NotFoundPage'

describe('403 and 404 pages', () => {
  it('FR-68 the 404 page is a glass card with the code in Bangla digits, a reason and a way home', () => {
    const { container } = renderApp(
      <Routes>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>,
      '/nowhere',
    )
    expect(screen.getByRole('heading', { level: 1, name: 'পাতাটি পাওয়া যায়নি' })).toBeVisible()
    expect(screen.getByText('৪০৪')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByText(/ঠিকানাটি/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'মূল পাতা' })).toHaveAttribute('href', '/')
    expect(container.querySelector('.depth-card')).not.toBeNull()
  })

  it('FR-68 the 404 page also offers the pages people usually look for', () => {
    renderApp(
      <Routes>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>,
      '/nowhere',
    )
    expect(screen.getByRole('link', { name: 'চুক্তি বিশ্লেষণ' })).toHaveAttribute('href', '/app')
    expect(screen.getByRole('link', { name: 'ডকুমেন্টেশন' })).toHaveAttribute('href', '/docs')
  })

  it('FR-68 the 403 page tells the user it is a permission problem and offers another account', () => {
    const { container } = renderApp(<ForbiddenPage />, '/admin')
    expect(screen.getByRole('heading', { level: 1, name: 'অনুমতি নেই' })).toBeVisible()
    expect(screen.getByText('৪০৩')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent('এই পাতাটি দেখার অনুমতি আপনার নেই')
    expect(screen.getByRole('link', { name: 'মূল পাতা' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'অন্য অ্যাকাউন্টে ঢুকুন' })).toHaveAttribute(
      'href',
      '/login',
    )
    expect(container.querySelector('.depth-card')).not.toBeNull()
  })

  it('R-11 both pages exist in English too', async () => {
    const i18n = (await import('../i18n')).default
    await i18n.changeLanguage('en')
    renderApp(<ForbiddenPage />, '/admin')
    expect(screen.getByText('403')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Use another account' })).toBeInTheDocument()
  })
})
