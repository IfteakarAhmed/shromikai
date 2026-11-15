import { Gauge } from "lucide-react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/Button";

interface LowBandwidthToggleProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}

export function LowBandwidthToggle({ checked, onCheckedChange }: LowBandwidthToggleProps) {
  const { t } = useTranslation();

  return (
    <Button
      type="button"
      variant="outline"
      role="switch"
      aria-checked={checked}
      onClick={() => onCheckedChange(!checked)}
      className="h-11 min-w-11 gap-2 rounded-lg border-border bg-background/70 px-3 text-xs text-muted-foreground sm:text-sm"
    >
      <Gauge aria-hidden="true" className="size-4 shrink-0" />
      <span>{t("header.lowBandwidth")}</span>
      <span className="font-semibold text-foreground" aria-hidden="true">
        {t(checked ? "header.lowBandwidthOn" : "header.lowBandwidthOff")}
      </span>
    </Button>
  );
}
