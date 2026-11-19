import { describe, expect, it } from 'vitest'
import indexHtml from '../../index.html?raw'

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
