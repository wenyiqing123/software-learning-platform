import { describe, expect, it, vi, beforeEach } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useFormBaseline, useUnsavedChanges } from './useUnsavedChanges'

describe('useUnsavedChanges and useFormBaseline', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  function createTestRouter(component: any) {
    return createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component },
        { path: '/other', component: { template: '<div>Other Page</div>' } },
      ],
    })
  }

  const RouterRoot = defineComponent({
    template: '<router-view />',
  })

  it('allows route leave without prompt when form is clean', async () => {
    const confirmSpy = vi.fn().mockResolvedValue(false)
    const TestComponent = defineComponent({
      setup() {
        const isDirty = ref(false)
        useUnsavedChanges(isDirty, { confirmLeave: confirmSpy })
        return { isDirty }
      },
      template: '<div>Clean Form</div>',
    })

    const router = createTestRouter(TestComponent)
    await router.push('/')
    await router.isReady()
    const wrapper = mount(RouterRoot, { global: { plugins: [router] } })

    await router.push('/other')
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/other')
    wrapper.unmount()
  })

  it('blocks route leave when dirty and user rejects confirmation', async () => {
    const confirmSpy = vi.fn().mockResolvedValue(false)
    const TestComponent = defineComponent({
      setup() {
        const isDirty = ref(true)
        useUnsavedChanges(isDirty, { confirmLeave: confirmSpy })
        return { isDirty }
      },
      template: '<div>Dirty Form</div>',
    })

    const router = createTestRouter(TestComponent)
    await router.push('/')
    await router.isReady()
    const wrapper = mount(RouterRoot, { global: { plugins: [router] } })

    await router.push('/other')
    expect(confirmSpy).toHaveBeenCalled()
    // 依然留在原路径
    expect(router.currentRoute.value.path).toBe('/')
    wrapper.unmount()
  })

  it('allows route leave when dirty and user accepts confirmation', async () => {
    const confirmSpy = vi.fn().mockResolvedValue(true)
    const TestComponent = defineComponent({
      setup() {
        const isDirty = ref(true)
        useUnsavedChanges(isDirty, { confirmLeave: confirmSpy })
        return { isDirty }
      },
      template: '<div>Dirty Form</div>',
    })

    const router = createTestRouter(TestComponent)
    await router.push('/')
    await router.isReady()
    const wrapper = mount(RouterRoot, { global: { plugins: [router] } })

    await router.push('/other')
    expect(confirmSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/other')
    wrapper.unmount()
  })

  it('installs beforeunload listener and removes it on unmount', () => {
    const addEventSpy = vi.spyOn(window, 'addEventListener')
    const removeEventSpy = vi.spyOn(window, 'removeEventListener')

    const isDirty = ref(true)
    const TestComponent = defineComponent({
      setup() {
        useUnsavedChanges(isDirty)
        return {}
      },
      template: '<div>Unload Component</div>',
    })

    const wrapper = mount(TestComponent)
    expect(addEventSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))

    // 触发 beforeunload 事件测试同步 returnValue
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)

    wrapper.unmount()
    expect(removeEventSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
  })

  it('useFormBaseline tracks deep changes and resets baseline', () => {
    const form = ref({
      title: '初始标题',
      tags: ['A', 'B'],
      meta: { count: 1 },
    })

    const { isDirty, resetBaseline } = useFormBaseline(() => form.value)
    expect(isDirty()).toBe(false)

    // 修改内部属性
    form.value.title = '新标题'
    expect(isDirty()).toBe(true)

    // 恢复原值
    form.value.title = '初始标题'
    expect(isDirty()).toBe(false)

    // 数组变动
    form.value.tags.push('C')
    expect(isDirty()).toBe(true)

    // 重置 baseline
    resetBaseline()
    expect(isDirty()).toBe(false)
  })

  it('confirmIfDirty helper can be called programmatically for tab switching', async () => {
    let dirtyVal = true
    const confirmSpy = vi.fn().mockImplementation(() => Promise.resolve(false))

    const TestComponent = defineComponent({
      setup() {
        const hook = useUnsavedChanges(() => dirtyVal, { confirmLeave: confirmSpy })
        return { hook }
      },
      template: '<div>Tab Component</div>',
    })

    const wrapper = mount(TestComponent)
    const canLeaveRejected = await wrapper.vm.hook.confirmIfDirty()
    expect(canLeaveRejected).toBe(false)
    expect(confirmSpy).toHaveBeenCalled()

    // 设为 clean 状态
    dirtyVal = false
    confirmSpy.mockClear()
    const canLeaveAllowed = await wrapper.vm.hook.confirmIfDirty()
    expect(canLeaveAllowed).toBe(true)
    expect(confirmSpy).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
