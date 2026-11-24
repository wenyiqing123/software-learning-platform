import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const style = (name: string) => readFileSync(resolve(process.cwd(), 'src', name), 'utf8')

describe('design system source contracts', () => {
  it('keeps theme tokens in one authoritative root and maps Element Plus surfaces', () => {
    const css = style('styles-design-system.css')
    const compactCss = css.replace(/\s+/g, '')

    expect(css.match(/:root\s*\{/g)).toHaveLength(1)
    expect(compactCss).toContain('--el-color-primary:var(--accent)')
    expect(compactCss).toContain('--el-color-primary-rgb:15,102,94')
    expect(compactCss).toContain('--el-table-header-bg-color:var(--surface-tint)')
    expect(compactCss).toContain('--el-button-bg-color:var(--accent)')
    expect(compactCss).toContain('--el-checkbox-checked-bg-color:var(--accent)')
    expect(compactCss).toContain('--el-select-input-focus-border-color:var(--accent)')
    expect(compactCss).toContain('--el-input-border-radius:8px')
    expect(compactCss).toContain('--el-dialog-border-radius:12px')
    expect(compactCss).not.toContain('--el-select-input-focus-font-size')
  })

  it('binds native baseline scopes to app-shell and login-page without double border on el-input', () => {
    const css = style('styles-design-system.css')
    expect(css).toMatch(/:where\(:is\(\.app-shell,\s*\.login-page\)\s+button:not\(\.el-button\)/)
    expect(css).toMatch(/:where\(:is\(\.app-shell,\s*\.login-page\)\s+table\)/)
    expect(css).not.toMatch(/\.el-input__wrapper\s*\{[^}]*border:/)
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

  it('assigns shell layout and responsive media rules exclusively to styles.css', () => {
    const styles = style('styles.css')
    const designSystem = style('styles-design-system.css')

    expect(styles).toMatch(/@media\s*\(max-width:\s*900px\)\s*\{[\s\S]*\.app-shell[\s\S]*\.sidebar[\s\S]*\}/)
    expect(styles).toMatch(/@media\s*\(max-width:\s*620px\)\s*\{[\s\S]*\.sidebar[\s\S]*\.primary-nav[\s\S]*\}/)
    expect(styles).toMatch(/\.section-heading h1,\.page-heading h1\{[^}]*font-size:22px/)

    expect(designSystem).not.toMatch(/@media\s*\(max-width:\s*900px\)\s*\{[^}]*\.app-shell/)
    expect(designSystem).not.toMatch(/@media\s*\(max-width:\s*620px\)\s*\{[^}]*\.sidebar/)
    expect(designSystem).not.toContain('.course-switch:hover')
  })

  it('assigns generic feedback, spinners, and motion ownership to design system, keeping styles-learning scoped to learning domain', () => {
    const designSystem = style('styles-design-system.css')
    const learning = style('styles-learning.css')

    expect(designSystem).toContain('.feedback-state')
    expect(designSystem).toContain('.empty-state')
    expect(designSystem).toContain('.spinner')
    expect(designSystem).toContain('@keyframes spin')
    expect(designSystem).toContain('@media (prefers-reduced-motion:reduce)')

    expect(learning).not.toContain('.feedback-state')
    expect(learning).not.toContain('.empty-state')
    expect(learning).not.toContain('@keyframes spin')
    expect(learning).not.toContain('@media (prefers-reduced-motion:reduce)')
    expect(learning).toContain('.quiz-layout')
    expect(learning).toContain('.answer-option')
  })
})
