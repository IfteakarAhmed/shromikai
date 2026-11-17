/*
 * NOT PRODUCT TEXT. Prototype placeholder content for the Lovable design reference only (R-10):
 * the section numbers, titles and quotes below were not taken from the statute corpus and must
 * never be copied into the product, the specification or a demo. See ../README.md.
 */
export const recordedLawSections = {
  section4: {
    section: "৪",
    titleKey: "mockReview.law.section4.title",
    textKey: "mockReview.law.section4.text",
    highlightKey: "mockReview.law.section4.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29072.html",
  },
  section5: {
    section: "৫",
    titleKey: "mockReview.law.section5.title",
    textKey: "mockReview.law.section5.text",
    highlightKey: "mockReview.law.section5.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29073.html",
  },
  section26: {
    section: "২৬",
    titleKey: "mockReview.law.section26.title",
    textKey: "mockReview.law.section26.text",
    highlightKey: "mockReview.law.section26.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29913.html",
  },
  section100: {
    section: "১০০",
    titleKey: "mockReview.law.section100.title",
    textKey: "mockReview.law.section100.text",
    highlightKey: "mockReview.law.section100.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29427.html",
  },
  section101: {
    section: "১০১",
    titleKey: "mockReview.law.section101.title",
    textKey: "mockReview.law.section101.text",
    highlightKey: "mockReview.law.section101.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29428.html",
  },
  section102: {
    section: "১০২",
    titleKey: "mockReview.law.section102.title",
    textKey: "mockReview.law.section102.text",
    highlightKey: "mockReview.law.section102.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29429.html",
  },
  section103: {
    section: "১০৩",
    titleKey: "mockReview.law.section103.title",
    textKey: "mockReview.law.section103.text",
    highlightKey: "mockReview.law.section103.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29430.html",
  },
  section108: {
    section: "১০৮",
    titleKey: "mockReview.law.section108.title",
    textKey: "mockReview.law.section108.text",
    highlightKey: "mockReview.law.section108.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29435.html",
  },
  section125: {
    section: "১২৫",
    titleKey: "mockReview.law.section125.title",
    textKey: "mockReview.law.section125.text",
    highlightKey: "mockReview.law.section125.highlight",
    href: "https://bdlaws.minlaw.gov.bd/act-952/section-29452.html",
  },
} as const;

export type RecordedLawSectionId = keyof typeof recordedLawSections;
export type ReviewSeverity = "critical" | "high" | "medium";

export interface RecordedFinding {
  readonly id: string;
  readonly titleKey: string;
  readonly clauseKey: string;
  readonly explanationKey: string;
  readonly severity: ReviewSeverity;
  readonly nonObvious: boolean;
  readonly sectionIds: readonly RecordedLawSectionId[];
  readonly modelKey: string;
}

export const recordedFindings = [
  {
    id: "weekly-hours",
    titleKey: "mockReview.finding.weeklyHours.title",
    clauseKey: "mockReview.finding.weeklyHours.clause",
    explanationKey: "mockReview.finding.weeklyHours.explanation",
    severity: "critical",
    nonObvious: true,
    sectionIds: ["section102"],
    modelKey: "mockReview.finding.weeklyHours.model",
  },
  {
    id: "unpaid-overtime",
    titleKey: "mockReview.finding.unpaidOvertime.title",
    clauseKey: "mockReview.finding.unpaidOvertime.clause",
    explanationKey: "mockReview.finding.unpaidOvertime.explanation",
    severity: "critical",
    nonObvious: true,
    sectionIds: ["section108"],
    modelKey: "mockReview.finding.unpaidOvertime.model",
  },
  {
    id: "daily-hours",
    titleKey: "mockReview.finding.dailyHours.title",
    clauseKey: "mockReview.finding.dailyHours.clause",
    explanationKey: "mockReview.finding.dailyHours.explanation",
    severity: "high",
    nonObvious: true,
    sectionIds: ["section100"],
    modelKey: "mockReview.finding.dailyHours.model",
  },
  {
    id: "uniform-deduction",
    titleKey: "mockReview.finding.uniformDeposit.title",
    clauseKey: "mockReview.finding.uniformDeposit.clause",
    explanationKey: "mockReview.finding.uniformDeposit.explanation",
    severity: "high",
    nonObvious: false,
    sectionIds: ["section125"],
    modelKey: "mockReview.finding.uniformDeposit.model",
  },
  {
    id: "probation-duration",
    titleKey: "mockReview.finding.probationDuration.title",
    clauseKey: "mockReview.finding.probationDuration.clause",
    explanationKey: "mockReview.finding.probationDuration.explanation",
    severity: "high",
    nonObvious: true,
    sectionIds: ["section4"],
    modelKey: "mockReview.finding.probationDuration.model",
  },
  {
    id: "termination-notice",
    titleKey: "mockReview.finding.terminationNotice.title",
    clauseKey: "mockReview.finding.terminationNotice.clause",
    explanationKey: "mockReview.finding.terminationNotice.explanation",
    severity: "high",
    nonObvious: true,
    sectionIds: ["section26"],
    modelKey: "mockReview.finding.terminationNotice.model",
  },
  {
    id: "missing-break",
    titleKey: "mockReview.finding.missingBreak.title",
    clauseKey: "mockReview.finding.missingBreak.clause",
    explanationKey: "mockReview.finding.missingBreak.explanation",
    severity: "medium",
    nonObvious: false,
    sectionIds: ["section101"],
    modelKey: "mockReview.finding.missingBreak.model",
  },
  {
    id: "no-weekly-rest",
    titleKey: "mockReview.finding.noWeeklyRest.title",
    clauseKey: "mockReview.finding.noWeeklyRest.clause",
    explanationKey: "mockReview.finding.noWeeklyRest.explanation",
    severity: "medium",
    nonObvious: true,
    sectionIds: ["section103"],
    modelKey: "mockReview.finding.noWeeklyRest.model",
  },
  {
    id: "missing-appointment-letter",
    titleKey: "mockReview.finding.missingAppointmentLetter.title",
    clauseKey: "mockReview.finding.missingAppointmentLetter.clause",
    explanationKey: "mockReview.finding.missingAppointmentLetter.explanation",
    severity: "medium",
    nonObvious: false,
    sectionIds: ["section5"],
    modelKey: "mockReview.finding.missingAppointmentLetter.model",
  },
  {
    id: "unconditional-uniform-replacement",
    titleKey: "mockReview.finding.unconditionalUniformReplacement.title",
    clauseKey: "mockReview.finding.unconditionalUniformReplacement.clause",
    explanationKey: "mockReview.finding.unconditionalUniformReplacement.explanation",
    severity: "medium",
    nonObvious: true,
    sectionIds: ["section125"],
    modelKey: "mockReview.finding.unconditionalUniformReplacement.model",
  },
] as const satisfies readonly RecordedFinding[];

export const uncertainFinding = {
  id: "probation-pay",
  titleKey: "mockReview.uncertain.title",
  clauseKey: "mockReview.uncertain.clause",
  explanationKey: "mockReview.uncertain.explanation",
  severity: "medium",
  nonObvious: false,
  sectionIds: [],
  modelKey: "mockReview.uncertain.model",
  verification: "uncertain",
} as const;

export const recordedSampleContract =
  "সিকিউরিটি গার্ড নিয়োগের শর্ত: কর্মী সপ্তাহে ছয় দিন, প্রতিদিন ১২ ঘণ্টা কাজ করবেন। ১২ ঘণ্টার শিফটে কোনো নির্ধারিত বিশ্রামের বিরতি থাকবে না। সপ্তাহের ছুটি কাজের প্রয়োজনে বাতিল করা যেতে পারে। অতিরিক্ত সময়ের জন্য আলাদা ভাতা দেওয়া হবে না। প্রতি মাসে ইউনিফর্মের নির্দিষ্ট মূল্য মজুরি থেকে কাটা হবে; ইউনিফর্ম হারালে, ক্ষতিগ্রস্ত হলে বা স্বাভাবিক ব্যবহারে বদলাতে হলে পুরো মূল্য কাটা হবে। প্রথম নয় মাস শিক্ষানবিশকাল; এই সময়ে মূল বেতনের ৭০% দেওয়া হবে। নিয়োগপত্র বা ছবিসহ পরিচয়পত্র দেওয়া হবে না। মালিক কোনো নোটিশ ছাড়াই চাকরি শেষ করতে পারবেন।";

export const clauseProposals = [
  {
    id: "overtime",
    titleKey: "mockReview.proposals.overtime.title",
    oldKey: "mockReview.proposals.overtime.old",
    newKey: "mockReview.proposals.overtime.new",
    sectionId: "section108",
  },
  {
    id: "uniform",
    titleKey: "mockReview.proposals.uniform.title",
    oldKey: "mockReview.proposals.uniform.old",
    newKey: "mockReview.proposals.uniform.new",
    sectionId: "section125",
  },
  {
    id: "termination",
    titleKey: "mockReview.proposals.termination.title",
    oldKey: "mockReview.proposals.termination.old",
    newKey: "mockReview.proposals.termination.new",
    sectionId: "section26",
  },
] as const satisfies readonly {
  readonly id: string;
  readonly titleKey: string;
  readonly oldKey: string;
  readonly newKey: string;
  readonly sectionId: RecordedLawSectionId;
}[];

export const workerDuties = [
  "mockReview.duties.workerItems.0",
  "mockReview.duties.workerItems.1",
  "mockReview.duties.workerItems.2",
  "mockReview.duties.workerItems.3",
] as const;

export const employerDuties = [
  "mockReview.duties.employerItems.0",
  "mockReview.duties.employerItems.1",
  "mockReview.duties.employerItems.2",
  "mockReview.duties.employerItems.3",
] as const;

const severityOrder: Record<ReviewSeverity, number> = { critical: 0, high: 1, medium: 2 };

export function sortRecordedFindings(findings: readonly RecordedFinding[]): RecordedFinding[] {
  return [...findings].sort(
    (first, second) => severityOrder[first.severity] - severityOrder[second.severity],
  );
}
