import {
  ArrowDownToLine,
  ArrowLeft,
  ArrowUpRight,
  Check,
  ClipboardCheck,
  FileText,
  Info,
  Printer,
  ShieldCheck,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { CitationChip } from "@/components/CitationChip";
import { Disclaimer } from "@/components/Disclaimer";
import { GlassCard } from "@/components/GlassCard";
import { SeverityTag } from "@/components/SeverityTag";
import { VerificationBadge } from "@/components/VerificationBadge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Checkbox } from "@/components/ui/checkbox";
import { CitationDetailSheet } from "@/components/appflow/CitationDetailSheet";
import { FindingWhySheet } from "@/components/appflow/FindingWhySheet";
import {
  clauseProposals,
  employerDuties,
  recordedFindings,
  recordedLawSections,
  sortRecordedFindings,
  uncertainFinding,
  workerDuties,
  type RecordedFinding,
  type RecordedLawSectionId,
} from "@/mocks/security-guard-review";
import type { ReviewSide } from "@/components/appflow/AppUploadScreen";

type ReviewTab = "risks" | "proposals" | "duties" | "memo";
type WhyFinding = RecordedFinding | typeof uncertainFinding;

interface ReviewResultsProps {
  side: ReviewSide;
  onBackToUpload: () => void;
}

const allFindings = sortRecordedFindings(recordedFindings);
const tabIds = ["risks", "proposals", "duties", "memo"] as const satisfies readonly ReviewTab[];

export function ReviewResults({ side, onBackToUpload }: ReviewResultsProps) {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<ReviewTab>("risks");
  const [selectedSectionId, setSelectedSectionId] = useState<RecordedLawSectionId | null>(null);
  const [whyFinding, setWhyFinding] = useState<WhyFinding | null>(null);

  const openCitation = useCallback((sectionId: RecordedLawSectionId) => {
    setWhyFinding(null);
    setSelectedSectionId(sectionId);
  }, []);

  useEffect(() => {
    const activePanel = document.querySelector<HTMLElement>(
      "[role='tabpanel'][data-state='active']",
    );
    if (!activePanel) return;
    let startX: number | null = null;
    let startY: number | null = null;
    const handleTouchStart = (event: TouchEvent) => {
      const touch = event.changedTouches.item(0);
      startX = touch?.clientX ?? null;
      startY = touch?.clientY ?? null;
    };
    const handleTouchEnd = (event: TouchEvent) => {
      const touch = event.changedTouches.item(0);
      if (startX === null || startY === null || !touch) return;
      const dx = touch.clientX - startX;
      const dy = touch.clientY - startY;
      if (Math.abs(dx) < 72 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
      setActiveTab((current) => {
        const index = tabIds.indexOf(current);
        return tabIds[(index + (dx < 0 ? 1 : -1) + tabIds.length) % tabIds.length] ?? "risks";
      });
      startX = null;
      startY = null;
    };
    activePanel.addEventListener("touchstart", handleTouchStart, { passive: true });
    activePanel.addEventListener("touchend", handleTouchEnd, { passive: true });
    return () => {
      activePanel.removeEventListener("touchstart", handleTouchStart);
      activePanel.removeEventListener("touchend", handleTouchEnd);
    };
  }, [activeTab]);

  const verifiedCounts = allFindings.reduce(
    (counts, finding) => ({ ...counts, [finding.severity]: counts[finding.severity] + 1 }),
    { critical: 0, high: 0, medium: 0 },
  );

  return (
    <div className="mx-auto w-full max-w-6xl pb-28 sm:pb-14">
      <header className="mb-7 flex flex-col gap-4 border-b border-border/70 pb-6 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <div className="mb-3 flex flex-wrap items-center gap-3">
            <Badge tone="neutral">{t("mockReview.memo.generated")}</Badge>
            <span className="text-xs text-muted-foreground">{t("mockReview.report.eyebrow")}</span>
          </div>
          <h1 className="font-display text-3xl font-semibold leading-[1.35] text-foreground sm:text-4xl">
            {t("mockReview.report.title")}
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-7 text-muted-foreground">
            {t("mockReview.report.summary")}
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={onBackToUpload}
          className="shrink-0 self-start sm:self-auto"
        >
          <ArrowLeft aria-hidden="true" />
          {t("mockReview.report.backToUpload")}
        </Button>
      </header>

      <GlassCard as="section" aria-label={t("review.summaryTitle")} className="mb-6 p-4 sm:p-5">
        <p className="text-sm leading-7 text-muted-foreground">
          {t("mockReview.report.reviewScope")}
        </p>
        <div className="mt-4 grid grid-cols-6 gap-px overflow-hidden rounded-md border border-border bg-border sm:grid-cols-5">
          <SummaryCell
            label={t("mockReview.report.criticalCount")}
            count={verifiedCounts.critical}
            tone="critical"
            className="col-span-2 sm:col-span-1"
          />
          <SummaryCell
            label={t("mockReview.report.highCount")}
            count={verifiedCounts.high}
            tone="high"
            className="col-span-2 sm:col-span-1"
          />
          <SummaryCell
            label={t("mockReview.report.mediumCount")}
            count={verifiedCounts.medium}
            tone="medium"
            className="col-span-2 sm:col-span-1"
          />
          <SummaryCell
            label={t("mockReview.report.verifiedCount")}
            count={allFindings.length}
            tone="verified"
            className="col-span-3 sm:col-span-1"
          />
          <SummaryCell
            label={t("mockReview.report.uncertainCount")}
            count={1}
            tone="uncertain"
            className="col-span-3 sm:col-span-1"
          />
        </div>
      </GlassCard>

      <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as ReviewTab)}>
        <TabsList
          aria-label={t("mockReview.tabs.risks")}
          className="sticky top-0 z-20 flex h-auto w-full justify-start gap-1 overflow-x-auto rounded-none border-y border-border bg-background/95 p-1 backdrop-blur-md print:hidden sm:static sm:justify-center sm:rounded-md sm:border"
        >
          {tabIds.map((tab) => (
            <TabsTrigger
              key={tab}
              value={tab}
              className="min-h-11 shrink-0 px-3 text-xs sm:px-4 sm:text-sm"
            >
              {t(`mockReview.tabs.${tab}`)}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="risks" className="mt-5 space-y-4">
          <div className="sr-only" aria-live="polite">
            {t("mockReview.report.title")}
          </div>
          {allFindings.map((finding) => (
            <FindingCard
              key={finding.id}
              finding={finding}
              onOpenCitation={openCitation}
              onOpenWhy={() => setWhyFinding(finding)}
            />
          ))}
          <UncertainFindingCard onOpenWhy={() => setWhyFinding(uncertainFinding)} />
        </TabsContent>

        <TabsContent value="proposals" className="mt-5 space-y-4">
          <section className="mb-5">
            <p className="text-xs font-semibold text-verified">
              {t("mockReview.proposals.eyebrow")}
            </p>
            <h2 className="mt-1 font-display text-2xl font-semibold text-foreground">
              {t("mockReview.proposals.title")}
            </h2>
            <p className="mt-2 text-sm leading-7 text-muted-foreground">
              {t("mockReview.proposals.description")}
            </p>
          </section>
          {clauseProposals.map((proposal) => (
            <ProposalCard key={proposal.id} proposal={proposal} onOpenCitation={openCitation} />
          ))}
        </TabsContent>

        <TabsContent value="duties" className="mt-5">
          <DutiesPanel />
        </TabsContent>

        <TabsContent value="memo" className="mt-5">
          <MemoPanel side={side} onOpenCitation={openCitation} />
        </TabsContent>
      </Tabs>

      <div className="mt-7 print:hidden">
        <Disclaimer />
      </div>
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/95 p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur-lg sm:hidden print:hidden">
        <div className="mx-auto flex max-w-6xl gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={onBackToUpload}
            className="min-w-0 flex-1 px-2 text-xs"
          >
            <ArrowLeft aria-hidden="true" />
            {t("mockReview.report.backToUpload")}
          </Button>
          {activeTab === "memo" ? (
            <Button
              type="button"
              onClick={() => window.print()}
              className="min-w-0 flex-1 px-2 text-xs"
            >
              <Printer aria-hidden="true" />
              {t("mockReview.memo.download")}
            </Button>
          ) : (
            <Button
              type="button"
              onClick={() =>
                setActiveTab(tabIds[(tabIds.indexOf(activeTab) + 1) % tabIds.length] ?? "risks")
              }
              className="min-w-0 flex-1 px-2 text-xs"
            >
              {t("mockReview.tabs." + tabIds[(tabIds.indexOf(activeTab) + 1) % tabIds.length])}
              <ArrowUpRight aria-hidden="true" />
            </Button>
          )}
        </div>
      </div>

      <CitationDetailSheet
        sectionId={selectedSectionId}
        onOpenChange={(open) => {
          if (!open) setSelectedSectionId(null);
        }}
      />
      <FindingWhySheet
        finding={whyFinding}
        onOpenCitation={openCitation}
        onOpenChange={(open) => {
          if (!open) setWhyFinding(null);
        }}
      />
    </div>
  );
}

function SummaryCell({
  label,
  count,
  tone,
  className,
}: {
  label: string;
  count: number;
  tone: "critical" | "high" | "medium" | "verified" | "uncertain";
  className: string;
}) {
  const { i18n } = useTranslation();
  const color =
    tone === "verified"
      ? "text-verified"
      : tone === "uncertain" || tone === "medium"
        ? "text-uncertain"
        : "text-rejected";
  return (
    <div className={`min-w-0 bg-background/90 px-3 py-3 sm:px-4 ${className}`}>
      <p className="font-display text-2xl font-semibold leading-none text-foreground">
        {new Intl.NumberFormat(i18n.resolvedLanguage ?? "bn-BD").format(count)}
      </p>
      <p className={`mt-2 text-xs leading-5 ${color}`}>{label}</p>
    </div>
  );
}

function FindingCard({
  finding,
  onOpenCitation,
  onOpenWhy,
}: {
  finding: RecordedFinding;
  onOpenCitation: (sectionId: RecordedLawSectionId) => void;
  onOpenWhy: () => void;
}) {
  const { t } = useTranslation();
  return (
    <GlassCard as="article" className="p-4 sm:p-6">
      <div className="flex flex-wrap items-center gap-2">
        <SeverityTag severity={finding.severity} showCode />
        {finding.nonObvious ? (
          <Badge tone="neutral">
            <Info aria-hidden="true" className="size-3.5" />
            {t("mockReview.report.nonObvious")}
          </Badge>
        ) : null}
        <span className="ml-auto">
          <VerificationBadge status="verified" />
        </span>
      </div>
      <h3 className="mt-4 font-display text-xl font-semibold leading-8 text-foreground">
        {t(finding.titleKey)}
      </h3>
      <blockquote className="mt-3 border-l-2 border-border pl-3 text-sm leading-7 text-muted-foreground">
        “{t(finding.clauseKey)}”
      </blockquote>
      <p className="mt-3 max-w-3xl text-sm leading-7 text-foreground">
        {t(finding.explanationKey)}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        {finding.sectionIds.map((sectionId) => {
          const section = recordedLawSections[sectionId];
          return (
            <CitationChip
              key={sectionId}
              citation={{
                section: section.section,
                titleKey: section.titleKey,
                href: section.href,
              }}
              onClick={() => onOpenCitation(sectionId)}
            />
          );
        })}
        <Button
          type="button"
          variant="ghost"
          onClick={onOpenWhy}
          className="ml-auto px-3 text-muted-foreground"
        >
          <Info aria-hidden="true" />
          {t("mockReview.report.why")}
        </Button>
      </div>
    </GlassCard>
  );
}

function UncertainFindingCard({ onOpenWhy }: { onOpenWhy: () => void }) {
  const { t } = useTranslation();
  return (
    <GlassCard as="article" className="border-uncertain/30 bg-uncertain/5 p-4 sm:p-6">
      <div className="flex flex-wrap items-center gap-2">
        <SeverityTag severity="medium" showCode />
        <VerificationBadge status="uncertain" />
      </div>
      <h3 className="mt-4 font-display text-xl font-semibold leading-8 text-foreground">
        {t(uncertainFinding.titleKey)}
      </h3>
      <blockquote className="mt-3 border-l-2 border-uncertain/50 pl-3 text-sm leading-7 text-muted-foreground">
        “{t(uncertainFinding.clauseKey)}”
      </blockquote>
      <p className="mt-3 max-w-3xl text-sm leading-7 text-foreground">
        {t(uncertainFinding.explanationKey)}
      </p>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm font-medium text-uncertain">{t("mockReview.report.noCitation")}</p>
        <Button
          type="button"
          variant="ghost"
          onClick={onOpenWhy}
          className="px-3 text-muted-foreground"
        >
          <Info aria-hidden="true" />
          {t("mockReview.report.why")}
        </Button>
      </div>
    </GlassCard>
  );
}

function ProposalCard({
  proposal,
  onOpenCitation,
}: {
  proposal: (typeof clauseProposals)[number];
  onOpenCitation: (sectionId: RecordedLawSectionId) => void;
}) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState(false);
  const section = recordedLawSections[proposal.sectionId];
  return (
    <GlassCard as="article" className="p-4 sm:p-6">
      <h3 className="font-display text-lg font-semibold text-foreground">{t(proposal.titleKey)}</h3>
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="rounded-md border border-rejected/20 bg-rejected/5 p-4">
          <p className="text-xs font-semibold text-rejected">{t("mockReview.proposals.old")}</p>
          <p className="mt-2 text-sm leading-7 text-foreground line-through decoration-rejected decoration-2">
            {t(proposal.oldKey)}
          </p>
        </div>
        <div className="rounded-md border border-verified/25 bg-verified/5 p-4">
          <p className="text-xs font-semibold text-verified">{t("mockReview.proposals.new")}</p>
          <p className="mt-2 text-sm leading-7 text-foreground">
            <mark className="rounded-sm bg-verified/20 px-1 text-foreground">
              {t(proposal.newKey)}
            </mark>
          </p>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <Button
          type="button"
          variant="ghost"
          onClick={() => onOpenCitation(proposal.sectionId)}
          className="px-2 text-sm text-verified"
        >
          <ShieldCheck aria-hidden="true" />
          {t("mockReview.proposals.source")}:{" "}
          {t("mockReview.citation.section", { number: section.section })}
        </Button>
        <Button
          type="button"
          variant={selected ? "secondary" : "outline"}
          onClick={() => setSelected((value) => !value)}
          className="text-xs"
        >
          {selected ? <Check aria-hidden="true" /> : <ClipboardCheck aria-hidden="true" />}
          {t(selected ? "mockReview.proposals.selected" : "mockReview.proposals.accept")}
        </Button>
      </div>
    </GlassCard>
  );
}

function DutiesPanel() {
  const { t } = useTranslation();
  const workerItems = workerDuties.map((key) => t(key));
  const employerItems = employerDuties.map((key) => t(key));
  return (
    <section>
      <h2 className="font-display text-2xl font-semibold text-foreground">
        {t("mockReview.duties.title")}
      </h2>
      <p className="mt-2 text-sm leading-7 text-muted-foreground">
        {t("mockReview.duties.description")}
      </p>
      <div className="mt-5 grid gap-5 md:grid-cols-2">
        <DutyColumn
          title={t("mockReview.duties.worker")}
          items={workerItems}
          keyPrefix={workerDuties[0]}
        />
        <DutyColumn
          title={t("mockReview.duties.employer")}
          items={employerItems}
          keyPrefix={employerDuties[0]}
        />
      </div>
    </section>
  );
}

function DutyColumn({
  title,
  items,
  keyPrefix,
}: {
  title: string;
  items: string[];
  keyPrefix: string;
}) {
  const [checked, setChecked] = useState<boolean[]>(() => items.map(() => false));
  return (
    <GlassCard as="section" className="p-4 sm:p-5">
      <h3 className="font-display text-lg font-semibold text-foreground">{title}</h3>
      <ul className="mt-3 divide-y divide-border">
        {items.map((item, index) => {
          const id = `${keyPrefix}-${index}`;
          return (
            <li key={id} className="flex min-h-14 items-center gap-3 py-2">
              <Checkbox
                id={id}
                checked={checked[index] ?? false}
                onCheckedChange={(value) =>
                  setChecked((previous) =>
                    previous.map((current, itemIndex) =>
                      itemIndex === index ? value === true : current,
                    ),
                  )
                }
                className="size-5"
              />
              <label
                htmlFor={id}
                className={`cursor-pointer text-sm leading-6 ${checked[index] ? "text-muted-foreground line-through" : "text-foreground"}`}
              >
                {item}
              </label>
            </li>
          );
        })}
      </ul>
    </GlassCard>
  );
}

function MemoPanel({
  side,
  onOpenCitation,
}: {
  side: ReviewSide;
  onOpenCitation: (sectionId: RecordedLawSectionId) => void;
}) {
  const { t } = useTranslation();
  const sourceIds = [...new Set(recordedFindings.flatMap((finding) => finding.sectionIds))];
  const printSourceIds = sourceIds;
  return (
    <section>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 print:hidden">
        <div>
          <p className="text-xs font-semibold text-verified">{t("mockReview.memo.generated")}</p>
          <p className="mt-1 text-sm text-muted-foreground">{t("mockReview.memo.printHint")}</p>
        </div>
        <Button type="button" onClick={() => window.print()}>
          <ArrowDownToLine aria-hidden="true" />
          {t("mockReview.memo.download")}
        </Button>
      </div>
      <article className="memo-print rounded-lg border border-border bg-background p-5 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-border pb-5">
          <div>
            <div className="flex items-center gap-2 text-verified">
              <FileText aria-hidden="true" className="size-5" />
              <span className="text-xs font-semibold">
                {t("app.name")}
                {t("app.ai")}
              </span>
            </div>
            <h2 className="mt-3 font-display text-2xl font-semibold text-foreground">
              {t("mockReview.memo.title")}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">{t("mockReview.memo.description")}</p>
          </div>
          <Badge tone="verified">{t("mockReview.memo.generated")}</Badge>
        </div>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <MemoMetric
            label={t("mockReview.memo.summary")}
            value={t("mockReview.report.verifiedCount")}
            count={allFindings.length}
          />
          <MemoMetric
            label={t("mockReview.memo.preparedFor")}
            value={t(`mockReview.side.${side}`)}
            count=""
          />
        </div>
        <div className="mt-6 space-y-3">
          {allFindings.map((finding) => (
            <div key={finding.id} className="border-b border-border pb-3">
              <div className="flex flex-wrap items-center gap-2">
                <SeverityTag severity={finding.severity} showCode />
                <h3 className="font-medium text-foreground">{t(finding.titleKey)}</h3>
              </div>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                {t(finding.explanationKey)}
              </p>
            </div>
          ))}
          <div className="rounded-md border border-uncertain/20 bg-uncertain/5 p-3">
            <div className="flex flex-wrap items-center gap-2">
              <VerificationBadge status="uncertain" />
              <h3 className="font-medium text-foreground">{t(uncertainFinding.titleKey)}</h3>
            </div>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">
              {t(uncertainFinding.explanationKey)}
            </p>
          </div>
        </div>
        <div className="mt-6">
          <h3 className="text-sm font-semibold text-foreground">
            {t("mockReview.memo.sourceList")}
          </h3>
          <div className="mt-2 flex flex-wrap gap-2">
            {sourceIds.map((sourceId) => {
              const source = recordedLawSections[sourceId];
              return (
                <Button
                  key={sourceId}
                  type="button"
                  variant="outline"
                  onClick={() => onOpenCitation(sourceId)}
                  className="min-h-11 px-3 text-xs print:hidden"
                >
                  {t("mockReview.citation.section", { number: source.section })}
                </Button>
              );
            })}
          </div>
          <ol className="mt-2 hidden list-decimal space-y-1 pl-5 text-xs leading-5 text-muted-foreground print:block">
            {printSourceIds.map((sourceId) => {
              const source = recordedLawSections[sourceId];
              return (
                <li key={sourceId}>
                  {t("mockReview.citation.section", { number: source.section })} ·{" "}
                  {t("mockReview.law.act")} · {t(source.titleKey)} · {source.href}
                </li>
              );
            })}
          </ol>
        </div>
        <p className="mt-6 border-t border-border pt-4 text-xs leading-6 text-muted-foreground">
          {t("mockReview.memo.notReal")}
        </p>
        <div className="mt-5 hidden print:block">
          <Disclaimer />
        </div>
      </article>
    </section>
  );
}

function MemoMetric({ label, value, count }: { label: string; value: string; count: number | "" }) {
  const { i18n } = useTranslation();
  return (
    <div className="rounded-md border border-border bg-card/40 p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 font-medium text-foreground">
        {value}
        {count !== ""
          ? ` · ${new Intl.NumberFormat(i18n.resolvedLanguage ?? "bn-BD").format(count)}`
          : ""}
      </p>
    </div>
  );
}
