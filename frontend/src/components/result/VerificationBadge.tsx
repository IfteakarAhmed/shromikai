import { useTranslation } from 'react-i18next'
import { Badge } from '../ui/Badge'

/**
 * The verification state of a finding (R-01). There are only two states about the law, verified and
 * uncertain; a playbook finding is labelled policy. There is deliberately no weak or partial state,
 * and `REJECTED` is not accepted here: it exists only for the baseline in compare mode.
 */
export type FindingVerification = 'VERIFIED' | 'UNCERTAIN' | 'POLICY_ONLY'

export function VerificationBadge({ status }: { status: FindingVerification }) {
  const { t } = useTranslation()
  switch (status) {
    case 'VERIFIED':
      return <Badge tone="verified">{t('verification.verified')}</Badge>
    case 'UNCERTAIN':
      return <Badge tone="uncertain">{t('verification.uncertain')}</Badge>
    case 'POLICY_ONLY':
      return <Badge tone="neutral">{t('verification.policy')}</Badge>
  }
}
