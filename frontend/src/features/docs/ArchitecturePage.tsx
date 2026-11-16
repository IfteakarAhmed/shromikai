import { useTranslation } from 'react-i18next'
import { useTitle } from '../../lib/useTitle'
import { ContainersDiagram, PipelineDiagram } from './ArchitectureDiagrams'
import { DocBody } from './DocPage'
import { sectionOf } from './markdownText'
import { useDocument } from './sources'

const SPEC = 'docs/spec/architecture.md'

/** `/docs/architecture` (FR-74): two hand-drawn pictures and the principles from the specification. */
export default function ArchitecturePage() {
  const { t } = useTranslation()
  useTitle(t('docs.nav.architecture'))
  const spec = useDocument(SPEC)
  const principles =
    spec.data === undefined || spec.data === null
      ? undefined
      : [
          sectionOf(spec.data, '## 1. Architectural principles'),
          sectionOf(spec.data, '## 7. Verification algorithm'),
        ].join('\n\n')
  return (
    <div>
      <h1 className="text-3xl font-bold">{t('docs.architecture.title')}</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">{t('docs.architecture.intro')}</p>
      <h2 className="mt-6 text-2xl font-bold">{t('docs.architecture.containersTitle')}</h2>
      <div className="mt-3">
        <ContainersDiagram />
      </div>
      <h2 className="mt-8 text-2xl font-bold">{t('docs.architecture.pipelineTitle')}</h2>
      <div className="mt-3">
        <PipelineDiagram />
      </div>
      <h2 className="mt-8 text-2xl font-bold">{t('docs.architecture.principles')}</h2>
      <p className="mt-1 text-muted-foreground">{t('docs.englishOnly')}</p>
      <div className="mt-4">
        {principles === undefined ? (
          <DocBody path={SPEC} source={undefined} />
        ) : (
          <DocBody source={principles} baseLevel={3} />
        )}
      </div>
    </div>
  )
}
