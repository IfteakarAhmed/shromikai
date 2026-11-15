import { useSyncExternalStore } from 'react'

function synthesis(): SpeechSynthesis | null {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
    ? window.speechSynthesis
    : null
}

function subscribe(onChange: () => void): () => void {
  const engine = synthesis()
  engine?.addEventListener('voiceschanged', onChange)
  return () => {
    engine?.removeEventListener('voiceschanged', onChange)
  }
}

/** Whether this device has a voice for a language ("bn", "en"); read-aloud is hidden without one. */
export function hasVoice(language: string): boolean {
  return (
    synthesis()
      ?.getVoices()
      .some((voice) => voice.lang.toLowerCase().startsWith(language)) ?? false
  )
}

export function useHasVoice(language: string): boolean {
  return useSyncExternalStore(
    subscribe,
    () => hasVoice(language),
    () => false,
  )
}

/** Reads text aloud with a voice of the language; returns a function that stops it. */
export function speak(text: string, language: string, onEnd: () => void): () => void {
  const engine = synthesis()
  if (engine === null) {
    onEnd()
    return () => undefined
  }
  const utterance = new SpeechSynthesisUtterance(text)
  const voice = engine.getVoices().find((v) => v.lang.toLowerCase().startsWith(language))
  if (voice !== undefined) {
    utterance.voice = voice
    utterance.lang = voice.lang
  }
  utterance.onend = onEnd
  utterance.onerror = onEnd
  engine.cancel()
  engine.speak(utterance)
  return () => {
    engine.cancel()
  }
}
