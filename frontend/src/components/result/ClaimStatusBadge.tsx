import { useTranslation } from 'react-i18next'
import { Badge } from '../ui/Badge'

/**
 * The state of a baseline claim in compare mode (ADR-0022). A claim is never a citation: this badge
 * is used only for the plain model's column, and a rejected claim is marked with a cross.
 */
export function ClaimStatusBadge({ status }: { status: 'VERIFIED' | 'UNCERTAIN' | 'REJECTED' }) {
  const { t } = useTranslation()
  switch (status) {
    case 'REJECTED':
      return <Badge tone="rejected">{t('compare.claimRejected')}</Badge>
    case 'UNCERTAIN':
      return <Badge tone="uncertain">{t('compare.claimUncertain')}</Badge>
    case 'VERIFIED':
      return <Badge tone="neutral">{t('compare.claimVerified')}</Badge>
  }
}
