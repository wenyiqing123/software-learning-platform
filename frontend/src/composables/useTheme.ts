import { computed, ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

const THEME_STORAGE_KEY = 'slp-theme'

function readStoredTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'light'
  try {
    const saved = window.localStorage.getItem(THEME_STORAGE_KEY)
    if (saved === 'dark' || saved === 'light') return saved
  } catch {
    // 忽略存储不可用时的异常
  }
  return 'light'
}

const theme = ref<ThemeMode>(readStoredTheme())

export function applyThemeToDocument(mode: ThemeMode) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.dataset.theme = mode
  root.style.colorScheme = mode
}

export function initTheme() {
  const mode = readStoredTheme()
  theme.value = mode
  applyThemeToDocument(mode)
  return mode
}

export function setTheme(mode: ThemeMode) {
  theme.value = mode
  applyThemeToDocument(mode)
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, mode)
    } catch {
      // 忽略私有模式存储限制
    }
  }
}

export function toggleTheme() {
  const next: ThemeMode = theme.value === 'dark' ? 'light' : 'dark'
  setTheme(next)
  return next
}

export function useTheme() {
  const isDark = computed(() => theme.value === 'dark')
  return {
    theme,
    isDark,
    initTheme,
    setTheme,
    toggleTheme,
  }
}
