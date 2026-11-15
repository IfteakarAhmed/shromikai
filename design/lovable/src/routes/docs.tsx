import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, ArrowUpRight, BookOpenCheck, ShieldCheck } from "lucide-react";
import { useTranslation } from "react-i18next";

import { Disclaimer } from "@/components/Disclaimer";
import { GlassCard } from "@/components/GlassCard";

export const Route = createFileRoute("/docs")({
  head: () => ({
    meta: [
      { title: "যাচাইকরণ পদ্ধতি · ShromikAI" },
      {
        name: "description",
        content: "শ্রমিকAI কীভাবে বাংলাদেশের শ্রম আইনের সরকারি সূত্র যাচাই করে এবং কোথায় এর সীমা।",
      },
      { property: "og:title", content: "যাচাইকরণ পদ্ধতি · ShromikAI" },
      {
        property: "og:description",
        content: "শ্রমিকAI কীভাবে বাংলাদেশের শ্রম আইনের সরকারি সূত্র যাচাই করে এবং কোথায় এর সীমা।",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: DocsPage,
});

function DocsPage() {
  const { t } = useTranslation();

  return (
    <div className="relative min-h-screen overflow-hidden">
      <div aria-hidden="true" className="texture-grid absolute inset-x-0 top-0 -z-10 h-[34rem]" />
      <header className="mx-auto max-w-5xl px-gutter py-5">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center gap-2 rounded-lg text-sm font-medium text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          {t("navigation.backHome")}
        </Link>
      </header>
      <main className="mx-auto max-w-5xl px-gutter pb-16 pt-8 sm:pt-12">
        <p className="text-sm font-medium text-verified">{t("docs.sourceTitle")}</p>
        <h1 className="mt-2 font-display text-4xl font-semibold leading-[1.3] text-foreground sm:text-5xl">
          {t("docs.title")}
        </h1>
        <p className="mt-4 max-w-2xl text-base leading-8 text-muted-foreground">
          {t("docs.description")}
        </p>

        <div className="mt-8 grid gap-4 md:grid-cols-2">
          <GlassCard as="section" className="p-5 sm:p-6">
            <ShieldCheck aria-hidden="true" className="size-6 text-verified" />
            <h2 className="mt-4 font-display text-xl font-semibold text-foreground">
              {t("docs.sourceTitle")}
            </h2>
            <p className="mt-2 text-sm leading-7 text-muted-foreground">
              {t("docs.sourceDescription")}
            </p>
            <a
              href="https://bdlaws.minlaw.gov.bd/act-952.html"
              target="_blank"
              rel="noopener noreferrer"
              className="mt-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-verified hover:underline"
            >
              {t("citation.officialSource")}
              <ArrowUpRight aria-hidden="true" className="size-4" />
            </a>
          </GlassCard>
          <GlassCard as="section" className="p-5 sm:p-6">
            <BookOpenCheck aria-hidden="true" className="size-6 text-uncertain" />
            <h2 className="mt-4 font-display text-xl font-semibold text-foreground">
              {t("docs.limitsTitle")}
            </h2>
            <p className="mt-2 text-sm leading-7 text-muted-foreground">
              {t("docs.limitsDescription")}
            </p>
          </GlassCard>
        </div>
        <div className="mt-6">
          <Disclaimer />
        </div>
      </main>
    </div>
  );
}
