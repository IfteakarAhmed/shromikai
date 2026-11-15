import { Check, Circle, LoaderCircle, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import { GlassCard } from "@/components/GlassCard";

interface ReviewProgressProps {
  onComplete: () => void;
}

export function ReviewProgress({ onComplete }: ReviewProgressProps) {
  const { t } = useTranslation();
  const stageKeys = [
    "reading",
    "redacting",
    "classifying",
    "searching",
    "analyzing",
    "verifying",
  ] as const;
  const stages = stageKeys.map((stage) => t(`mockReview.progress.stages.${stage}`));
  const statuses = stageKeys.map((stage) => t(`mockReview.progress.statuses.${stage}`));
  const [activeStage, setActiveStage] = useState(0);

  useEffect(() => {
    let completionTimer: number | undefined;
    const timer = window.setInterval(() => {
      setActiveStage((stage) => {
        if (stage >= stages.length - 1) {
          window.clearInterval(timer);
          completionTimer = window.setTimeout(onComplete, 420);
          return stages.length;
        }
        return stage + 1;
      });
    }, 680);

    return () => {
      window.clearInterval(timer);
      if (completionTimer !== undefined) window.clearTimeout(completionTimer);
    };
  }, [onComplete, stages.length]);

  return (
    <section
      className="mx-auto w-full max-w-3xl"
      aria-live="polite"
      aria-busy={activeStage < stages.length}
    >
      <div className="mb-7 flex flex-wrap items-center gap-3">
        <Badge tone="neutral">{t("mockReview.memo.generated")}</Badge>
      </div>
      <h1 className="font-display text-3xl font-semibold leading-[1.35] text-foreground sm:text-4xl">
        {t("mockReview.progress.title")}
      </h1>
      <p className="mt-3 text-sm leading-7 text-muted-foreground">
        {t("mockReview.progress.description")}
      </p>

      <GlassCard as="section" className="mt-8 overflow-hidden p-2 sm:p-4">
        <ol>
          {stages.map((stage, index) => {
            const complete = index < activeStage;
            const current = index === activeStage && activeStage < stages.length;
            const StatusIcon = complete ? Check : current ? LoaderCircle : Circle;
            const status = complete
              ? t("mockReview.progress.stageDone")
              : current
                ? t("mockReview.progress.stageCurrent")
                : t("mockReview.progress.stageWaiting");
            return (
              <li
                key={stage}
                className="relative grid min-h-20 grid-cols-[3rem_minmax(0,1fr)_auto] items-center gap-3 px-3 sm:min-h-24 sm:px-5"
              >
                {index < stages.length - 1 ? (
                  <span
                    className="absolute bottom-0 left-[2.25rem] top-[3.8rem] w-px bg-border sm:left-[2.7rem]"
                    aria-hidden="true"
                  />
                ) : null}
                <span
                  className={`relative z-10 flex size-10 items-center justify-center rounded-full border transition-colors ${complete ? "border-verified/40 bg-verified/10 text-verified" : current ? "border-primary/50 bg-primary/10 text-primary" : "border-border bg-background text-muted-foreground"}`}
                >
                  <StatusIcon
                    aria-hidden="true"
                    className={`size-4 ${current ? "animate-spin" : ""}`}
                  />
                </span>
                <span className="min-w-0 py-4">
                  <span
                    className={`block font-medium ${current || complete ? "text-foreground" : "text-muted-foreground"}`}
                  >
                    {stage}
                  </span>
                  {current ? (
                    <span className="mt-1 block text-sm text-muted-foreground">
                      {statuses[index]}
                    </span>
                  ) : null}
                </span>
                <span
                  className={`shrink-0 text-xs ${complete ? "text-verified" : current ? "text-primary" : "text-muted-foreground"}`}
                >
                  {status}
                </span>
              </li>
            );
          })}
        </ol>
        {activeStage >= stages.length ? (
          <div className="flex items-center gap-3 border-t border-border px-3 py-4 text-sm font-medium text-verified sm:px-5">
            <ShieldCheck aria-hidden="true" className="size-5" />
            {t("mockReview.progress.stageDone")}
          </div>
        ) : null}
      </GlassCard>
    </section>
  );
}
