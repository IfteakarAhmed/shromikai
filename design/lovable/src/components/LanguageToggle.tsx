import { useEffect } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/Button";

const languages = ["bn", "en"] as const;
type AppLanguage = (typeof languages)[number];

export function LanguageToggle() {
  const { i18n, t } = useTranslation();
  const currentLanguage: AppLanguage = i18n.resolvedLanguage === "en" ? "en" : "bn";

  useEffect(() => {
    document.documentElement.lang = currentLanguage;
  }, [currentLanguage]);

  function changeLanguage(language: AppLanguage) {
    void i18n.changeLanguage(language);
  }

  return (
    <div
      role="group"
      aria-label={t("review.languageLabel")}
      className="inline-flex min-h-11 items-center rounded-lg border border-border bg-background/70 p-0.5"
    >
      {languages.map((language) => (
        <Button
          key={language}
          type="button"
          variant="ghost"
          size="sm"
          aria-pressed={currentLanguage === language}
          aria-label={t(language === "bn" ? "language.switchToBangla" : "language.switchToEnglish")}
          onClick={() => changeLanguage(language)}
          className={
            currentLanguage === language
              ? "min-h-10 rounded-md bg-secondary px-3 text-foreground hover:bg-secondary"
              : "min-h-10 rounded-md px-3 text-muted-foreground"
          }
        >
          {t(language === "bn" ? "language.bangla" : "language.english")}
        </Button>
      ))}
    </div>
  );
}
