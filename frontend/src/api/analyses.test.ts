import { afterEach, describe, expect, it, vi } from 'vitest'
import { startAnalysis, startCompare } from './analyses'

afterEach(() => {
  vi.unstubAllGlobals()
})

function capture() {
  const fetchMock = vi.fn(() =>
    Promise.resolve(new Response(JSON.stringify({ analysisId: 'x' }), { status: 202 })),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const OPTIONS = { side: 'EMPLOYER', outputLanguage: 'en', clientKind: 'MOBILE_WEB' } as const

function sentForm(fetchMock: ReturnType<typeof capture>): FormData {
  const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
  expect(call[1].body).toBeInstanceOf(FormData)
  return call[1].body as FormData
}

describe('startAnalysis', () => {
  it('FR-69 sends the sample as an options part only, with sample true and no content part', async () => {
    const fetchMock = capture()

    await startAnalysis({ kind: 'sample' }, OPTIONS, 'bn', 'tok')

    const form = sentForm(fetchMock)
    const options = JSON.parse(await (form.get('options') as Blob).text()) as Record<
      string,
      unknown
    >
    expect(options).toMatchObject({ side: 'EMPLOYER', outputLanguage: 'en', sample: true })
    expect([...form.keys()]).toEqual(['options'])
  })

  it('FR-69 sends pasted text as a text part, never as the options', async () => {
    const fetchMock = capture()

    await startAnalysis({ kind: 'text', text: 'ধারা ১' }, OPTIONS, 'bn', null)

    const form = sentForm(fetchMock)
    expect(await (form.get('text') as Blob).text()).toBe('ধারা ১')
    const options = JSON.parse(await (form.get('options') as Blob).text()) as Record<
      string,
      unknown
    >
    expect(options['sample']).toBe(false)
    expect(JSON.stringify(options)).not.toContain('ধারা')
  })

  it('FR-69 sends photos as repeated images parts in page order', async () => {
    const fetchMock = capture()
    const files = [
      new File(['1'], 'p1.jpg', { type: 'image/jpeg' }),
      new File(['2'], 'p2.jpg', { type: 'image/jpeg' }),
    ]

    await startAnalysis({ kind: 'images', files }, OPTIONS, 'bn', 'tok')

    const form = sentForm(fetchMock)
    expect(form.getAll('images').map((f) => (f as File).name)).toEqual(['p1.jpg', 'p2.jpg'])
  })

  it('T-23 puts the token in the Authorization header and never in the URL', async () => {
    const fetchMock = capture()

    await startAnalysis({ kind: 'sample' }, OPTIONS, 'bn', 'secret-token')

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).not.toContain('secret-token')
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer secret-token')
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
  })
})

describe('startCompare', () => {
  it('FR-59 sends the sample or the text as JSON, never both', async () => {
    const fetchMock = capture()

    await startCompare({ kind: 'sample' }, OPTIONS, 'bn', 'tok')
    await startCompare({ kind: 'text', text: 'abc' }, OPTIONS, 'bn', 'tok')

    const bodies = fetchMock.mock.calls.map(
      (call) => JSON.parse((call as unknown as [string, RequestInit])[1].body as string) as object,
    )
    expect(bodies[0]).toEqual({ sample: true, side: 'EMPLOYER', outputLanguage: 'en' })
    expect(bodies[1]).toEqual({ text: 'abc', side: 'EMPLOYER', outputLanguage: 'en' })
  })
})
