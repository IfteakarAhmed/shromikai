import { FileSearch } from "lucide-react";
import { useTranslation } from "react-i18next";

interface EmptyStateProps {
  title?: string;
  description?: string;
}

export function EmptyState({ title, description }: EmptyStateProps) {
  const { t } = useTranslation();

  return (
    <div className="flex min-h-48 flex-col items-center justify-center gap-3 px-6 py-10 text-center">
      <span className="flex size-12 items-center justify-center rounded-full border border-border bg-secondary text-muted-foreground">
        <FileSearch aria-hidden="true" className="size-5" />
      </span>
      <h3 className="font-display text-lg font-semibold text-foreground">
        {title ?? t("common.emptyTitle")}
      </h3>
      <p className="max-w-sm text-sm text-muted-foreground">
        {description ?? t("common.emptyDescription")}
      </p>
    </div>
  );
}
