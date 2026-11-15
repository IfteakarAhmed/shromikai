import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUpRight,
  Check,
  FileCheck2,
  FileUp,
  LockKeyhole,
  Search,
  ShieldCheck,
  UsersRound,
  X,
} from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { CitationChip } from "@/components/CitationChip";
import { Disclaimer } from "@/components/Disclaimer";
import { GlassCard } from "@/components/GlassCard";
import { LanguageToggle } from "@/components/LanguageToggle";
import { LowBandwidthToggle } from "@/components/LowBandwidthToggle";
import { ScrollReveal } from "@/components/ScrollReveal";
import { ContractSceneFrame } from "@/components/scene/ContractSceneFrame";
import { ThemeToggle } from "@/components/ThemeToggle";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "ShromikAI — আপনার চাকরির চুক্তি, আইনের চোখে" },
      {
        name: "description",
        content:
          "চুক্তি দিন। কয়েক মিনিটে জানুন কোথায় ঝুঁকি, কোন ধারা আপনার পক্ষে, আর কী বদলাতে বলবেন। প্রতিটি আইনি উদ্ধৃতি সরকারি উৎস থেকে যাচাই করা।",
      },
      { property: "og:title", content: "ShromikAI — আপনার চাকরির চুক্তি, আইনের চোখে" },
      {
        property: "og:description",
        content:
          "চুক্তি দিন। কয়েক মিনিটে জানুন কোথায় ঝুঁকি, কোন ধারা আপনার পক্ষে, আর কী বদলাতে বলবেন। প্রতিটি আইনি উদ্ধৃতি সরকারি উৎস থেকে যাচাই করা।",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

const workSteps = [
  { key: "upload", icon: FileUp },
  { key: "privacy", icon: LockKeyhole },
  { key: "review", icon: Search },
  { key: "verify", icon: ShieldCheck },
] as const;

const audiences = [
  { key: "workers", icon: ShieldCheck, role: "worker" },
  { key: "hr", icon: FileCheck2, role: "hr" },
  { key: "ngo", icon: UsersRound, role: "admin" },
] as const;

function Index() {
  const { t } = useTranslation();
  const [lowBandwidth, setLowBandwidth] = useState(false);

  return (
    <div className="relative isolate min-h-screen overflow-hidden">
      <div aria-hidden="true" className="texture-grid absolute inset-x-0 top-0 -z-10 h-[42rem]" />

      <header className="mx-auto grid max-w-7xl grid-cols-[minmax(0,1fr)_auto] grid-rows-2 items-center gap-x-3 gap-y-3 px-gutter py-4 sm:gap-x-5 sm:py-6 lg:grid-rows-1">
        <a
          href="#top"
          className="group inline-flex min-h-11 min-w-0 items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={`${t("app.name")} ${t("app.ai")}`}
        >
          <span className="flex size-11 shrink-0 items-center justify-center rounded-xl border border-verified/25 bg-verified/10 text-verified shadow-glass-panel transition-transform duration-300 group-hover:-rotate-3 group-hover:scale-105">
            <span className="font-display text-xl font-bold" lang="bn">
              শ
            </span>
          </span>
          <span className="flex min-w-0 flex-col leading-tight">
            <span className="font-display text-lg font-bold text-foreground">
              {t("app.name")}
              <span className="text-verified">{t("app.ai")}</span>
            </span>
            <span className="truncate text-xs text-muted-foreground">{t("app.tagline")}</span>
          </span>
        </a>

        <div className="col-start-2 row-start-1 flex items-center justify-end gap-2 sm:gap-3">
          <div className="hidden items-center gap-2 rounded-full border border-border bg-background/60 px-3 py-1.5 text-xs text-muted-foreground xl:flex">
            <ShieldCheck aria-hidden="true" className="size-4 text-verified" />
            <span>{t("header.officialSources")}</span>
          </div>
          <ThemeToggle />
          <LanguageToggle />
        </div>
        <div className="col-span-2 row-start-2 flex items-center justify-end gap-2 lg:col-span-1 lg:col-start-2 lg:row-start-1">
          <LowBandwidthToggle checked={lowBandwidth} onCheckedChange={setLowBandwidth} />
        </div>
      </header>

      <main>
        <div id="top" className="mx-auto max-w-7xl px-gutter pb-12 pt-5 sm:pb-16 sm:pt-10 lg:pb-20">
          <section
            aria-labelledby="page-title"
            className="grid items-center gap-9 lg:grid-cols-[0.92fr_1.08fr] lg:gap-12"
          >
            <div className="max-w-2xl">
              <div className="mb-5 inline-flex min-h-8 items-center gap-2 rounded-full border border-verified/20 bg-verified/5 px-3 text-sm font-medium text-verified">
                <span className="size-1.5 rounded-full bg-verified" aria-hidden="true" />
                {t("hero.eyebrow")}
              </div>
              <h1
                id="page-title"
                className="max-w-2xl font-display text-4xl font-semibold leading-[1.3] text-foreground sm:text-5xl xl:text-6xl"
              >
                {t("hero.title")}
              </h1>
              <p className="mt-5 max-w-xl text-base leading-[1.85] text-muted-foreground sm:mt-6 sm:text-lg">
                {t("hero.description")}
              </p>

              <div className="mt-7 flex flex-col items-start gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                <Button
                  asChild
                  size="lg"
                  className="group w-full rounded-lg px-5 font-semibold sm:w-auto"
                >
                  <Link to="/app" search={{ role: "worker" }}>
                    {t("hero.sampleButton")}
                    <ArrowDown
                      aria-hidden="true"
                      className="transition-transform duration-300 group-hover:translate-y-0.5"
                    />
                  </Link>
                </Button>
                <Link
                  to="/app"
                  search={{ role: "worker" }}
                  className="inline-flex min-h-11 items-center gap-2 rounded-lg px-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {t("hero.workerDemo")}
                  <ArrowUpRight aria-hidden="true" className="size-4" />
                </Link>
              </div>
              <div className="mt-2 flex flex-wrap gap-2">
                <Link
                  to="/app"
                  search={{ role: "hr" }}
                  className="inline-flex min-h-11 items-center rounded-lg px-2 text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {t("hero.hrDemo")}
                </Link>
                <span aria-hidden="true" className="my-auto text-muted-foreground/60">
                  ·
                </span>
                <Link
                  to="/app"
                  search={{ role: "admin" }}
                  className="inline-flex min-h-11 items-center rounded-lg px-2 text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {t("hero.adminDemo")}
                </Link>
              </div>
            </div>

            <div className="relative mx-auto aspect-[5/4] w-full max-w-2xl lg:max-w-none">
              <ContractSceneFrame alt={t("hero.sceneAlt")} lowBandwidth={lowBandwidth} />
              <div className="pointer-events-none absolute -bottom-3 left-1/2 w-[min(94%,28rem)] -translate-x-1/2 rounded-xl border border-border/70 bg-background/85 px-4 py-3 shadow-glass-panel backdrop-blur-xl sm:-bottom-5">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg border border-verified/20 bg-verified/10 text-verified">
                      <ShieldCheck aria-hidden="true" className="size-4" />
                    </span>
                    <span className="truncate text-sm font-medium text-foreground">
                      {t("comparison.verifiedStatus")}
                    </span>
                  </div>
                  <Badge tone="verified" className="shrink-0">
                    {t("comparison.sectionChip")}
                  </Badge>
                </div>
              </div>
            </div>
          </section>

          <section
            aria-label={t("trust.noInventedLaw")}
            className="mt-14 grid gap-3 border-y border-border/70 py-4 sm:mt-20 sm:grid-cols-3 sm:gap-5 sm:py-5"
          >
            <TrustItem icon={X} tone="rejected" text={t("trust.noInventedLaw")} />
            <TrustItem icon={LockKeyhole} tone="verified" text={t("trust.privateOnDevice")} />
            <TrustItem icon={ArrowUpRight} tone="verified" text={t("trust.officialLink")} />
          </section>
        </div>

        <section
          id="how-it-works"
          aria-labelledby="steps-title"
          className="border-y border-border/70 bg-ink-raised/45"
        >
          <div className="mx-auto max-w-7xl px-gutter py-16 sm:py-20 lg:py-24">
            <ScrollReveal>
              <SectionHeading
                eyebrow={t("howItWorks.eyebrow")}
                title={t("howItWorks.title")}
                description={t("howItWorks.description")}
                id="steps-title"
              />
            </ScrollReveal>
            <div className="relative mt-9 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:gap-4">
              <div
                aria-hidden="true"
                className="pipeline-line absolute left-[12%] right-[12%] top-8 hidden h-px bg-border lg:block"
              />
              {workSteps.map((step, index) => {
                const Icon = step.icon;
                return (
                  <ScrollReveal key={step.key} delay={(index % 4) as 0 | 1 | 2 | 3}>
                    <GlassCard className="relative h-full p-4 sm:p-5">
                      <span className="relative z-10 flex size-11 items-center justify-center rounded-xl border border-verified/25 bg-background text-verified shadow-glass-panel">
                        <Icon aria-hidden="true" className="size-5" />
                      </span>
                      <p className="mt-4 text-xs font-semibold text-muted-foreground">
                        {t("howItWorks.stepNumber", { number: index + 1 })}
                      </p>
                      <h3 className="mt-1 font-display text-lg font-semibold text-foreground">
                        {t(`howItWorks.${step.key}`)}
                      </h3>
                    </GlassCard>
                  </ScrollReveal>
                );
              })}
            </div>
          </div>
        </section>

        <section
          id="verified-citations"
          aria-labelledby="comparison-title"
          className="mx-auto max-w-7xl px-gutter py-16 sm:py-20 lg:py-24"
        >
          <ScrollReveal>
            <SectionHeading
              eyebrow={t("comparison.eyebrow")}
              title={t("comparison.title")}
              description={t("comparison.description")}
              id="comparison-title"
            />
          </ScrollReveal>
          <div className="mt-9 grid gap-4 lg:grid-cols-2">
            <ScrollReveal delay={1}>
              <GlassCard className="h-full border-rejected/25 p-5 sm:p-6">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="font-display text-lg font-semibold text-foreground">
                    {t("comparison.plainAi")}
                  </h3>
                  <Badge tone="rejected">
                    <X aria-hidden="true" className="size-3.5" />
                    {t("comparison.notFound")}
                  </Badge>
                </div>
                <div className="mt-5 rounded-lg border border-rejected/25 bg-rejected/5 p-4">
                  <p className="text-xs font-medium text-muted-foreground">
                    {t("review.analysisLabel")}
                  </p>
                  <p className="mt-2 font-display text-lg font-semibold text-rejected line-through decoration-2">
                    {t("comparison.plainCitation")}
                  </p>
                  <p className="mt-3 flex items-center gap-2 text-sm text-rejected">
                    <X aria-hidden="true" className="size-4 shrink-0" />
                    <span>{t("comparison.notFound")}</span>
                  </p>
                </div>
              </GlassCard>
            </ScrollReveal>

            <ScrollReveal delay={2}>
              <GlassCard className="h-full border-verified/25 p-5 sm:p-6">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="font-display text-lg font-semibold text-foreground">
                    {t("comparison.shromikAi")}
                  </h3>
                  <Badge tone="verified">
                    <Check aria-hidden="true" className="size-3.5" />
                    {t("comparison.verifiedStatus")}
                  </Badge>
                </div>
                <blockquote className="mt-5 rounded-lg border border-verified/20 bg-verified/5 p-4 font-display text-sm leading-8 text-foreground sm:text-base">
                  {t("comparison.verifiedQuote")}
                </blockquote>
                <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                  <span className="text-xs text-muted-foreground">
                    {t("comparison.translationLabel")}
                  </span>
                  <CitationChip
                    citation={{
                      section: "১০৮",
                      titleKey: "citation.labourAct",
                      href: "https://bdlaws.minlaw.gov.bd/act-952/section-29435.html",
                    }}
                  />
                </div>
              </GlassCard>
            </ScrollReveal>
          </div>
        </section>

        <section
          id="for-whom"
          aria-labelledby="audience-title"
          className="border-y border-border/70 bg-ink-raised/45"
        >
          <div className="mx-auto max-w-7xl px-gutter py-16 sm:py-20 lg:py-24">
            <ScrollReveal>
              <SectionHeading
                eyebrow={t("audience.eyebrow")}
                title={t("audience.title")}
                id="audience-title"
              />
            </ScrollReveal>
            <div className="mt-9 grid gap-4 md:grid-cols-3">
              {audiences.map((audience, index) => {
                const Icon = audience.icon;
                const demoKey =
                  audience.role === "worker"
                    ? "workerCta"
                    : audience.role === "hr"
                      ? "hrCta"
                      : "adminCta";

                return (
                  <ScrollReveal key={audience.key} delay={(index % 4) as 0 | 1 | 2 | 3}>
                    <GlassCard interactive as="article" className="flex h-full flex-col p-5 sm:p-6">
                      <span className="flex size-12 items-center justify-center rounded-xl border border-verified/20 bg-verified/10 text-verified">
                        <Icon aria-hidden="true" className="size-5" />
                      </span>
                      <h3 className="mt-5 font-display text-xl font-semibold text-foreground">
                        {t(`audience.${audience.key}`)}
                      </h3>
                      <p className="mt-2 flex-1 text-sm leading-7 text-muted-foreground">
                        {t(`audience.${audience.key}Description`)}
                      </p>
                      <Button asChild variant="outline" className="mt-5 w-full rounded-lg">
                        <Link to="/app" search={{ role: audience.role }}>
                          {t(`audience.${demoKey}`)}
                          <ArrowUpRight aria-hidden="true" />
                        </Link>
                      </Button>
                    </GlassCard>
                  </ScrollReveal>
                );
              })}
            </div>
          </div>
        </section>

        <section
          id="limits"
          aria-labelledby="limits-title"
          className="mx-auto max-w-7xl px-gutter py-16 sm:py-20 lg:py-24"
        >
          <ScrollReveal>
            <GlassCard className="grid gap-7 p-5 sm:p-8 lg:grid-cols-[0.8fr_1.2fr] lg:items-center lg:gap-12 lg:p-10">
              <div>
                <p className="text-sm font-medium text-uncertain">{t("limits.eyebrow")}</p>
                <h2
                  id="limits-title"
                  className="mt-2 font-display text-2xl font-semibold text-foreground sm:text-3xl"
                >
                  {t("limits.title")}
                </h2>
              </div>
              <div className="space-y-4">
                <Disclaimer />
                <div className="flex items-start gap-3 rounded-lg border border-uncertain/20 bg-uncertain/5 px-4 py-3">
                  <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full border border-uncertain/30 text-sm font-semibold text-uncertain">
                    ?
                  </span>
                  <p className="font-medium text-foreground">{t("limits.uncertain")}</p>
                </div>
                <p className="text-sm leading-7 text-muted-foreground">{t("limits.description")}</p>
              </div>
            </GlassCard>
          </ScrollReveal>
        </section>
      </main>

      <footer className="border-t border-border/70">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-gutter py-6 sm:flex-row sm:items-center sm:justify-between">
          <a
            href="#top"
            className="inline-flex min-h-11 items-center gap-2 font-display font-semibold text-foreground"
          >
            {t("app.name")}
            <span className="text-verified">{t("app.ai")}</span>
          </a>
          <nav
            aria-label={t("footer.docs")}
            className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-muted-foreground"
          >
            <Link
              to="/docs"
              className="inline-flex min-h-11 items-center transition-colors hover:text-foreground"
            >
              {t("footer.docs")}
            </Link>
            <Link
              to="/app"
              search={{ role: "worker" }}
              className="inline-flex min-h-11 items-center transition-colors hover:text-foreground"
            >
              {t("footer.app")}
            </Link>
            <a
              href="https://bdlaws.minlaw.gov.bd/act-952.html"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-11 items-center gap-1 transition-colors hover:text-foreground"
            >
              {t("citation.officialSource")}
              <ArrowUpRight aria-hidden="true" className="size-3.5" />
            </a>
          </nav>
          <span className="text-xs text-muted-foreground">{t("footer.copyright")}</span>
        </div>
      </footer>
    </div>
  );
}

function TrustItem({
  icon: Icon,
  tone,
  text,
}: {
  icon: typeof Check;
  tone: "verified" | "rejected";
  text: string;
}) {
  return (
    <div className="flex min-h-11 items-center gap-2.5 text-sm font-medium text-foreground">
      <span
        className={`flex size-8 shrink-0 items-center justify-center rounded-full border ${tone === "verified" ? "border-verified/25 bg-verified/10 text-verified" : "border-rejected/25 bg-rejected/10 text-rejected"}`}
      >
        <Icon aria-hidden="true" className="size-4" />
      </span>
      <span>{text}</span>
    </div>
  );
}

function SectionHeading({
  eyebrow,
  title,
  description,
  id,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  id: string;
}) {
  return (
    <div className="max-w-2xl">
      <p className="text-sm font-medium text-verified">{eyebrow}</p>
      <h2
        id={id}
        className="mt-2 font-display text-3xl font-semibold leading-[1.35] text-foreground sm:text-4xl"
      >
        {title}
      </h2>
      {description && (
        <p className="mt-3 text-sm leading-7 text-muted-foreground sm:text-base">{description}</p>
      )}
    </div>
  );
}
