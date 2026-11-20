import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { resetSystemStatus, useSystemStatus } from './useSystemStatus'

const api = vi.hoisted(() => ({ systemStatus: vi.fn(), dependencies: vi.fn() }))
vi.mock('../api/client', () => ({ apiError: vi.fn(() => '依赖状态不可用'), learningApi: api }))

const status = { overallStatus: 'UP', checkedAt: '2026-11-20T03:00:00Z', dependencies: [] }

describe('useSystemStatus session isolation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.setItem('slp-access-token', 'token-a')
    api.dependencies.mockResolvedValue({ configured: true, pythonKnowledgeStatus: 'UP' })
  })
  afterEach(() => {
    localStorage.clear()
    resetSystemStatus()
  })

  it('does not apply an old response after status state is reset', async () => {
    let resolveStatus!: (value: typeof status) => void
    api.systemStatus.mockReturnValue(new Promise(resolve => { resolveStatus = resolve }))
    const wrapper = mount({ setup: () => useSystemStatus(), template: '<div />' })
    await nextTick()

    resetSystemStatus()
    resolveStatus(status)
    await flushPromises()

    expect(wrapper.vm.status).toBe(null)
    wrapper.unmount()
  })
})
