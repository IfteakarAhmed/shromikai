import { useTranslation } from 'react-i18next'
import { useTitle } from '../../lib/useTitle'
import { DocPage } from './DocPage'

/** `/docs/mcp`: how another AI agent uses the server; the text is `docs/mcp.md`. */
export default function McpPage() {
  const { t } = useTranslation()
  useTitle(t('docs.nav.mcp'))
  return <DocPage title={t('docs.nav.mcp')} path="docs/mcp.md" />
}
