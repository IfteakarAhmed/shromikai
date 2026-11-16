import { Fragment } from 'react'

/**
 * Text for a display heading in Tiro Bangla (C1 decision): a trailing danda is dropped, because
 * Tiro Bangla draws it with a wide gap before it; a danda inside the heading, where the sentence
 * needs it, is set in Hind Siliguri. Other text, English full stops included, is left as it is.
 */
export function DisplayText({ text }: { text: string }) {
  const trimmed = text.replace(/\s*।\s*$/, '')
  const parts = trimmed.split('।')
  return (
    <>
      {parts.map((part, index) => (
        <Fragment key={index}>
          {index > 0 && <span className="font-body">।</span>}
          {part}
        </Fragment>
      ))}
    </>
  )
}
