/*
 * NOT PRODUCT TEXT. Prototype placeholder content for the Lovable design reference only (R-10):
 * the section numbers, titles and quotes below were not taken from the statute corpus and must
 * never be copied into the product, the specification or a demo. See ../README.md.
 */
export const verificationStatuses = ["verified", "uncertain", "rejected"] as const;

export type VerificationStatus = (typeof verificationStatuses)[number];
export type FindingSeverity = "critical" | "high" | "medium" | "low";

export interface LawCitation {
  readonly section: string;
  readonly titleKey: string;
  readonly href: string;
}

export interface ReviewFinding {
  readonly id: string;
  readonly titleKey: string;
  readonly excerptKey: string;
  readonly explanationKey: string;
  readonly verification: VerificationStatus;
  readonly severity: FindingSeverity;
  readonly citations: readonly LawCitation[];
}

export const reviewFindings = [
  {
    id: "overtime-rate",
    titleKey: "finding.overtimeTitle",
    excerptKey: "finding.overtimeExcerpt",
    explanationKey: "finding.overtimeExplanation",
    verification: "rejected",
    severity: "critical",
    citations: [
      {
        section: "১০৮",
        titleKey: "citation.labourAct",
        href: "https://bdlaws.minlaw.gov.bd/act-952/section-29435.html",
      },
    ],
  },
  {
    id: "daily-hours",
    titleKey: "finding.dailyTitle",
    excerptKey: "finding.dailyExcerpt",
    explanationKey: "finding.dailyExplanation",
    verification: "verified",
    severity: "medium",
    citations: [
      {
        section: "১০০",
        titleKey: "citation.labourAct",
        href: "https://bdlaws.minlaw.gov.bd/act-952/section-29427.html",
      },
      {
        section: "১০৮",
        titleKey: "citation.labourAct",
        href: "https://bdlaws.minlaw.gov.bd/act-952/section-29435.html",
      },
    ],
  },
  {
    id: "weekly-rest",
    titleKey: "finding.weeklyRestTitle",
    excerptKey: "finding.weeklyRestExcerpt",
    explanationKey: "finding.weeklyRestExplanation",
    verification: "uncertain",
    severity: "low",
    citations: [],
  },
] satisfies readonly ReviewFinding[];
