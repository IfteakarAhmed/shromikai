import { describe, expect, it } from 'vitest'
import indexHtml from '../../index.html?raw'
import { resolveSiteUrl } from './siteUrl'

/** The social-preview tags must be absolute on a deployment (FR-85); the build fills the origin in. */
describe('FR-85 index.html social preview', () => {
  const content = (property: string): string => {
    const match = new RegExp(`<meta property="${property}" content="([^"]*)"`).exec(indexHtml)
    const value = match?.[1]
    if (value === undefined) throw new Error(`no ${property} tag`)
    return value
  }

  it('FR-85 og:image is built from the SITE_URL placeholder, not a bare relative path', () => {
    expect(content('og:image')).toBe('%SITE_URL%/og-image.jpg')
  })

  it('FR-85 og:url is built from the SITE_URL placeholder', () => {
    expect(content('og:url')).toBe('%SITE_URL%/')
  })
})

describe('FR-85 resolveSiteUrl', () => {
  it('FR-85 prefers VITE_SITE_URL, then SITE_URL, then the Vercel production host', () => {
    expect(
      resolveSiteUrl({
        VITE_SITE_URL: 'https://a.test',
        SITE_URL: 'https://b.test',
        VERCEL_PROJECT_PRODUCTION_URL: 'c.test',
      }),
    ).toBe('https://a.test')
    expect(
      resolveSiteUrl({ SITE_URL: 'https://b.test', VERCEL_PROJECT_PRODUCTION_URL: 'c.test' }),
    ).toBe('https://b.test')
    expect(resolveSiteUrl({ VERCEL_PROJECT_PRODUCTION_URL: 'c.test' })).toBe('https://c.test')
  })

  it('FR-85 is empty when nothing is set, so development and CI builds stay relative', () => {
    expect(resolveSiteUrl({})).toBe('')
    expect(resolveSiteUrl({ VITE_SITE_URL: '  ' })).toBe('')
  })

  it('FR-85 adds https to a bare host and drops trailing slashes', () => {
    expect(resolveSiteUrl({ VITE_SITE_URL: 'shromikai.vercel.app/' })).toBe(
      'https://shromikai.vercel.app',
    )
    expect(resolveSiteUrl({ VITE_SITE_URL: 'http://localhost:4173//' })).toBe(
      'http://localhost:4173',
    )
  })
})
