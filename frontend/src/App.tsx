import { useEffect, useState } from 'react'
import { getMeta, type Meta } from './api'

// Phase 1 shell: shows that the frontend can reach the backend and database.
// The upload, risk cards and comparison screens arrive in phase 7.
export default function App() {
  const [meta, setMeta] = useState<Meta | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getMeta().then(setMeta).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="text-3xl font-bold">ShromikAI</h1>
      <p className="mt-2 text-lg text-stone-600">
        চাকরির চুক্তির ঝুঁকি খুঁজে বের করে, প্রতিটা আইনের ধারা যাচাই করে।
      </p>

      <section className="mt-8 rounded-lg border border-stone-200 bg-white p-5">
        <h2 className="font-semibold">সিস্টেমের অবস্থা</h2>
        {error && (
          <p className="mt-2 text-red-700">
            Backend-এর সাথে যোগাযোগ হচ্ছে না ({error})। Backend চালু আছে কিনা দেখুন।
          </p>
        )}
        {!error && !meta && <p className="mt-2 text-stone-500">লোড হচ্ছে…</p>}
        {meta && (
          <dl className="mt-3 grid grid-cols-2 gap-y-2 text-sm">
            <dt className="text-stone-500">Backend</dt>
            <dd>চালু (v{meta.version})</dd>
            <dt className="text-stone-500">আইন</dt>
            <dd>{meta.corpus.acts}টি</dd>
            <dt className="text-stone-500">ধারা (বর্তমান)</dt>
            <dd>{meta.corpus.currentSections}টি</dd>
            <dt className="text-stone-500">Embedding সম্পন্ন</dt>
            <dd>{meta.corpus.embeddedSections}টি</dd>
          </dl>
        )}
      </section>

      <p className="mt-6 text-xs text-stone-500">
        এটা আইনি পরামর্শ না। গুরুত্বপূর্ণ সিদ্ধান্তের আগে একজন আইনজীবীর সাথে কথা বলুন।
      </p>
    </main>
  )
}
