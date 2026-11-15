import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { AppUploadScreen, type ReviewSide } from "@/components/appflow/AppUploadScreen";
import { ReviewProgress } from "@/components/appflow/ReviewProgress";
import { ReviewResults } from "@/components/appflow/ReviewResults";
import { Badge } from "@/components/Badge";
import { LanguageToggle } from "@/components/LanguageToggle";
import { ThemeToggle } from "@/components/ThemeToggle";
import { validateContractFile, type ContractFileError } from "@/lib/contract-file-validation";

type DemoRole = "worker" | "hr" | "admin";
type Screen = "upload" | "progress" | "results";

export const Route = createFileRoute("/app")({
  validateSearch: (search: Record<string, unknown>): { role: DemoRole } => ({
    role: search["role"] === "hr" || search["role"] === "admin" ? search["role"] : "worker",
  }),
  head: () => ({
    meta: [
      { title: "নমুনা চুক্তি পর্যালোচনা · ShromikAI" },
      {
        name: "description",
        content:
          "বাংলাদেশের শ্রম আইনের সরকারি সূত্রসহ স্থির নমুনা কর্মচুক্তি পর্যালোচনা। আপনার ফাইল বা লেখা সার্ভারে পাঠানো হয় না।",
      },
      { property: "og:title", content: "নমুনা চুক্তি পর্যালোচনা · ShromikAI" },
      {
        property: "og:description",
        content: "বাংলাদেশের শ্রম আইনের সরকারি সূত্রসহ স্থির নমুনা কর্মচুক্তি পর্যালোচনা।",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: ContractReviewApp,
});

function ContractReviewApp() {
  const { t } = useTranslation();
  const { role } = Route.useSearch();
  const [screen, setScreen] = useState<Screen>("upload");
  const [file, setFile] = useState<File | null>(null);
  const [text, setText] = useState("");
  const [side, setSide] = useState<ReviewSide>(role === "worker" ? "worker" : "employer");
  const [error, setError] = useState<ContractFileError | "invalidText" | null>(null);

  const handleFileSelected = (nextFile: File | null) => {
    setError(null);
    if (nextFile) {
      const validationError = validateContractFile(nextFile);
      if (validationError) {
        setFile(null);
        setError(validationError);
        return;
      }
    }
    setFile(nextFile);
  };

  const startReview = () => {
    if (!file && text.trim().length === 0) {
      setError("invalidText");
      return;
    }
    // The selected document is never parsed, transmitted, or persisted in this recorded demo.
    // Clear the local form as the flow starts; the fixed sample findings are separate mock data.
    setFile(null);
    setText("");
    setError(null);
    setScreen("progress");
  };

  const finishProgress = useCallback(() => setScreen("results"), []);

  return (
    <div className="relative min-h-screen overflow-hidden">
      <div aria-hidden="true" className="texture-grid absolute inset-x-0 top-0 -z-10 h-[34rem]" />
      <header className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-gutter py-4 sm:py-6">
        <Link
          to="/"
          className="inline-flex min-h-11 min-w-0 items-center gap-2 rounded-md text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
          <span>{t("navigation.backHome")}</span>
        </Link>
        <div className="flex min-w-0 items-center gap-2">
          <LanguageToggle />
          <ThemeToggle />
          <span className="hidden size-8 items-center justify-center rounded-md border border-verified/25 bg-verified/10 text-verified sm:flex">
            <ShieldCheck aria-hidden="true" className="size-4" />
          </span>
          <span className="hidden font-display text-sm font-bold text-foreground sm:inline">
            {t("app.name")}
            <span className="text-verified">{t("app.ai")}</span>
          </span>
          <Badge
            tone="neutral"
            className="max-w-[9rem] truncate text-[10px] sm:max-w-none sm:text-xs"
          >
            {t(`roles.${role}`)}
          </Badge>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-gutter pb-14 pt-7 sm:pb-20 sm:pt-10">
        {screen === "upload" ? (
          <AppUploadScreen
            file={file}
            error={error}
            side={side}
            text={text}
            onFileSelected={handleFileSelected}
            onSideChange={setSide}
            onTextChange={(nextText) => {
              setText(nextText);
              setError(null);
            }}
            onAnalyze={startReview}
          />
        ) : null}
        {screen === "progress" ? <ReviewProgress onComplete={finishProgress} /> : null}
        {screen === "results" ? (
          <ReviewResults
            side={side}
            onBackToUpload={() => {
              setScreen("upload");
              setFile(null);
              setText("");
            }}
          />
        ) : null}
      </main>
    </div>
  );
}
