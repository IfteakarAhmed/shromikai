import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '../ui/Badge'

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

const TONE: Record<Severity, BadgeTone> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

/** Severity in words and colour; the word carries the meaning. */
export function SeverityTag({ severity }: { severity: Severity }) {
  const { t } = useTranslation()
  return <Badge tone={TONE[severity]}>{t(`severity.${severity.toLowerCase()}`)}</Badge>
}
