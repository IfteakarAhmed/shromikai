import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/Button";

export function ThemeToggle() {
  const [isLight, setIsLight] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    document.documentElement.dataset["theme"] = isLight ? "light" : "dark";
  }, [isLight]);

  return (
    <Button
      type="button"
      variant="outline"
      size="icon"
      onClick={() => setIsLight((current) => !current)}
      aria-label={t(isLight ? "theme.switchToDark" : "theme.switchToLight")}
      title={t(isLight ? "theme.switchToDark" : "theme.switchToLight")}
      className="min-h-11 min-w-11 rounded-lg border-border bg-background/70 text-muted-foreground"
    >
      {isLight ? <Moon aria-hidden="true" /> : <Sun aria-hidden="true" />}
    </Button>
  );
}
