import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { makeCitation, makeFinding, makeUncertainFinding } from '../../test/fixtures'
import { groupCitations } from '../../lib/citations'
import { CitationChip } from './CitationChip'
import { RiskCard } from './RiskCard'
import { SeverityTag } from './SeverityTag'
import { VerificationBadge } from './VerificationBadge'
import { VerificationLegend } from './VerificationLegend'

beforeEach(async () => {
  await i18n.changeLanguage('bn')
})

describe('VerificationBadge', () => {
  it('FR-70 shows ✓ যাচাইকৃত for a verified finding and ? অনিশ্চিত for an uncertain one', () => {
    render(
      <>
        <VerificationBadge status="VERIFIED" />
        <VerificationBadge status="UNCERTAIN" />
      </>,
    )

    expect(screen.getByText('✓ যাচাইকৃত')).toBeInTheDocument()
    expect(screen.getByText('? অনিশ্চিত')).toBeInTheDocument()
  })

  it('FR-70 labels a playbook finding as policy, never as law', () => {
    render(<VerificationBadge status="POLICY_ONLY" />)

    expect(screen.getByText('নীতি (আইন নয়)')).toBeInTheDocument()
    expect(screen.queryByText(/যাচাইকৃত/)).not.toBeInTheDocument()
  })

  it('FR-70 the legend explains exactly two states and has no weak or partial one', () => {
    render(<VerificationLegend />)

    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0] as HTMLElement).getByText('✓ যাচাইকৃত')).toBeInTheDocument()
    expect(within(items[1] as HTMLElement).getByText('? অনিশ্চিত')).toBeInTheDocument()
    expect(screen.queryByText(/দুর্বল|আংশিক|weak|partial/i)).not.toBeInTheDocument()
  })
})

describe('SeverityTag', () => {
  it('FR-70 names the severity in words, so colour is never the only signal', () => {
    render(<SeverityTag severity="CRITICAL" />)

    expect(screen.getByText('গুরুতর')).toBeInTheDocument()
  })
})

describe('CitationChip', () => {
  it('FR-70 names the act and the section and opens the citation on click', async () => {
    const onOpen = vi.fn()
    const citation = makeCitation()
    render(
      <CitationChip group={groupCitations([citation])[0] as never} language="bn" onOpen={onOpen} />,
    )

    const chip = screen.getByRole('button', { name: /কাল্পনিক আইন.*ধারা ৫ খুলুন/ })
    expect(chip).toHaveTextContent('ধারা ৫')
    await userEvent.click(chip)

    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ primary: citation }))
  })

  it('FR-70 uses the English act title and Latin digits in English', async () => {
    await i18n.changeLanguage('en')
    render(
      <CitationChip
        group={groupCitations([makeCitation()])[0] as never}
        language="en"
        onOpen={() => undefined}
      />,
    )

    expect(
      screen.getByRole('button', { name: /Fictional Act.*Section 5 Open/i }),
    ).toBeInTheDocument()
  })
})

describe('RiskCard', () => {
  const noop = () => undefined

  it('FR-70 FR-36 says where a finding comes from: a fixed legal limit, an organisation policy, or nothing for the model', () => {
    const labels = new Map([
      ['RULE_ENGINE', 'নির্দিষ্ট আইনি সীমা'],
      ['PLAYBOOK', 'প্রতিষ্ঠানের নীতি'],
    ])
    for (const source of ['RULE_ENGINE', 'PLAYBOOK', 'LLM_ANALYSIS'] as const) {
      const { unmount } = render(
        <RiskCard
          finding={makeFinding({ source })}
          language="en"
          onOpenCitation={noop}
          onOpenWhy={noop}
        />,
      )
      for (const [other, label] of labels) {
        expect(screen.queryByText(label) !== null, `${source} shows ${other}`).toBe(
          source === other,
        )
      }
      unmount()
    }
  })

  it('FR-70 a verified card shows its severity, reason and one chip per citation', () => {
    const finding = makeFinding({
      nonObvious: true,
      citations: [
        makeCitation(),
        makeCitation({ sectionKey: 'XX-ACT-1:6@original', sectionNo: '6' }),
      ],
    })
    render(
      <RiskCard
        finding={finding}
        language="en"
        onOpenCitation={noop}
        onOpenWhy={noop}
        onOpenProposal={noop}
      />,
    )

    expect(screen.getByText('Dismissal without notice')).toBeInTheDocument()
    expect(screen.getByText('✓ যাচাইকৃত')).toBeInTheDocument()
    expect(screen.getByText('বড় ঝুঁকি')).toBeInTheDocument()
    expect(screen.getByText('সহজে চোখে পড়ে না এমন ঝুঁকি')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /ধারা \S+ খুলুন/ })).toHaveLength(2)
    expect(screen.getByText(/without any notice/)).toBeInTheDocument()
  })

  it('FR-70 R-01 an uncertain card never renders a citation and says it went to human review', () => {
    // Even a malformed finding that carries a citation must not show it while UNCERTAIN.
    const finding = makeUncertainFinding({ citations: [makeCitation()] })
    render(<RiskCard finding={finding} language="en" onOpenCitation={noop} onOpenWhy={noop} />)

    expect(screen.getByText('? অনিশ্চিত')).toBeInTheDocument()
    expect(screen.getByText('মানুষের পর্যালোচনার জন্য পাঠানো হয়েছে')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /ধারা \S+ খুলুন/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByText(/XX-ACT-1/)).not.toBeInTheDocument()
  })

  it('FR-70 the why button opens the trace of that finding', async () => {
    const onOpenWhy = vi.fn()
    render(
      <RiskCard
        finding={makeFinding()}
        language="en"
        onOpenCitation={noop}
        onOpenWhy={onOpenWhy}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: 'কেন?' }))

    expect(onOpenWhy).toHaveBeenCalledOnce()
  })

  it('FR-70 marks the language of the content so a screen reader pronounces it right', () => {
    const { container } = render(
      <RiskCard finding={makeFinding()} language="bn" onOpenCitation={noop} onOpenWhy={noop} />,
    )

    expect(container.querySelector('[lang="bn"]')).not.toBeNull()
  })

  it('FR-70 two passages of one section are one chip, not two identical ones', () => {
    const finding = makeFinding({
      citations: [
        makeCitation({ citationId: 'a', quote: 'First passage of the section here.' }),
        makeCitation({ citationId: 'b', quote: 'Second passage of the same section.' }),
      ],
    })
    render(<RiskCard finding={finding} language="en" onOpenCitation={noop} onOpenWhy={noop} />)

    expect(screen.getAllByRole('button', { name: /ধারা \S+ খুলুন/ })).toHaveLength(1)
  })

  it('FR-70 two versions of one section are two chips and each says its version', () => {
    const finding = makeFinding({
      citations: [
        makeCitation({ sectionKey: 'XX-ACT-1:5@original', versionLabel: 'original' }),
        makeCitation({ sectionKey: 'XX-ACT-1:5@amend-1', versionLabel: 'amend-1' }),
      ],
    })
    render(<RiskCard finding={finding} language="en" onOpenCitation={noop} onOpenWhy={noop} />)

    const chips = screen.getAllByRole('button', { name: /ধারা \S+ .*খুলুন/ })
    expect(chips).toHaveLength(2)
    expect(chips[0]).toHaveTextContent('সংস্করণ original')
    expect(chips[1]).toHaveTextContent('সংস্করণ amend-1')
  })

  it('FR-70 a single version shows no version label', () => {
    render(
      <RiskCard finding={makeFinding()} language="en" onOpenCitation={noop} onOpenWhy={noop} />,
    )

    expect(screen.queryByText(/সংস্করণ/)).not.toBeInTheDocument()
  })
})

/** True when the element sits inside a closed <details> (hidden until opened). */
const folded = (element: Element) => element.closest('details:not([open])') !== null

describe('RiskCard compact', () => {
  const handlers = {
    language: 'bn' as const,
    onOpenCitation: () => undefined,
    onOpenWhy: () => undefined,
  }

  it('FR-70 shows the title, the first sentence, the marks and the chips; the rest is under বিস্তারিত', () => {
    const finding = makeFinding({
      explanation: 'The contract ends at once. It gives no notice at all.',
      citations: [makeCitation()],
    })
    render(<RiskCard finding={finding} {...handlers} />)
    expect(folded(screen.getByText('Dismissal without notice'))).toBe(false)
    expect(folded(screen.getByText('The contract ends at once.'))).toBe(false)
    expect(folded(screen.getByText('It gives no notice at all.'))).toBe(true)
    expect(folded(screen.getByText('✓ যাচাইকৃত'))).toBe(false)
    expect(folded(screen.getByText('বড় ঝুঁকি'))).toBe(false)
    expect(
      folded(screen.getAllByRole('button', { name: /ধারা \S+ খুলুন/ })[0] as HTMLElement),
    ).toBe(false)
    expect(folded(screen.getByText(/চুক্তির শর্ত/))).toBe(true)
    expect(folded(screen.getByRole('button', { name: 'কেন?' }))).toBe(true)
    expect(screen.getByText('বিস্তারিত').tagName).toBe('SUMMARY')
  })

  it('FR-36 keeps the fixed-legal-limit label on the compact card', () => {
    render(<RiskCard finding={makeFinding({ source: 'RULE_ENGINE' })} {...handlers} />)
    expect(folded(screen.getByText('নির্দিষ্ট আইনি সীমা'))).toBe(false)
  })

  it('R-01 an uncertain card keeps অনিশ্চিত and the review message outside বিস্তারিত', () => {
    render(<RiskCard finding={makeUncertainFinding()} {...handlers} />)
    expect(folded(screen.getByText('? অনিশ্চিত'))).toBe(false)
    expect(folded(screen.getByText('মানুষের পর্যালোচনার জন্য পাঠানো হয়েছে'))).toBe(false)
  })
})
