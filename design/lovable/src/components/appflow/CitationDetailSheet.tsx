import { ArrowUpRight, Scale } from "lucide-react";
import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { recordedLawSections, type RecordedLawSectionId } from "@/mocks/security-guard-review";

interface CitationDetailSheetProps {
  sectionId: RecordedLawSectionId | null;
  onOpenChange: (open: boolean) => void;
}

export function CitationDetailSheet({ sectionId, onOpenChange }: CitationDetailSheetProps) {
  const { t } = useTranslation();
  const section = sectionId ? recordedLawSections[sectionId] : null;

  return (
    <Sheet open={section !== null} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="inset-y-0 right-0 h-full w-full overflow-y-auto border-l border-border bg-background p-0 sm:max-w-xl"
      >
        {section ? (
          <div className="p-5 pb-8 sm:p-8">
            <SheetHeader className="pr-10 text-left">
              <Badge tone="verified" className="w-fit">
                <Scale aria-hidden="true" className="size-3.5" />
                {t("mockReview.citation.section", { number: section.section })}
              </Badge>
              <SheetTitle className="pt-3 font-display text-2xl leading-8">
                {t(section.titleKey)}
              </SheetTitle>
              <SheetDescription>{t("mockReview.citation.title")}</SheetDescription>
            </SheetHeader>

            <div className="mt-7 space-y-4">
              <div className="border-y border-border py-4">
                <p className="text-xs font-medium text-muted-foreground">
                  {t("mockReview.citation.act")}
                </p>
                <p className="mt-1 font-medium text-foreground">{t("mockReview.law.act")}</p>
              </div>
              <div>
                <p className="text-xs font-medium text-muted-foreground">
                  {t("mockReview.citation.version")}
                </p>
                <p className="mt-1 text-sm leading-6 text-foreground">
                  {t("mockReview.law.version")}
                </p>
              </div>
              <div>
                <p className="text-xs font-medium text-muted-foreground">
                  {t("mockReview.citation.quotedText")}
                </p>
                <p className="mt-2 text-xs leading-5 text-muted-foreground">
                  {t("mockReview.citation.languageNote")}
                </p>
                <blockquote className="mt-2 rounded-md border border-border bg-card/70 p-4 font-display text-base leading-8 text-foreground">
                  {renderHighlightedQuote(t(section.textKey), t(section.highlightKey))}
                </blockquote>
              </div>
              <Button asChild className="w-full sm:w-auto">
                <a href={section.href} target="_blank" rel="noopener noreferrer">
                  {t("mockReview.law.sourceLink")}
                  <ArrowUpRight aria-hidden="true" />
                  <span className="sr-only">{t("mockReview.law.openInNewTab")}</span>
                </a>
              </Button>
            </div>
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function renderHighlightedQuote(quote: string, highlight: string) {
  const index = quote.indexOf(highlight);
  if (index < 0 || highlight.length === 0) return quote;

  return (
    <>
      {quote.slice(0, index)}
      <mark className="rounded-sm bg-verified/20 px-0.5 text-foreground ring-1 ring-verified/30">
        {highlight}
      </mark>
      {quote.slice(index + highlight.length)}
    </>
  );
}
