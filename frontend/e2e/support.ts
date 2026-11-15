import { expect, type Page } from '@playwright/test'
import bn from '../src/i18n/bn.json' with { type: 'json' }
import en from '../src/i18n/en.json' with { type: 'json' }

export type Locale = 'bn' | 'en'
export const LOCALES: readonly Locale[] = ['bn', 'en']
const dictionaries: Record<Locale, unknown> = { bn, en }

/** The text of an interface string, read from the same files the app uses. */
export function text(locale: Locale, key: string, values: Record<string, string> = {}): string {
  let node: unknown = dictionaries[locale]
  for (const part of key.split('.')) {
    node = (node as Record<string, unknown>)[part]
  }
  if (typeof node !== 'string') {
    throw new Error(`No string ${key} in ${locale}`)
  }
  return node.replace(/\{\{(\w+)\}\}/g, (_, name: string) => values[name] ?? '')
}

/** Bangla digits to a number, so counts can be read in either language. */
export function toNumber(value: string): number {
  return Number(
    value.replace(/[০-৯]/g, (digit) => String('০১২৩৪৫৬৭৮৯'.indexOf(digit))).replace(/[^\d]/g, ''),
  )
}

/** Opens the app in a language, the way a visitor who chose it earlier would find it. */
export async function inLocale(page: Page, locale: Locale): Promise<void> {
  await page.addInitScript((value) => {
    window.localStorage.setItem('shromikai.locale', value)
  }, locale)
}

/** Runs the recorded sample from the upload page and waits for the result. */
export async function analyseSample(page: Page, locale: Locale): Promise<void> {
  await page.getByRole('button', { name: text(locale, 'upload.sample') }).click()
  await expect(page.getByRole('heading', { name: text(locale, 'results.title') })).toBeVisible({
    timeout: 60_000,
  })
}

/** Every interface string of the English file that has at least two words. */
export function englishPhrases(): string[] {
  const out: string[] = []
  const walk = (node: unknown) => {
    if (typeof node === 'string') {
      const plain = node.replace(/\{\{\w+\}\}/g, '').trim()
      if (/[A-Za-z]{3,}\s+[A-Za-z]{2,}/.test(plain) && plain.length >= 8) {
        out.push(plain)
      }
    } else if (typeof node === 'object' && node !== null) {
      Object.values(node).forEach(walk)
    }
  }
  walk(en)
  return out
}

/** Signs in as the demo worker, runs the recorded sample and waits for the result. */
export async function openResult(page: Page, locale: Locale): Promise<void> {
  await page.goto('/demo-user')
  await expect(page.getByRole('heading', { name: text(locale, 'upload.title') })).toBeVisible()
  await analyseSample(page, locale)
}
