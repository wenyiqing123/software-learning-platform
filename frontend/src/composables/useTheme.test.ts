import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { initTheme, setTheme, toggleTheme, useTheme } from './useTheme'

describe('useTheme', () => {
  beforeEach(() => {
    window.localStorage.clear()
    delete document.documentElement.dataset.theme
    document.documentElement.style.colorScheme = ''
  })

  afterEach(() => {
    setTheme('light')
  })

  it('defaults to light theme and persists toggles to localStorage and html data-theme', () => {
    expect(initTheme()).toBe('light')
    const { theme, isDark } = useTheme()
    expect(theme.value).toBe('light')
    expect(isDark.value).toBe(false)
    expect(document.documentElement.dataset.theme).toBe('light')

    const next = toggleTheme()
    expect(next).toBe('dark')
    expect(isDark.value).toBe(true)
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(window.localStorage.getItem('slp-theme')).toBe('dark')

    expect(toggleTheme()).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(window.localStorage.getItem('slp-theme')).toBe('light')
  })

  it('restores saved dark preference from localStorage', () => {
    window.localStorage.setItem('slp-theme', 'dark')
    expect(initTheme()).toBe('dark')
    const { isDark } = useTheme()
    expect(isDark.value).toBe(true)
    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})
