import { useTranslation } from 'react-i18next'
import { usePreferences } from '../../state/preferencesContext'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'

export function ThemeToggle() {
  const { t } = useTranslation()
  const { theme, setTheme } = usePreferences()
  const light = theme === 'light'
  const label = t(light ? 'theme.switchToDark' : 'theme.switchToLight')
  return (
    <Button
      variant="outline"
      size="icon"
      onClick={() => {
        setTheme(light ? 'dark' : 'light')
      }}
      aria-label={label}
      title={label}
    >
      <Icon name={light ? 'moon' : 'sun'} />
    </Button>
  )
}
