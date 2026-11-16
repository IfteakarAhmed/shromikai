import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DisplayText } from './DisplayText'

describe('DisplayText', () => {
  it('NFR-09 drops a trailing danda from a display heading', () => {
    const { container } = render(
      <h1>
        <DisplayText text="আপনার চাকরির চুক্তি, আইনের চোখে।" />
      </h1>,
    )
    expect(container.textContent).toBe('আপনার চাকরির চুক্তি, আইনের চোখে')
  })

  it('NFR-09 sets a danda inside the heading in Hind Siliguri', () => {
    const { container } = render(
      <h2>
        <DisplayText text="যা জানি, বলি। যা জানি না, তাও বলি।" />
      </h2>,
    )
    expect(container.textContent).toBe('যা জানি, বলি। যা জানি না, তাও বলি')
    const dandas = container.querySelectorAll('span.font-body')
    expect(dandas).toHaveLength(1)
    expect(dandas[0]?.textContent).toBe('।')
  })

  it('NFR-09 leaves text without a danda, and English full stops, as they are', () => {
    expect(render(<DisplayText text="Made-up sections stop here." />).container.textContent).toBe(
      'Made-up sections stop here.',
    )
    expect(render(<DisplayText text="আপনার চুক্তি দিন" />).container.textContent).toBe(
      'আপনার চুক্তি দিন',
    )
  })
})
