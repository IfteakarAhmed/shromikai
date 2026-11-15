import { useTranslation } from 'react-i18next'
import { usePreferences } from '../../state/preferencesContext'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'

/** Shows the language you can switch to, in that language. */
export function LanguageToggle() {
  const { t } = useTranslation()
  const { locale, setLocale } = usePreferences()
  const target = locale === 'bn' ? 'en' : 'bn'
  return (
    <Button
      variant="outline"
      onClick={() => {
        setLocale(target)
      }}
    >
      <Icon name="globe" />
      <span lang={target}>{t('language.switch')}</span>
      <span className="sr-only">{t('language.switchHint')}</span>
    </Button>
  )
}
