import { Check, FileSearch, X } from "lucide-react";
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
import {
  recordedLawSections,
  uncertainFinding,
  type RecordedFinding,
  type RecordedLawSectionId,
} from "@/mocks/security-guard-review";

type WhyFinding = RecordedFinding | typeof uncertainFinding;

interface FindingWhySheetProps {
  finding: WhyFinding | null;
  onOpenCitation: (sectionId: RecordedLawSectionId) => void;
  onOpenChange: (open: boolean) => void;
}

export function FindingWhySheet({ finding, onOpenCitation, onOpenChange }: FindingWhySheetProps) {
  const { t } = useTranslation();
  const isUncertain = finding?.id === uncertainFinding.id;
  const checkedItems = [
    t("mockReview.why.lawExists"),
    t("mockReview.why.quoteMatches"),
    t("mockReview.why.supports"),
  ];

  return (
    <Sheet open={finding !== null} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="inset-y-0 right-0 h-full w-full overflow-y-auto border-l border-border bg-background p-0 sm:max-w-xl"
      >
        {finding ? (
          <div className="p-5 pb-8 sm:p-8">
            <SheetHeader className="pr-10 text-left">
              <Badge tone={isUncertain ? "uncertain" : "verified"} className="w-fit">
                <FileSearch aria-hidden="true" className="size-3.5" />
                {t("mockReview.why.title")}
              </Badge>
              <SheetTitle className="pt-3 font-display text-2xl leading-8">
                {t(finding.titleKey)}
              </SheetTitle>
              <SheetDescription>{t("mockReview.why.description")}</SheetDescription>
            </SheetHeader>

            <section className="mt-7">
              <h3 className="text-sm font-semibold text-foreground">
                {t("mockReview.why.retrieved")}
              </h3>
              {finding.sectionIds.length ? (
                <ul className="mt-3 divide-y divide-border border-y border-border">
                  {finding.sectionIds.map((sectionId: RecordedLawSectionId) => {
                    const section = recordedLawSections[sectionId];
                    return (
                      <li key={sectionId}>
                        <Button
                          type="button"
                          variant="ghost"
                          onClick={() => onOpenCitation(sectionId)}
                          className="h-auto min-h-14 w-full justify-between whitespace-normal px-2 py-3 text-left"
                        >
                          <span className="min-w-0">
                            <span className="block text-xs text-muted-foreground">
                              {t("mockReview.citation.section", { number: section.section })}
                            </span>
                            <span className="mt-1 block text-sm font-medium text-foreground">
                              {t(section.titleKey)}
                            </span>
                          </span>
                          <span aria-hidden="true" className="text-verified">
                            ↗
                          </span>
                        </Button>
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <p className="mt-3 rounded-md border border-uncertain/20 bg-uncertain/5 p-4 text-sm leading-7 text-uncertain">
                  {t("mockReview.report.noCitation")}
                </p>
              )}
            </section>

            <section className="mt-7">
              <h3 className="text-sm font-semibold text-foreground">
                {t("mockReview.why.checks")}
              </h3>
              <ul className="mt-3 space-y-2">
                {checkedItems.map((item) => (
                  <li
                    key={item}
                    className={`flex min-h-11 items-center gap-3 rounded-md border px-3 text-sm ${isUncertain ? "border-border text-muted-foreground" : "border-verified/20 bg-verified/5 text-foreground"}`}
                  >
                    {isUncertain ? (
                      <X aria-hidden="true" className="size-4 text-uncertain" />
                    ) : (
                      <Check aria-hidden="true" className="size-4 text-verified" />
                    )}
                    {item}
                  </li>
                ))}
              </ul>
            </section>

            <section className="mt-7 border-t border-border pt-5">
              <h3 className="text-xs font-medium text-muted-foreground">
                {t("mockReview.why.model")}
              </h3>
              <p className="mt-2 font-medium text-foreground">{t(finding.modelKey)}</p>
              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                {t("mockReview.why.recorded")}
              </p>
            </section>
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
