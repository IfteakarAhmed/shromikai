import { TableShell } from '../../components/ui/TableShell'
import { Fragment, type ReactNode } from 'react'
import { slug } from './markdownText'

/**
 * A small Markdown renderer for the project's own documents (FR-74). It builds React elements and
 * never sets inner HTML, so a tag or a script in a document stays text. It reads what the docs use:
 * headings, paragraphs, nested lists, tables, code fences, quotes, rules, and the inline forms
 * code, bold, italic and links. A link to a file in the repository is shown as text (the file is
 * not served here); only http, https and in-page links are links.
 */

interface ListNode {
  ordered: boolean
  items: { text: string; children: ListNode | null }[]
}

type Block =
  | { kind: 'heading'; level: number; text: string }
  | { kind: 'code'; lang: string; text: string }
  | { kind: 'table'; header: string[]; rows: string[][] }
  | { kind: 'list'; list: ListNode }
  | { kind: 'quote'; text: string }
  | { kind: 'rule' }
  | { kind: 'paragraph'; text: string }

const LIST_ITEM = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/
const TABLE_SEPARATOR = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/

function splitRow(line: string): string[] {
  const cells: string[] = []
  let current = ''
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '')
  for (let i = 0; i < trimmed.length; i += 1) {
    const ch = trimmed.charAt(i)
    if (ch === '\\' && trimmed.charAt(i + 1) === '|') {
      current += '|'
      i += 1
    } else if (ch === '|') {
      cells.push(current.trim())
      current = ''
    } else {
      current += ch
    }
  }
  cells.push(current.trim())
  return cells
}

function buildList(
  flat: { indent: number; ordered: boolean; text: string }[],
  from: number,
): [ListNode, number] {
  const first = flat[from]
  const indent = first?.indent ?? 0
  const node: ListNode = { ordered: first?.ordered ?? false, items: [] }
  let i = from
  while (i < flat.length) {
    const item = flat[i]
    if (item === undefined || item.indent < indent) {
      break
    }
    if (item.indent > indent) {
      const last = node.items[node.items.length - 1]
      const [child, next] = buildList(flat, i)
      if (last !== undefined) {
        last.children = child
      }
      i = next
      continue
    }
    node.items.push({ text: item.text, children: null })
    i += 1
  }
  return [node, i]
}

function parseBlocks(source: string): Block[] {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const blocks: Block[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i] ?? ''
    if (line.trim() === '') {
      i += 1
      continue
    }
    const fence = /^```\s*([\w-]*)/.exec(line)
    if (fence !== null) {
      const body: string[] = []
      i += 1
      while (i < lines.length && !(lines[i] ?? '').startsWith('```')) {
        body.push(lines[i] ?? '')
        i += 1
      }
      i += 1
      blocks.push({ kind: 'code', lang: fence[1] ?? '', text: body.join('\n') })
      continue
    }
    const heading = /^(#{1,6})\s+(.*?)\s*#*\s*$/.exec(line)
    if (heading !== null) {
      blocks.push({ kind: 'heading', level: heading[1]?.length ?? 1, text: heading[2] ?? '' })
      i += 1
      continue
    }
    if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(line)) {
      blocks.push({ kind: 'rule' })
      i += 1
      continue
    }
    if (line.includes('|') && TABLE_SEPARATOR.test(lines[i + 1] ?? '')) {
      const header = splitRow(line)
      const rows: string[][] = []
      i += 2
      while (i < lines.length && (lines[i] ?? '').includes('|') && (lines[i] ?? '').trim() !== '') {
        rows.push(splitRow(lines[i] ?? ''))
        i += 1
      }
      blocks.push({ kind: 'table', header, rows })
      continue
    }
    if (line.startsWith('>')) {
      const body: string[] = []
      while (i < lines.length && (lines[i] ?? '').startsWith('>')) {
        body.push((lines[i] ?? '').replace(/^>\s?/, ''))
        i += 1
      }
      blocks.push({ kind: 'quote', text: body.join(' ') })
      continue
    }
    if (LIST_ITEM.test(line)) {
      const flat: { indent: number; ordered: boolean; text: string }[] = []
      while (i < lines.length) {
        const current = lines[i] ?? ''
        const item = LIST_ITEM.exec(current)
        if (item !== null) {
          flat.push({
            indent: (item[1] ?? '').length,
            ordered: /\d/.test(item[2] ?? ''),
            text: item[3] ?? '',
          })
          i += 1
        } else if (/^\s{2,}\S/.test(current) && flat.length > 0) {
          const last = flat[flat.length - 1]
          if (last !== undefined) {
            last.text += ` ${current.trim()}`
          }
          i += 1
        } else if (current.trim() === '' && LIST_ITEM.test(lines[i + 1] ?? '')) {
          i += 1
        } else {
          break
        }
      }
      blocks.push({ kind: 'list', list: buildList(flat, 0)[0] })
      continue
    }
    const text: string[] = []
    while (i < lines.length) {
      const current = lines[i] ?? ''
      if (
        current.trim() === '' ||
        /^(#{1,6}\s|```|>)/.test(current) ||
        LIST_ITEM.test(current) ||
        (current.includes('|') && TABLE_SEPARATOR.test(lines[i + 1] ?? ''))
      ) {
        break
      }
      text.push(current.trim())
      i += 1
    }
    blocks.push({ kind: 'paragraph', text: text.join(' ') })
  }
  return blocks
}

const INLINE = /(`[^`]+`)|(\[[^\]]+\]\([^)]*\))|(\*\*[^*]+\*\*)|(\*[^*\s][^*]*\*)|(<br\s*\/?>)/g

function inline(text: string, key: string): ReactNode[] {
  const out: ReactNode[] = []
  let last = 0
  let n = 0
  for (const match of text.matchAll(INLINE)) {
    const at = match.index
    if (at > last) {
      out.push(text.slice(last, at))
    }
    const token = match[0]
    const id = `${key}-${String(n)}`
    n += 1
    if (token.startsWith('`')) {
      out.push(
        <code
          key={id}
          className="rounded bg-secondary px-1 py-0.5 text-[0.92em] [overflow-wrap:anywhere]"
        >
          {token.slice(1, -1)}
        </code>,
      )
    } else if (token.startsWith('[')) {
      const parts = /^\[([^\]]+)\]\(([^)\s]*)(?:\s+"[^"]*")?\)$/.exec(token)
      const label = parts?.[1] ?? token
      const href = parts?.[2] ?? ''
      if (/^https?:\/\//i.test(href) || href.startsWith('#')) {
        out.push(
          <a
            key={id}
            href={href}
            rel={href.startsWith('#') ? undefined : 'noopener noreferrer'}
            className="underline underline-offset-4"
          >
            {inline(label, id)}
          </a>,
        )
      } else {
        out.push(<Fragment key={id}>{inline(label, id)}</Fragment>)
      }
    } else if (token.startsWith('**')) {
      out.push(<strong key={id}>{inline(token.slice(2, -2), id)}</strong>)
    } else if (token.startsWith('*')) {
      out.push(<em key={id}>{inline(token.slice(1, -1), id)}</em>)
    } else {
      out.push(<br key={id} />)
    }
    last = at + token.length
  }
  if (last < text.length) {
    out.push(text.slice(last))
  }
  return out
}

function renderList(list: ListNode, key: string): ReactNode {
  const Tag = list.ordered ? 'ol' : 'ul'
  return (
    <Tag
      key={key}
      className={list.ordered ? 'list-decimal space-y-1 ps-6' : 'list-disc space-y-1 ps-6'}
    >
      {list.items.map((item, index) => (
        <li key={`${key}-${String(index)}`}>
          {inline(item.text, `${key}-${String(index)}`)}
          {item.children !== null && renderList(item.children, `${key}-${String(index)}c`)}
        </li>
      ))}
    </Tag>
  )
}

interface MarkdownProps {
  source: string
  /** The level of a top-level `#` heading, so the page keeps a single h1. */
  baseLevel?: number
  /** Said in place of a diagram source (a `mermaid` fence). */
  mermaidNote?: string
  /** The accessible name of a table that may scroll sideways. */
  tableLabel?: string
}

export function Markdown({
  source,
  baseLevel = 2,
  mermaidNote = '',
  tableLabel = '',
}: MarkdownProps) {
  const blocks = parseBlocks(source)
  return (
    <div className="space-y-4 leading-relaxed">
      {blocks.map((block, index) => {
        const key = `b${String(index)}`
        switch (block.kind) {
          case 'heading': {
            const level = Math.min(6, Math.max(1, block.level - 1 + baseLevel))
            const Tag = `h${String(level)}` as 'h2'
            const size = level <= 2 ? 'text-2xl' : level === 3 ? 'text-xl' : 'text-lg'
            return (
              <Tag
                key={key}
                id={slug(block.text)}
                className={`${size} mt-6 scroll-mt-24 font-bold`}
              >
                {inline(block.text, key)}
              </Tag>
            )
          }
          case 'code':
            if (block.lang === 'mermaid') {
              return mermaidNote === '' ? null : (
                <p
                  key={key}
                  role="note"
                  className="rounded-lg border border-border bg-secondary/60 p-3"
                >
                  {mermaidNote}
                </p>
              )
            }
            return (
              <pre
                key={key}
                tabIndex={0}
                dir="ltr"
                className="overflow-x-auto rounded-lg border border-border bg-secondary/60 p-3 text-sm"
              >
                <code>{block.text}</code>
              </pre>
            )
          case 'table':
            return (
              <TableShell
                key={key}
                label={tableLabel === '' ? undefined : tableLabel}
                minWidth="max-content"
              >
                <thead>
                  <tr className="border-b border-border">
                    {block.header.map((cell, c) => (
                      <th key={`${key}h${String(c)}`} scope="col" className="px-3 py-2 text-start">
                        {inline(cell, `${key}h${String(c)}`)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {block.rows.map((row, r) => (
                    <tr
                      key={`${key}r${String(r)}`}
                      className="border-b border-border align-top last:border-b-0"
                    >
                      {row.map((cell, c) => (
                        <td key={`${key}r${String(r)}c${String(c)}`} className="px-3 py-2">
                          {inline(cell, `${key}r${String(r)}c${String(c)}`)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </TableShell>
            )
          case 'list':
            return renderList(block.list, key)
          case 'quote':
            return (
              <blockquote key={key} className="border-s-4 border-border ps-4 text-muted-foreground">
                {inline(block.text, key)}
              </blockquote>
            )
          case 'rule':
            return <hr key={key} className="border-border" />
          case 'paragraph':
            return <p key={key}>{inline(block.text, key)}</p>
        }
      })}
    </div>
  )
}
