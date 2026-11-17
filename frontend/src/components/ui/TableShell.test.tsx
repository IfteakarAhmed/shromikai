import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TableShell } from './TableShell'

describe('TableShell', () => {
  function shell() {
    return render(
      <TableShell label="Usage per day">
        <caption className="sr-only">Usage per day</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>today</td>
          </tr>
        </tbody>
      </TableShell>,
    )
  }

  it('FR-68 puts the table on one glass card that never leans, with a scroll region of its own', () => {
    const { container } = shell()
    const card = container.querySelector('.depth-card')
    expect(card).not.toBeNull()
    expect(card).not.toHaveAttribute('data-tilt')
    const region = screen.getByRole('region', { name: 'Usage per day' })
    expect(region).toHaveClass('overflow-x-auto')
    expect(card).toContainElement(region)
  })

  it('NFR-08 the scroll region is reachable by keyboard', () => {
    shell()
    expect(screen.getByRole('region', { name: 'Usage per day' })).toHaveAttribute('tabindex', '0')
  })

  it('FR-68 the table carries the shared themed class and keeps its caption for screen readers', () => {
    shell()
    const table = screen.getByRole('table', { name: 'Usage per day' })
    expect(table).toHaveClass('data-table')
    expect(table.querySelector('caption')).toHaveClass('sr-only')
  })

  it('FR-68 a wide table can say how wide it needs to be', () => {
    const { container } = render(
      <TableShell label="Wide" minWidth="40rem">
        <tbody>
          <tr>
            <td>x</td>
          </tr>
        </tbody>
      </TableShell>,
    )
    expect(container.querySelector('table')?.getAttribute('style')).toContain('min-width: 40rem')
  })

  it('NFR-08 the scroll region is its own containing block, so visually hidden text in a cell cannot widen the page on a phone', () => {
    shell()
    // Without `relative`, an absolutely positioned .sr-only span in a cell escapes the region's
    // clip and Chrome on a phone widens the layout viewport (seen on /admin/api-keys and /docs/data).
    expect(screen.getByRole('region', { name: 'Usage per day' })).toHaveClass('relative')
  })
})
