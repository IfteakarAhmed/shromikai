import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import i18n from '../../i18n'
import type { StepState } from '../../lib/stages'
import { ProgressView } from './ProgressView'

const STATES: StepState[] = ['done', 'active', 'waiting', 'waiting', 'waiting', 'waiting']

function progress(states: StepState[] = STATES) {
  return render(<ProgressView states={states} progress={30} warnings={[]} polling={false} />)
}

beforeEach(async () => {
  await i18n.changeLanguage('bn')
})

describe('ProgressView', () => {
  it('FR-69 the running step says in a plain sentence what is happening now', () => {
    const { container } = progress()
    const sentence = 'নাম, ফোন, ঠিকানা ও পরিচয়পত্রের নম্বর আমাদের নিজস্ব সার্ভারেই সরিয়ে ফেলছি'
    const running = container.querySelector('[data-stage="redacting"]') as HTMLElement
    expect(within(running).getByText(sentence)).toBeInTheDocument()
    // Screen readers hear it once, from the live region.
    expect(screen.getByRole('status')).toHaveTextContent(sentence)
    expect(screen.queryByText('আইনের প্রাসঙ্গিক ধারা খুঁজছি')).not.toBeInTheDocument()
  })

  it('FR-69 lights the six stages: done steps glow, the running one pulses, the rest wait', () => {
    const { container } = progress()
    const steps = Array.from(container.querySelectorAll('[data-stage]'))
    expect(steps).toHaveLength(6)
    expect(steps.map((step) => step.getAttribute('data-state'))).toEqual(STATES)
    expect(steps[1]).toHaveAttribute('aria-current', 'step')
  })

  it('R-07 shows only the percentage the API sent, no invented number', () => {
    const { container } = progress()
    const numbers = container.textContent.match(/[0-9০-৯]+/g) ?? []
    // The step numbers (১ to ৬) and the API's percentage (৩০) are the only numbers on screen.
    expect(numbers.filter((value) => !['১', '২', '৩', '৪', '৫', '৬'].includes(value))).toEqual([
      '৩০',
    ])
  })
})
