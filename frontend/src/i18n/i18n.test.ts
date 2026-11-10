import { describe, expect, it } from 'vitest'
import bn from './bn.json'
import en from './en.json'
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

  it('R-12 states that this is not legal advice in both languages', () => {
    expect(bn.app.disclaimer).toContain('এটা আইনি পরামর্শ নয়')
    expect(en.app.disclaimer).toContain('not legal advice')
  })
})
