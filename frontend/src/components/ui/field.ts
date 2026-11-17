/**
 * The one look of a form field (C4): a themed surface, an edge that reaches 3:1 in both themes, a
 * placeholder, hover, a glowing focus ring and an error edge. The rules live in `index.css` under
 * `.field`; a page adds only its own spacing (`mt-1`).
 */
export const fieldClass = 'field block min-h-11 w-full rounded-lg px-3 py-2'

/** A field that is only as wide as its content, such as a date or a small select. */
export const fieldInlineClass = 'field block min-h-11 rounded-lg px-3'

/** A multi-line field. */
export const textareaClass = 'field block w-full rounded-lg p-3'
