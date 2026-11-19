import { textareaClass } from '../../components/ui/field'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import { startAnalysis, type AnalysisInput } from '../../api/analyses'
import { useAuthedCall } from '../../api/useAuthedCall'
import { isStaticReplay } from '../../api/replay'
import { useMeta } from '../../api/useMeta'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { GlassCard } from '../../components/ui/GlassCard'
import { Icon } from '../../components/ui/Icon'
import { Tabs } from '../../components/ui/Tabs'
import { cx } from '../../lib/cx'
import { formatNumber } from '../../lib/format'
import { compressImage } from '../../lib/image'
import {
  classifyFile,
  MAX_BYTES,
  MAX_IMAGES,
  MAX_TEXT,
  MIN_TEXT,
  validateImages,
  validatePdf,
  validateText,
  type UploadError,
} from '../../lib/upload'
import { useTitle } from '../../lib/useTitle'
import { usePreferences } from '../../state/preferencesContext'
import type { Language } from '../../i18n'

type Method = 'file' | 'photos' | 'text'

/** `/app`: give a contract as a PDF, photos or text, choose a side, and start the analysis. */
export function UploadPage() {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  useTitle(t('upload.title'))
  const navigate = useNavigate()
  const location = useLocation()
  const call = useAuthedCall()
  const meta = useMeta()
  const { side, setSide, locale } = usePreferences()
  const recorded = meta.data?.recordedDemo ?? null

  const [method, setMethod] = useState<Method>('file')
  const [pdf, setPdf] = useState<File | null>(null)
  const [images, setImages] = useState<File[]>([])
  const [text, setText] = useState('')
  const [invalid, setInvalid] = useState<UploadError | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const photoInput = useRef<HTMLInputElement>(null)
  const cameraInput = useRef<HTMLInputElement>(null)

  const effectiveSide = recorded === null ? side : (recorded.side as 'WORKER' | 'EMPLOYER')
  const outputLanguage = recorded === null ? locale : (recorded.outputLanguage as 'bn' | 'en')

  const start = useMutation({
    mutationFn: async (input: AnalysisInput) => {
      const prepared: AnalysisInput =
        input.kind === 'images'
          ? { kind: 'images', files: await Promise.all(input.files.map(compressImage)) }
          : input
      return call((token, lang) =>
        startAnalysis(
          prepared,
          {
            side: effectiveSide,
            outputLanguage,
            clientKind: window.matchMedia('(max-width: 767px)').matches
              ? 'MOBILE_WEB'
              : 'DESKTOP_WEB',
          },
          lang,
          token,
        ),
      )
    },
    onSuccess: (accepted, input) => {
      void navigate(`/app/analyses/${accepted.analysisId}`, {
        state: { ocrExpected: input.kind === 'file' || input.kind === 'images' },
      })
    },
  })

  // The landing page's "try a sample contract" arrives here and starts the sample at once.
  const autoSample = (location.state as { autoSample?: boolean } | null)?.autoSample === true
  const autoStarted = useRef(false)
  const { mutate: startMutation } = start
  useEffect(() => {
    if (autoSample && !autoStarted.current) {
      autoStarted.current = true
      startMutation({ kind: 'sample' })
    }
  }, [autoSample, startMutation])

  const thumbnails = useMemo(
    () => images.map((file) => ({ file, url: URL.createObjectURL(file) })),
    [images],
  )
  useEffect(
    () => () => {
      thumbnails.forEach(({ url }) => {
        URL.revokeObjectURL(url)
      })
    },
    [thumbnails],
  )

  function errorText(error: UploadError): string {
    return t(`upload.errors.${error}`, {
      mb: formatNumber(MAX_BYTES / (1024 * 1024), language),
      max: formatNumber(error === 'textTooLong' ? MAX_TEXT : MAX_IMAGES, language),
      min: formatNumber(MIN_TEXT, language),
    })
  }

  async function takeFiles(event: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.target.files ?? [])
    event.target.value = ''
    setInvalid(null)
    const first = picked[0]
    if (first === undefined) {
      return
    }
    const kind = classifyFile(first)
    if (kind === 'unsupported') {
      setInvalid('unsupportedType')
    } else if (kind === 'pdf') {
      const problem = validatePdf(first)
      setInvalid(problem)
      if (problem === null) {
        setPdf(first)
        setMethod('file')
      }
    } else if (kind === 'text') {
      if (first.size > MAX_BYTES) {
        setInvalid('tooLarge')
      } else {
        setText(await first.text())
        setMethod('text')
      }
    } else {
      setImages((current) => [...current, ...picked.filter((f) => classifyFile(f) === 'image')])
      setMethod('photos')
    }
  }

  function takePhotos(event: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.target.files ?? [])
    event.target.value = ''
    setInvalid(null)
    setImages((current) => [...current, ...picked])
  }

  function move(index: number, delta: number) {
    setImages((current) => {
      const next = [...current]
      const [item] = next.splice(index, 1)
      if (item !== undefined) {
        next.splice(index + delta, 0, item)
      }
      return next
    })
  }

  function submit() {
    setInvalid(null)
    let problem: UploadError | null
    let input: AnalysisInput
    if (method === 'file') {
      problem = pdf === null ? 'noInput' : validatePdf(pdf)
      input = { kind: 'file', file: pdf ?? new File([], '') }
    } else if (method === 'photos') {
      problem = validateImages(images)
      input = { kind: 'images', files: images }
    } else {
      problem = validateText(text)
      input = { kind: 'text', text: text.trim() }
    }
    if (problem !== null) {
      setInvalid(problem)
      return
    }
    start.mutate(input)
  }

  const busy = start.isPending
  const textLength = text.trim().length

  return (
    <div className="mx-auto max-w-3xl px-(--gutter) py-10">
      <h1 className="text-3xl font-bold">{t('upload.title')}</h1>
      <p className="mt-2 text-lg text-muted-foreground">{t('upload.intro')}</p>

      <GlassCard className="mt-6 flex items-start gap-3 p-4">
        <Icon name="lock" className="mt-1 shrink-0 text-verified" />
        <div>
          <h2 className="font-semibold">{t('upload.privacyTitle')}</h2>
          <p className="text-muted-foreground">{t('upload.privacyNote')}</p>
        </div>
      </GlassCard>

      {recorded !== null && (
        <p
          role="note"
          className="mt-4 rounded-lg border border-uncertain/40 bg-uncertain/10 px-4 py-3"
        >
          {t('upload.recordedOnly')}
        </p>
      )}
      {isStaticReplay() && (
        <p
          role="note"
          className="mt-2 rounded-lg border border-rejected/40 bg-rejected/10 px-4 py-3"
        >
          {t('replay.uploadNote')}
        </p>
      )}

      <div role="group" aria-label={t('upload.steps.label')} className="upload-steps mt-8">
        <section aria-labelledby="step-contract" className="upload-step">
          <StepHeading
            id="step-contract"
            n={1}
            title={t('upload.steps.contract')}
            hint={t('upload.steps.contractHint')}
          />
          <GlassCard depth="still" className="p-5">
            <fieldset disabled={recorded !== null || busy} className="min-w-0 border-0 p-0">
              <legend className="sr-only">{t('upload.method')}</legend>
              <Tabs
                idPrefix="upload"
                label={t('upload.method')}
                value={method}
                onChange={(id) => {
                  setMethod(id as Method)
                  setInvalid(null)
                }}
                items={[
                  {
                    id: 'file',
                    label: t('upload.methodFile'),
                    panel: (
                      <div className="rounded-xl border-2 border-dashed border-border p-6 text-center">
                        <Icon name="file" size={32} className="mx-auto text-muted-foreground" />
                        <p className="mt-2 font-semibold">{pdf?.name ?? t('upload.dropTitle')}</p>
                        <p className="text-muted-foreground">
                          {t('upload.dropHint', {
                            mb: formatNumber(MAX_BYTES / (1024 * 1024), language),
                          })}
                        </p>
                        <input
                          ref={fileInput}
                          type="file"
                          accept="application/pdf,.pdf,image/jpeg,image/png,text/plain,.txt"
                          className="sr-only"
                          tabIndex={-1}
                          aria-hidden="true"
                          onChange={(event) => void takeFiles(event)}
                        />
                        <Button
                          variant="secondary"
                          className="mt-4"
                          onClick={() => fileInput.current?.click()}
                        >
                          <Icon name="upload" />
                          {t('upload.chooseFile')}
                        </Button>
                      </div>
                    ),
                  },
                  {
                    id: 'photos',
                    label: t('upload.methodPhotos'),
                    panel: (
                      <div>
                        <p className="text-muted-foreground">
                          {t('upload.photosHint', { max: formatNumber(MAX_IMAGES, language) })}
                        </p>
                        <input
                          ref={cameraInput}
                          type="file"
                          accept="image/*"
                          capture="environment"
                          className="sr-only"
                          tabIndex={-1}
                          aria-hidden="true"
                          onChange={takePhotos}
                        />
                        <input
                          ref={photoInput}
                          type="file"
                          accept="image/jpeg,image/png"
                          multiple
                          className="sr-only"
                          tabIndex={-1}
                          aria-hidden="true"
                          onChange={takePhotos}
                        />
                        <div className="mt-3 flex flex-wrap gap-2">
                          <Button variant="secondary" onClick={() => cameraInput.current?.click()}>
                            <Icon name="camera" />
                            {t('upload.takePhoto')}
                          </Button>
                          <Button variant="outline" onClick={() => photoInput.current?.click()}>
                            <Icon name="upload" />
                            {t('upload.choosePhotos')}
                          </Button>
                        </div>
                        {thumbnails.length > 0 && (
                          <ol className="mt-4 space-y-2">
                            {thumbnails.map(({ file, url }, index) => {
                              const n = formatNumber(index + 1, language)
                              return (
                                <li
                                  key={`${file.name}-${url}`}
                                  className="flex items-center gap-3 rounded-lg border border-border p-2"
                                >
                                  <img
                                    src={url}
                                    alt=""
                                    className="decor size-12 shrink-0 rounded object-cover"
                                  />
                                  <span className="min-w-0 flex-1 truncate">
                                    <strong>{t('upload.pageN', { n })}</strong> · {file.name}
                                  </span>
                                  <Button
                                    variant="ghost"
                                    size="icon"
                                    disabled={index === 0}
                                    aria-label={t('upload.moveUp', { n })}
                                    onClick={() => {
                                      move(index, -1)
                                    }}
                                  >
                                    <Icon name="chevronDown" className="rotate-180" />
                                  </Button>
                                  <Button
                                    variant="ghost"
                                    size="icon"
                                    disabled={index === thumbnails.length - 1}
                                    aria-label={t('upload.moveDown', { n })}
                                    onClick={() => {
                                      move(index, 1)
                                    }}
                                  >
                                    <Icon name="chevronDown" />
                                  </Button>
                                  <Button
                                    variant="ghost"
                                    size="icon"
                                    aria-label={t('upload.remove', { n })}
                                    onClick={() => {
                                      setImages((current) => current.filter((_, i) => i !== index))
                                    }}
                                  >
                                    <Icon name="trash" />
                                  </Button>
                                </li>
                              )
                            })}
                          </ol>
                        )}
                      </div>
                    ),
                  },
                  {
                    id: 'text',
                    label: t('upload.methodText'),
                    panel: (
                      <div>
                        <label htmlFor="contract-text" className="font-semibold">
                          {t('upload.textLabel')}
                        </label>
                        <textarea
                          id="contract-text"
                          value={text}
                          onChange={(event) => {
                            setText(event.target.value)
                          }}
                          rows={10}
                          placeholder={t('upload.textPlaceholder')}
                          className={cx(textareaClass, 'mt-2')}
                        />
                        <p className="mt-1 text-sm text-muted-foreground">
                          {t('upload.textHint', {
                            min: formatNumber(MIN_TEXT, language),
                            count: formatNumber(textLength, language),
                          })}
                        </p>
                      </div>
                    ),
                  },
                ]}
              />
            </fieldset>
          </GlassCard>
        </section>

        <section aria-labelledby="step-side" className="upload-step">
          <StepHeading id="step-side" n={2} title={t('upload.steps.side')} />
          <fieldset className="min-w-0 border-0 p-0" disabled={recorded !== null || busy}>
            <legend className="sr-only">{t('upload.sideLabel')}</legend>
            <div className="grid gap-3 sm:grid-cols-2">
              {(['WORKER', 'EMPLOYER'] as const).map((value) => (
                <label
                  key={value}
                  className={cx(
                    'flex min-h-11 cursor-pointer items-start gap-3 rounded-xl border p-4',
                    effectiveSide === value ? 'border-primary bg-primary/10' : 'border-border',
                  )}
                >
                  <input
                    type="radio"
                    name="side"
                    value={value}
                    checked={effectiveSide === value}
                    onChange={() => {
                      setSide(value)
                    }}
                    className="mt-1.5 size-5 accent-[var(--primary)]"
                  />
                  <span>
                    <span className="block font-semibold">
                      {t(value === 'WORKER' ? 'upload.sideWorker' : 'upload.sideEmployer')}
                    </span>
                    <span className="text-sm text-muted-foreground">
                      {t(value === 'WORKER' ? 'upload.sideWorkerHint' : 'upload.sideEmployerHint')}
                    </span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        </section>

        <section aria-labelledby="step-start" className="upload-step">
          <StepHeading
            id="step-start"
            n={3}
            title={t('upload.steps.start')}
            hint={t('upload.steps.startHint')}
          />
          {invalid !== null && (
            <p
              role="alert"
              className="mt-4 rounded-lg border border-rejected/40 bg-rejected/10 px-4 py-3 font-medium text-rejected"
            >
              {errorText(invalid)}
            </p>
          )}
          {start.isError && (
            <div className="mt-4">
              <ErrorState error={start.error} />
            </div>
          )}

          <div className="flex flex-col gap-3 sm:flex-row">
            <Button
              size="lg"
              busy={busy && start.variables.kind !== 'sample'}
              disabled={recorded !== null || busy}
              onClick={submit}
            >
              {busy && start.variables.kind !== 'sample'
                ? t('upload.submitting')
                : t('upload.submit')}
            </Button>
            <Button
              size="lg"
              variant="secondary"
              busy={busy && start.variables.kind === 'sample'}
              disabled={busy}
              onClick={() => {
                setInvalid(null)
                start.mutate({ kind: 'sample' })
              }}
            >
              {t('upload.sample')}
            </Button>
          </div>
          <p className="mt-2 text-sm text-muted-foreground">{t('upload.sampleHint')}</p>
        </section>
      </div>
    </div>
  )
}

/** The number, title and hint of one upload step; the number is a lit node on the rail. */
function StepHeading({
  id,
  n,
  title,
  hint,
}: {
  id: string
  n: number
  title: string
  hint?: string
}) {
  const { t, i18n } = useTranslation()
  const language: Language = i18n.language === 'en' ? 'en' : 'bn'
  return (
    <div className="mb-4 flex items-start gap-3">
      <span
        aria-hidden="true"
        className="step-node font-ui flex size-10 shrink-0 items-center justify-center rounded-full border-2 border-primary bg-background font-bold text-primary"
      >
        {formatNumber(n, language)}
      </span>
      <div>
        <p className="sr-only">{t('upload.steps.number', { n: formatNumber(n, language) })}</p>
        <h2 id={id} className="text-2xl leading-tight">
          {title}
        </h2>
        {hint !== undefined && <p className="mt-1 text-muted-foreground">{hint}</p>}
      </div>
    </div>
  )
}
