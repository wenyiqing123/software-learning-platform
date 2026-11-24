import { computed, onBeforeUnmount, unref, type Ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'

export type DirtySource = Ref<boolean> | (() => boolean) | boolean

export interface UnsavedChangesOptions {
  confirmLeave?: () => Promise<boolean> | boolean
  message?: string
}

export function useUnsavedChanges(
  isDirtySource: DirtySource,
  options: UnsavedChangesOptions = {},
) {
  const defaultMessage = '您有未保存的修改，确定要离开吗？未保存的内容将会丢失。'
  const confirmLeave = options.confirmLeave ?? (() => {
    return window.confirm(options.message || defaultMessage)
  })

  function checkDirty(): boolean {
    if (typeof isDirtySource === 'function') {
      return Boolean(isDirtySource())
    }
    return Boolean(unref(isDirtySource))
  }

  // 1. 浏览器原生关闭/刷新拦截（必须为纯同步处理，禁止异步弹窗）
  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (checkDirty()) {
      event.preventDefault()
      event.returnValue = options.message || defaultMessage
      return event.returnValue
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', handleBeforeUnload)
    onBeforeUnmount(() => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    })
  }

  // 2. Vue Router 路由切换拦截
  // 支持作为顶级 router-view 组件或嵌入式子组件（如 Tab 面板）
  let leaveGuardActive = false

  try {
    const router = useRouter()
    if (router) {
      const removeGuard = router.beforeEach(async (to, from) => {
        if (leaveGuardActive) return true
        if (to.fullPath === from.fullPath) return true
        if (!checkDirty()) return true
        leaveGuardActive = true
        try {
          const ok = await confirmLeave()
          return Boolean(ok)
        } finally {
          leaveGuardActive = false
        }
      })
      onBeforeUnmount(() => {
        removeGuard()
      })
    }
  } catch {
    // 非 router 上下文忽略
  }

  try {
    onBeforeRouteLeave(async () => {
      if (leaveGuardActive) return true
      if (!checkDirty()) return true
      leaveGuardActive = true
      try {
        const ok = await confirmLeave()
        return Boolean(ok)
      } finally {
        leaveGuardActive = false
      }
    })
  } catch {
    // 当非 setup 上下文或未注入 router 时静默忽略
  }

  // 3. 手动离开检查（供组件内部切换 Tab 或选项时复用）
  async function confirmIfDirty(): Promise<boolean> {
    if (!checkDirty()) return true
    const ok = await confirmLeave()
    return Boolean(ok)
  }

  return {
    isDirty: computed(() => checkDirty()),
    confirmIfDirty,
  }
}

/**
 * 辅助表单深度快照 baseline 与 dirty 状态判断
 */
export function useFormBaseline<T extends Record<string, any>>(getValues: (() => T) | Ref<T>) {
  const read = () => (typeof getValues === 'function' ? getValues() : unref(getValues))
  let baseline = JSON.stringify(read())

  const isDirty = () => JSON.stringify(read()) !== baseline

  const resetBaseline = (custom?: T) => {
    baseline = JSON.stringify(custom ?? read())
  }

  return {
    isDirty,
    resetBaseline,
  }
}
