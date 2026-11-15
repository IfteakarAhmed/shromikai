import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import type { FindingSeverity } from "@/mocks/contract-review";

interface SeverityTagProps {
  severity: FindingSeverity;
  showCode?: boolean;
}

const severityTone = {
  critical: "rejected",
  high: "rejected",
  medium: "uncertain",
  low: "neutral",
} as const;

export function SeverityTag({ severity, showCode = false }: SeverityTagProps) {
  const { t } = useTranslation();

  return (
    <Badge tone={severityTone[severity]}>
      {showCode ? <span className="font-sans uppercase">{severity}</span> : null}
      <span>{t(`severity.${severity}`)}</span>
    </Badge>
  );
}
