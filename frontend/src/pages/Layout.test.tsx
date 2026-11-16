import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderApp } from '../test/render'
import { Layout } from './Layout'

function at(path: string) {
  return renderApp(
    <Routes>
      <Route element={<Layout />}>
        <Route path="*" element={<p>page</p>} />
      </Route>
    </Routes>,
    path,
  )
}

describe('Layout', () => {
  it('NFR-08 app pages get the ambient glow backdrop', async () => {
    const { container } = at('/login')
    await screen.findByText('page')
    expect(container.querySelector('[data-backdrop]')).not.toBeNull()
  })

  it('FR-68 the landing keeps its own scene and gets no backdrop', async () => {
    const { container } = at('/')
    await screen.findByText('page')
    expect(container.querySelector('[data-backdrop]')).toBeNull()
  })

  it('NFR-08 the landing follows the chosen theme like every other page', async () => {
    const { container } = at('/')
    await screen.findByText('page')
    expect(container.querySelector('[data-surface="ink"]')).toBeNull()
  })
})
