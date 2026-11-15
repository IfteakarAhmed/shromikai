import { ExternalLink, Scale } from "lucide-react";
import type { MouseEventHandler } from "react";
import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import type { LawCitation } from "@/mocks/contract-review";

interface CitationChipProps {
  citation: LawCitation;
  className?: string;
  onClick?: MouseEventHandler<HTMLButtonElement>;
}

export function CitationChip({ citation, className, onClick }: CitationChipProps) {
  const { t } = useTranslation();
  const contents = (
    <>
      <Scale aria-hidden="true" className="size-4 shrink-0 text-verified" />
      <span className="min-w-0 truncate">{t(citation.titleKey)}</span>
      <span className="shrink-0 rounded bg-secondary px-1.5 py-0.5 text-xs text-secondary-foreground">
        {t("citation.section", { number: citation.section })}
      </span>
      <ExternalLink aria-hidden="true" className="size-3.5 shrink-0 text-muted-foreground" />
    </>
  );
  const sharedClassName = cn(
    "inline-flex min-h-11 max-w-full items-center gap-2 rounded-md border border-border bg-background/60 px-3 py-2 text-left text-sm text-foreground transition-colors hover:border-verified/50 hover:bg-verified/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
    className,
  );

  if (onClick) {
    return (
      <button
        type="button"
        aria-label={`${t(citation.titleKey)} · ${t("citation.section", { number: citation.section })}`}
        className={sharedClassName}
        onClick={onClick}
      >
        {contents}
      </button>
    );
  }

  return (
    <a
      href={citation.href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`${t(citation.titleKey)} · ${t("citation.section", { number: citation.section })} · ${t("citation.officialSource")} · ${t("citation.opensInNewTab")}`}
      className={sharedClassName}
    >
      {contents}
    </a>
  );
}
