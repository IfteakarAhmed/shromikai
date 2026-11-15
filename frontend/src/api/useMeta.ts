import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { Language } from '../i18n'
import { getMeta } from './client'

/** The public build, corpus and audit information of the API (E-01), cached per language. */
export function useMeta() {
  const { i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  return useQuery({
    queryKey: ['meta', language],
    queryFn: () => getMeta(language),
    retry: false,
    staleTime: 60_000,
  })
}
