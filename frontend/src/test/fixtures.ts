import type {
  AnalysisResult,
  ObligationItem,
  Redline,
  RiskFinding,
  VerifiedCitation,
} from '../api/analyses'

/**
 * Synthetic API objects for tests: a fictional act and invented contract facts (R-10, R-02). They
 * have the shape of the generated types, so a change in the API breaks the tests at compile time.
 */

export const QUOTE =
  'The employer shall give the worker written notice of fourteen days before dismissal.'

export function makeCitation(overrides: Partial<VerifiedCitation> = {}): VerifiedCitation {
  return {
    citationId: '22222222-2222-4222-8222-222222222222',
    sectionKey: 'XX-ACT-1:5@original',
    actCode: 'XX-ACT-1',
    actTitleBn: 'কাল্পনিক আইন (শুধু পরীক্ষার জন্য)',
    actTitleEn: 'Fictional Act (test only)',
    sectionNo: '5',
    headingBn: 'নোটিশ',
    headingEn: 'Notice',
    quote: QUOTE,
    quoteLanguage: 'en',
    translation: null,
    sourceUrl: 'https://example.invalid/xx/5',
    versionLabel: 'original',
    effectiveFrom: null,
    verification: {
      citationId: '22222222-2222-4222-8222-222222222222',
      sectionKey: 'XX-ACT-1:5@original',
      status: 'VERIFIED',
      attempt: 1,
      checks: {
        existence: { outcome: 'PASS' },
        quoteMatch: { outcome: 'PASS', similarity: 1, matchedLanguage: 'en' },
        support: {
          outcome: 'PASS',
          label: 'SUPPORTS',
          confidence: 0.92,
          independence: 'SAME_FAMILY',
          modelId: 'recorded-verifier',
          reason: 'The section requires written notice.',
        },
      },
      reasonCodes: [],
      verifiedAt: '2026-11-16T10:00:00.000Z',
      corpusRelease: 'xx-2026.11',
    },
    ...overrides,
  }
}

export function makeFinding(overrides: Partial<RiskFinding> = {}): RiskFinding {
  return {
    id: 'f1',
    title: 'Dismissal without notice',
    kind: 'CONFLICT',
    severity: 'HIGH',
    nonObvious: false,
    source: 'LLM_ANALYSIS',
    clauseType: 'NOTICE_AND_RESIGNATION',
    clauseIds: ['c3'],
    explanation: 'The contract lets the employer end it at once, without any notice to the worker.',
    whyItMatters: 'Without notice you may lose pay and time to find other work.',
    sideNote: null,
    verificationStatus: 'VERIFIED',
    citations: [makeCitation()],
    iloReferences: [],
    ruleId: null,
    playbookRuleId: null,
    redlineId: 'r1',
    reviewItemId: null,
    confidence: 0.8,
    ...overrides,
  }
}

export function makeUncertainFinding(overrides: Partial<RiskFinding> = {}): RiskFinding {
  return makeFinding({
    id: 'f2',
    title: 'Resignation needs permission',
    severity: 'MEDIUM',
    verificationStatus: 'UNCERTAIN',
    citations: [],
    redlineId: null,
    reviewItemId: '33333333-3333-4333-8333-333333333333',
    ...overrides,
  })
}

export function makeRedline(overrides: Partial<Redline> = {}): Redline {
  return {
    id: 'r1',
    findingIds: ['f1'],
    clauseId: 'c3',
    action: 'REPLACE',
    originalText: 'The company may end this contract at any time without notice.',
    proposedText: 'The company may end this contract after fourteen days written notice.',
    diff: [
      { op: 'EQUAL', text: 'The company may end this contract ' },
      { op: 'DELETE', text: 'at any time without notice' },
      { op: 'INSERT', text: 'after fourteen days written notice' },
      { op: 'EQUAL', text: '.' },
    ],
    rationale: 'Written notice is fair to both sides.',
    citations: [makeCitation()],
    ruleCheck: { passed: true, ruleIds: [], attempts: 1 },
    language: 'en',
    ...overrides,
  }
}

export function makeObligation(overrides: Partial<ObligationItem> = {}): ObligationItem {
  return {
    id: 'o1',
    party: 'EMPLOYER',
    obligation: 'Pay the monthly salary stated in the contract',
    trigger: 'Each month',
    deadline: null,
    source: 'CONTRACT',
    status: 'STATED_IN_CONTRACT',
    clauseIds: [],
    citations: [],
    ...overrides,
  }
}

export function makeResult(overrides: Partial<AnalysisResult> = {}): AnalysisResult {
  const findings = [makeFinding(), makeUncertainFinding()]
  return {
    analysisId: '44444444-4444-4444-8444-444444444444',
    status: 'SUCCEEDED',
    createdAt: '2026-11-16T10:00:00.000Z',
    completedAt: '2026-11-16T10:00:05.000Z',
    expiresAt: '2026-11-16T10:30:05.000Z',
    side: 'WORKER',
    outputLanguage: 'en',
    profile: null,
    input: {
      kind: 'TEXT',
      pageCount: 1,
      detectedLanguage: 'en',
      ocrMeanConfidence: null,
      legacyFontConverted: false,
      sample: true,
    },
    documentType: 'EMPLOYMENT_CONTRACT',
    clauses: [],
    findings,
    redlines: [makeRedline()],
    obligations: [
      makeObligation(),
      makeObligation({ id: 'o2', party: 'WORKER', obligation: 'Work the agreed hours' }),
    ],
    memo: {
      id: '55555555-5555-4555-8555-555555555555',
      analysisId: '44444444-4444-4444-8444-444444444444',
      language: 'en',
      readingLevel: 'STANDARD',
      title: 'Contract review memo',
      generatedAt: '2026-11-16T10:00:05.000Z',
      sections: [
        {
          kind: 'SUMMARY',
          heading: 'Summary',
          blocks: [{ type: 'paragraph', text: 'Two risks were found.' }],
        },
        {
          kind: 'RISK_TABLE',
          heading: 'Risks',
          blocks: [{ type: 'riskTable', findingIds: ['f1', 'f2'] }],
        },
        {
          kind: 'RISK_DETAILS',
          heading: 'Details',
          blocks: [{ type: 'findingRef', findingId: 'f1' }],
        },
        {
          kind: 'REVISED_CLAUSES',
          heading: 'Revised clauses',
          blocks: [{ type: 'redlineRef', redlineId: 'r1' }],
        },
        {
          kind: 'OBLIGATIONS',
          heading: 'Obligations',
          blocks: [{ type: 'obligationTable', obligationIds: ['o1', 'o2'] }],
        },
        {
          kind: 'NEXT_STEPS',
          heading: 'Next steps',
          blocks: [{ type: 'list', items: ['Check each citation.'] }],
        },
        {
          kind: 'VERIFICATION_AND_METHOD',
          heading: 'Method',
          blocks: [{ type: 'paragraph', text: 'Checked against the corpus.' }],
        },
        {
          kind: 'DISCLAIMER',
          heading: 'Disclaimer',
          blocks: [{ type: 'paragraph', text: 'This is not legal advice.' }],
        },
      ],
      verificationSummary: {
        verifiedCitations: 1,
        uncertainFindings: 1,
        policyFindings: 0,
        independence: 'SAME_FAMILY',
      },
      disclaimer: 'This is not legal advice. Talk to a lawyer before an important decision.',
      footer: {
        analysisId: '44444444-4444-4444-8444-444444444444',
        corpusRelease: 'xx-2026.11',
        pipelineVersion: 'p6-1',
        keyId: 'k1',
      },
    },
    summary: {
      overallSeverity: 'HIGH',
      findingsBySeverity: { CRITICAL: 0, HIGH: 1, MEDIUM: 1, LOW: 0 },
      nonObviousFindings: 0,
      verifiedFindings: 1,
      uncertainFindings: 1,
      policyFindings: 0,
      verifiedCitations: 1,
      coverageNote: null,
    },
    reviewTicket: {
      ticketId: '66666666-6666-4666-8666-666666666666',
      token: 'ticket-token-0123456789abcdef0123456789',
      expiresAt: '2026-12-16T10:00:05.000Z',
    },
    warnings: [],
    provenance: {
      pipelineVersion: 'p6-1',
      corpusRelease: 'xx-2026.11',
      promptVersions: { 'risk-analysis': 2 },
      models: { 'risk-analysis': 'recorded-model' },
      verifierIndependence: 'SAME_FAMILY',
      recorded: true,
    },
    costUsd: 0,
    disclaimer: 'This is not legal advice. Talk to a lawyer before an important decision.',
    envelope: {
      keyId: 'k1',
      algorithm: 'HMAC-SHA256',
      expiresAt: '2026-11-17T10:00:05.000Z',
      signature: 'A'.repeat(43),
    },
    ...overrides,
  }
}

export function makeCompare(
  overrides: Partial<import('../api/analyses').CompareResult> = {},
): import('../api/analyses').CompareResult {
  const claim = (
    sectionKey: string,
    status: 'VERIFIED' | 'UNCERTAIN' | 'REJECTED',
    reasonCodes: (
      'SECTION_NOT_FOUND' | 'QUOTE_NOT_FOUND' | 'SUPPORT_PARTIAL' | 'SECTION_NOT_CURRENT'
    )[],
  ) => ({
    claimed: { sectionKey, quote: `A quote claimed for ${sectionKey} here.`, claim: 'A claim.' },
    verification: { status, reasonCodes },
  })
  return {
    compareId: '44444444-4444-4444-8444-444444444444',
    baseline: {
      status: 'OK',
      reason: null,
      modelId: 'plain-model',
      findings: [
        {
          title: 'Dismissal needs notice',
          severity: 'HIGH',
          explanation: 'The plain model thinks the notice rule is broken.',
          citations: [
            claim('XX-ACT-1:999@original', 'REJECTED', ['SECTION_NOT_FOUND']),
            claim('XX-ACT-1:5@original', 'REJECTED', ['QUOTE_NOT_FOUND']),
            claim('XX-ACT-1:6@original', 'UNCERTAIN', ['SUPPORT_PARTIAL']),
            claim('XX-ACT-1:7@original', 'VERIFIED', []),
            claim('XX-ACT-1:8@original', 'REJECTED', ['SECTION_NOT_CURRENT']),
          ],
        },
      ],
    },
    shromikai: makeResult(),
    summary: {
      baselineCitations: 5,
      baselineRejected: 3,
      baselineUncertain: 1,
      baselineVerified: 1,
      shromikaiShownCitations: 1,
    },
    disclaimer: 'This is not legal advice. Talk to a lawyer before an important decision.',
    ...overrides,
  }
}
