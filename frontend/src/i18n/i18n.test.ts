import { describe, expect, it } from 'vitest'
import bn from './bn.json'
import en from './en.json'
import { afterEach, vi } from 'vitest'
import { rememberLanguage, storedLanguage } from '.'

function keys(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) {
    return [prefix]
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keys(child, prefix ? `${prefix}.${key}` : key),
  )
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{\{(\w+)\}\}/g)].map((match) => match[1] ?? '').sort()
}

function leaf(source: unknown, path: string): string {
  return String(
    path.split('.').reduce<unknown>((node, key) => (node as Record<string, unknown>)[key], source),
  )
}

describe('i18n', () => {
  it('NFR-09 bn and en have the same keys', () => {
    expect(keys(bn).sort()).toEqual(keys(en).sort())
  })

  it('NFR-09 bn and en use the same placeholders', () => {
    for (const key of keys(bn)) {
      expect(placeholders(leaf(bn, key)), key).toEqual(placeholders(leaf(en, key)))
    }
  })

  it('NFR-09 defaults to Bangla and stores only the locale preference', () => {
    expect(storedLanguage()).toBe('bn')
    rememberLanguage('en')
    expect(storedLanguage()).toBe('en')
    expect(Object.keys(window.localStorage)).toEqual(['shromikai.locale'])
  })

  it('R-11 bn strings use no English words except the brand and the English switch', () => {
    // app.name is the brand শ্রমিকAI; language.switch names the target language in English;
    // common.recorded is the label the product owner fixed for recorded data.
    const allowed = new Set(['app.name', 'language.switch', 'language.english', 'common.recorded'])
    for (const key of keys(bn).filter((k) => !allowed.has(k))) {
      const text = leaf(bn, key).replace(/\{\{\w+\}\}/g, '')
      expect(text, key).not.toMatch(/[A-Za-z]/)
    }
  })

  it('R-12 states that this is not legal advice in both languages', () => {
    expect(bn.app.disclaimer).toContain('এটা আইনি পরামর্শ নয়')
    expect(en.app.disclaimer).toContain('not legal advice')
  })

  it('R-11 no bn string is the English text left in place', () => {
    // Strings that are the same in both languages on purpose: the brand and the English switch.
    const same = new Set(['app.name', 'language.switch', 'language.english'])
    const untranslated = keys(bn).filter((key) => {
      const text = leaf(bn, key)
      return !same.has(key) && text === leaf(en, key) && /[A-Za-z]{3,}/.test(text)
    })

    expect(untranslated).toEqual([])
  })
})

describe('first load language', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.resetModules()
    window.localStorage.clear()
  })

  async function freshLanguage(browserLanguage: string): Promise<string> {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue(browserLanguage)
    vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue([browserLanguage])
    vi.resetModules()
    const fresh = await import('./index')
    return fresh.default.language
  }

  it('R-11 starts in Bangla whatever the browser language is', async () => {
    expect(await freshLanguage('en-US')).toBe('bn')
    expect(await freshLanguage('fr-FR')).toBe('bn')
  })

  it('R-11 only a stored choice of the user overrides Bangla', async () => {
    window.localStorage.setItem('shromikai.locale', 'en')

    expect(await freshLanguage('bn-BD')).toBe('en')
  })
})
