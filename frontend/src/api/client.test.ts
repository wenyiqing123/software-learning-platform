import { describe, expect, it } from 'vitest'
import { shouldRefreshAfterUnauthorized } from './client'

describe('401 refresh eligibility', () => {
  it('refreshes a protected current-user request', () => {
    expect(shouldRefreshAfterUnauthorized('/auth/me')).toBe(true)
  })

  it.each(['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'])('%s does not recursively trigger refresh', (url) => {
    expect(shouldRefreshAfterUnauthorized(url)).toBe(false)
  })
})
