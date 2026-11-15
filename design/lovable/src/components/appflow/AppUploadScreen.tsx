import { Camera, FileText, FileUp, LockKeyhole, ShieldCheck, Trash2 } from "lucide-react";
import { useRef, useState, type DragEvent } from "react";
import { useTranslation } from "react-i18next";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { GlassCard } from "@/components/GlassCard";
import { Textarea } from "@/components/ui/textarea";
import { recordedSampleContract } from "@/mocks/security-guard-review";

export type ReviewSide = "worker" | "employer";

interface AppUploadScreenProps {
  file: File | null;
  error: string | null;
  side: ReviewSide;
  text: string;
  onFileSelected: (file: File | null) => void;
  onSideChange: (side: ReviewSide) => void;
  onTextChange: (text: string) => void;
  onAnalyze: () => void;
}

export function AppUploadScreen({
  file,
  error,
  side,
  text,
  onFileSelected,
  onSideChange,
  onTextChange,
  onAnalyze,
}: AppUploadScreenProps) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const canAnalyze = error === null && (file !== null || text.trim().length > 0);

  const pickFile = (candidate?: File) => {
    if (candidate) onFileSelected(candidate);
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    pickFile(event.dataTransfer.files.item(0) ?? undefined);
  };

  return (
    <section className="mx-auto grid w-full max-w-6xl gap-8 lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-12">
      <div className="min-w-0">
        <div className="mb-7 flex flex-wrap items-center gap-3">
          <Badge tone="neutral">{t("mockReview.memo.generated")}</Badge>
        </div>
        <h1 className="font-display text-3xl font-semibold leading-[1.35] text-foreground sm:text-4xl">
          {t("mockReview.upload.title")}
        </h1>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-muted-foreground sm:text-base">
          {t("mockReview.upload.description")}
        </p>

        <div className="mt-7 grid gap-5">
          <div
            onDragEnter={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragOver={(event) => event.preventDefault()}
            onDragLeave={(event) => {
              if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                setDragging(false);
              }
            }}
            onDrop={handleDrop}
            className={`rounded-lg border border-dashed bg-card/60 p-5 transition-colors sm:p-8 ${dragging ? "border-verified bg-verified/5" : "border-border"}`}
          >
            <input
              ref={inputRef}
              className="sr-only"
              type="file"
              accept="application/pdf,image/*,text/plain,.md,.markdown"
              aria-label={t("mockReview.upload.browse")}
              onChange={(event) => {
                pickFile(event.currentTarget.files?.item(0) ?? undefined);
                event.currentTarget.value = "";
              }}
            />
            <input
              ref={cameraInputRef}
              className="sr-only"
              type="file"
              accept="image/*"
              capture="environment"
              aria-label={t("mockReview.upload.camera")}
              onChange={(event) => {
                pickFile(event.currentTarget.files?.item(0) ?? undefined);
                event.currentTarget.value = "";
              }}
            />
            {file ? (
              <div className="flex flex-wrap items-center gap-4">
                <span className="flex size-12 shrink-0 items-center justify-center rounded-md border border-verified/25 bg-verified/10 text-verified">
                  <FileText aria-hidden="true" className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-muted-foreground">
                    {t("mockReview.upload.fileSelected")}
                  </p>
                  <p className="mt-1 break-all font-medium text-foreground">{file.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {(file.size / (1024 * 1024)).toFixed(2)} MB
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  aria-label={t("mockReview.upload.removeFile")}
                  onClick={() => {
                    onFileSelected(null);
                    if (inputRef.current) inputRef.current.value = "";
                  }}
                  className="px-3 text-muted-foreground"
                >
                  <Trash2 aria-hidden="true" />
                  <span>{t("mockReview.upload.removeFile")}</span>
                </Button>
              </div>
            ) : (
              <div className="flex flex-col items-center text-center">
                <span className="flex size-12 items-center justify-center rounded-md border border-border bg-background text-verified">
                  <FileUp aria-hidden="true" className="size-5" />
                </span>
                <p className="mt-4 font-display text-lg font-semibold text-foreground">
                  {t("mockReview.upload.dropTitle")}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t("mockReview.upload.dropHint")}
                </p>
                <div className="mt-5 flex flex-wrap justify-center gap-2">
                  <Button type="button" variant="outline" onClick={() => inputRef.current?.click()}>
                    <FileUp aria-hidden="true" />
                    {t("mockReview.upload.browse")}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => cameraInputRef.current?.click()}
                  >
                    <Camera aria-hidden="true" />
                    {t("mockReview.upload.camera")}
                  </Button>
                </div>
              </div>
            )}
          </div>

          <div className="flex items-center gap-3 text-xs text-muted-foreground" aria-hidden="true">
            <span className="h-px flex-1 bg-border" />
            <span>{t("common.or", { defaultValue: "অথবা" })}</span>
            <span className="h-px flex-1 bg-border" />
          </div>

          <div>
            <label
              htmlFor="contract-text"
              className="mb-2 block text-sm font-medium text-foreground"
            >
              {t("mockReview.upload.pasteLabel")}
            </label>
            <Textarea
              id="contract-text"
              value={text}
              onChange={(event) => onTextChange(event.currentTarget.value)}
              placeholder={t("mockReview.upload.pastePlaceholder")}
              className="min-h-36 resize-y rounded-lg border-border bg-card/55 leading-7 placeholder:text-muted-foreground/70"
            />
            <Button
              type="button"
              variant="ghost"
              className="mt-2 px-2 text-sm text-muted-foreground"
              onClick={() => onTextChange(recordedSampleContract)}
            >
              {t("mockReview.upload.useSample")}
            </Button>
          </div>

          {error ? (
            <p
              role="alert"
              className="rounded-md border border-rejected/25 bg-rejected/5 px-4 py-3 text-sm text-rejected"
            >
              {t(
                `mockReview.upload.${error === "tooLarge" ? "errorTooLarge" : error === "unsupportedType" ? "errorUnsupported" : "invalidText"}`,
              )}
            </p>
          ) : null}

          <div>
            <p className="mb-2 text-sm font-medium text-foreground">
              {t("mockReview.upload.sideLabel")}
            </p>
            <div
              className="grid grid-cols-2 rounded-lg border border-border bg-card/50 p-1"
              role="group"
              aria-label={t("mockReview.upload.sideLabel")}
            >
              {(["worker", "employer"] as const).map((option) => (
                <Button
                  key={option}
                  type="button"
                  variant={side === option ? "secondary" : "ghost"}
                  aria-pressed={side === option}
                  onClick={() => onSideChange(option)}
                  className={`h-11 rounded-md ${side === option ? "text-foreground shadow-sm" : "text-muted-foreground"}`}
                >
                  {t(`mockReview.upload.${option}`)}
                </Button>
              ))}
            </div>
          </div>

          <Button
            type="button"
            size="lg"
            disabled={!canAnalyze}
            onClick={onAnalyze}
            className="w-full sm:w-auto"
          >
            <ShieldCheck aria-hidden="true" />
            {t("mockReview.upload.analyze")}
          </Button>
        </div>
      </div>

      <aside className="lg:pt-14">
        <GlassCard as="section" className="p-5 sm:p-6">
          <span className="flex size-11 items-center justify-center rounded-md border border-verified/20 bg-verified/10 text-verified">
            <LockKeyhole aria-hidden="true" className="size-5" />
          </span>
          <h2 className="mt-4 font-display text-lg font-semibold text-foreground">
            {t("mockReview.upload.privacyTitle")}
          </h2>
          <p className="mt-2 text-sm font-semibold leading-6 text-verified">
            {t("mockReview.upload.privacyNote")}
          </p>
          <p className="mt-3 text-sm leading-7 text-muted-foreground">
            {t("mockReview.upload.privacyDetail")}
          </p>
        </GlassCard>
      </aside>
    </section>
  );
}
