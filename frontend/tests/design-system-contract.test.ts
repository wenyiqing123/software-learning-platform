import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const style = (name: string) => readFileSync(resolve(process.cwd(), 'src', name), 'utf8')

describe('design system source contracts', () => {
  it('keeps theme tokens in one authoritative root and maps Element Plus surfaces', () => {
    const css = style('styles-design-system.css')

    expect(css.match(/:root\s*\{/g)).toHaveLength(1)
    expect(css.replace(/\s+/g, '')).toContain('--el-color-primary:var(--accent)')
    expect(css.replace(/\s+/g, '')).toContain('--el-table-header-bg-color:var(--surface-tint)')
  })

  it('keeps core visual roles token-driven and free of important overrides', () => {
    const files = ['styles-design-system.css', 'styles.css', 'styles-graph.css', 'styles-learning.css']
      .map(style)
      .join('\n')

    expect(files).not.toMatch(/!important/i)
    expect(files.replace(/\s+/g, '')).toContain('font-variant-numeric:tabular-nums')
    expect(files.replace(/\s+/g, '')).toContain('150mscubic-bezier(0.2,0,0,1)')
  })

  it('does not override the shared desktop page heading with a second global theme', () => {
    expect(style('styles.css')).toMatch(/\.section-heading h1,\.page-heading h1\{[^}]*font-size:24px/)
    expect(style('styles-design-system.css')).not.toMatch(/\.page-heading h1\{[^}]*font-size:(?:2[5-9]|[3-9]\d)px/)
  })
})
