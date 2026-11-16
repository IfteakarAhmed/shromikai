import i18n from 'i18next'
import { describe, expect, it } from 'vitest'
import '../i18n'
import { warningText } from './warnings'

describe('warningText (screenshot review, R-11)', () => {
  it('R-11 shows a known warning in Bangla, not the English text of the API', async () => {
    await i18n.changeLanguage('bn')
    const text = warningText(i18n.t, {
      code: 'counter-offer-unchecked',
      message: 'No active rule applied',
    })
    expect(text).not.toMatch(/[A-Za-z]/)
  })

  it('R-11 an unknown code falls back to the message of the API', async () => {
    await i18n.changeLanguage('bn')
    expect(warningText(i18n.t, { code: 'new-code', message: 'From the API' })).toBe('From the API')
  })
})
