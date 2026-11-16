import { useTranslation } from 'react-i18next'
import { Button } from '../../components/ui/Button'
import { Icon } from '../../components/ui/Icon'
import type { Tier } from './cinematic/tier'

/**
 * The always-visible switch between the moving scene and the still picture (FR-68, FR-76). The
 * choice lasts for this page only; nothing is stored. Without WebGL there is nothing to switch.
 */
export function SceneToggle({
  tier,
  canEnable,
  onChoose,
}: {
  tier: Tier
  canEnable: boolean
  onChoose: (choice: 'lite' | 'scene') => void
}) {
  const { t } = useTranslation()
  if (tier === 'lite' && !canEnable) {
    return null
  }
  const still = tier !== 'lite'
  return (
    <div className="fixed bottom-4 right-4 z-20" role="group" aria-label={t('scene.label')}>
      <Button
        variant="outline"
        className="bg-background/90 shadow-glass backdrop-blur"
        onClick={() => {
          onChoose(still ? 'lite' : 'scene')
        }}
      >
        <Icon name={still ? 'eye' : 'play'} />
        {t(still ? 'scene.still' : 'scene.enable')}
      </Button>
    </div>
  )
}
