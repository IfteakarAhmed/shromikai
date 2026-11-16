import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Markdown } from './markdown'
import { sectionOf, slug } from './markdownText'

describe('Markdown (FR-74)', () => {
  it('FR-74 renders headings, lists, tables and code as elements', () => {
    render(
      <Markdown
        baseLevel={2}
        source={[
          '# Title',
          '',
          'A paragraph with `code`, **bold** and *italic*.',
          '',
          '- one',
          '- two',
          '',
          '1. first',
          '2. second',
          '',
          '| Name | Value |',
          '| --- | --- |',
          '| a | 1 |',
          '| b | 2 |',
          '',
          '```java',
          'int x = 1;',
          '```',
        ].join('\n')}
      />,
    )

    expect(screen.getByRole('heading', { level: 2, name: 'Title' })).toBeInTheDocument()
    expect(screen.getByText('code').tagName).toBe('CODE')
    expect(screen.getByText('bold').tagName).toBe('STRONG')
    expect(screen.getAllByRole('listitem')).toHaveLength(4)
    const table = screen.getByRole('table')
    expect(within(table).getAllByRole('row')).toHaveLength(3)
    expect(within(table).getByRole('columnheader', { name: 'Name' })).toBeInTheDocument()
    expect(screen.getByText('int x = 1;').closest('pre')).toHaveAttribute('tabindex', '0')
  })

  it('FR-74 R-02 never turns text into HTML: tags, scripts and javascript links stay text', () => {
    const { container } = render(
      <Markdown
        source={[
          '<script>window.hacked = true</script>',
          '',
          '<img src=x onerror="window.hacked = true">',
          '',
          '[click](javascript:window.hacked=true) and [site](https://example.test/page)',
        ].join('\n')}
      />,
    )

    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText(/<script>window.hacked = true<\/script>/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'click' })).not.toBeInTheDocument()
    const link = screen.getByRole('link', { name: /site/ })
    expect(link).toHaveAttribute('href', 'https://example.test/page')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect((window as unknown as { hacked?: boolean }).hacked).toBeUndefined()
  })

  it('FR-74 links to a relative file become plain text, an in-page anchor stays a link', () => {
    render(<Markdown source={'See [the spec](spec/api.md) and [below](#second-part).'} />)

    expect(screen.queryByRole('link', { name: 'the spec' })).not.toBeInTheDocument()
    expect(screen.getByText(/the spec/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'below' })).toHaveAttribute('href', '#second-part')
  })

  it('FR-74 gives every heading an id so an anchor can reach it', () => {
    render(<Markdown source={'## Second part'} />)

    expect(screen.getByRole('heading', { name: 'Second part' })).toHaveAttribute(
      'id',
      'second-part',
    )
  })

  it('FR-74 replaces a diagram source with a note instead of showing it', () => {
    render(
      <Markdown
        mermaidNote="The diagram is drawn above."
        source={['```mermaid', 'flowchart TB', 'A --> B', '```'].join('\n')}
      />,
    )

    expect(screen.getByText('The diagram is drawn above.')).toBeInTheDocument()
    expect(screen.queryByText(/flowchart/)).not.toBeInTheDocument()
  })

  it('FR-74 nests a list item under its parent', () => {
    render(<Markdown source={['- parent', '  - child', '- sibling'].join('\n')} />)

    const lists = screen.getAllByRole('list')
    expect(lists).toHaveLength(2)
    expect(within(lists[0] as HTMLElement).getByText('child')).toBeInTheDocument()
  })
})

describe('sectionOf and slug', () => {
  const doc = [
    '# Doc',
    '',
    '## 1. One',
    'text one',
    '### 1.1 Sub',
    'sub text',
    '## 2. Two',
    'text two',
  ].join('\n')

  it('FR-74 cuts a section up to the next heading of the same or a higher level', () => {
    expect(sectionOf(doc, '## 1. One')).toBe('## 1. One\ntext one\n### 1.1 Sub\nsub text')
    expect(sectionOf(doc, '### 1.1 Sub')).toBe('### 1.1 Sub\nsub text')
    expect(sectionOf(doc, '## 9. Missing')).toBe('')
  })

  it('FR-74 makes ids from headings, keeping Bangla letters', () => {
    expect(slug('10. Privacy pipeline')).toBe('10-privacy-pipeline')
    expect(slug('শ্রম আইন')).toBe('শ্রম-আইন')
  })
})
