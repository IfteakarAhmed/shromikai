import { useTranslation } from 'react-i18next'
import type { ReviewItem } from '../../api/admin'
import { Badge, type BadgeTone } from '../../components/ui/Badge'

const STATUS_TONE: Record<ReviewItem['status'], BadgeTone> = {
  OPEN: 'uncertain',
  IN_REVIEW: 'medium',
  APPROVED: 'verified',
  REJECTED: 'rejected',
  EXPIRED: 'neutral',
}

/** The status of a review item in words and colour. */
export function ReviewStatusBadge({ status }: { status: ReviewItem['status'] }) {
  const { t } = useTranslation()
  return <Badge tone={STATUS_TONE[status]}>{t(`admin.review.statuses.${status}`)}</Badge>
}
