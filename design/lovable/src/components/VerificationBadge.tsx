import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import type { VerificationStatus } from "@/mocks/contract-review";

interface VerificationBadgeProps {
  status: VerificationStatus;
}

export function VerificationBadge({ status }: VerificationBadgeProps) {
  const { t } = useTranslation();

  return <Badge tone={status}>{t(`verification.${status}`)}</Badge>;
}
