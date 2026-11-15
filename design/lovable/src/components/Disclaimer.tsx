import { Info } from "lucide-react";
import { useTranslation } from "react-i18next";

export function Disclaimer() {
  const { t } = useTranslation();

  return (
    <aside
      role="note"
      className="flex min-h-11 items-start gap-2.5 rounded-lg border border-uncertain/20 bg-uncertain/5 px-4 py-3 text-sm text-muted-foreground"
    >
      <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-uncertain" />
      <p className="font-medium text-foreground">{t("common.disclaimer")}</p>
    </aside>
  );
}
