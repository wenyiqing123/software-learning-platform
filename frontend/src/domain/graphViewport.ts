import type { Graph } from '@antv/g6'

export type GraphViewportInstance = Graph & {
  resize?: () => void
  fitView?: (options?: { when?: 'overflow' | 'always' }, animation?: { duration?: number }) => Promise<void> | void
  focusElement?: (id: string, options?: Record<string, unknown>) => void
  setOptions?: (options: Record<string, unknown>) => void
}

/** 根据当前是否全屏返回兼顾悬浮控件安全区的内边距 */
export function graphViewportPadding(fullscreen: boolean): [number, number, number, number] {
  return fullscreen ? [92, 56, 76, 56] : [56, 48, 60, 48]
}

/** 绑定容器尺寸变化与全屏切换时的 G6 画布重绘和视口自适应 */
export function bindGraphViewportSync(
  host: HTMLElement,
  getGraph: () => GraphViewportInstance | null,
  isFullscreen: () => boolean,
) {
  let lastWidth = host.clientWidth
  let lastHeight = host.clientHeight
  let timer = 0
  let raf = 0

  function syncFit(animate = false) {
    window.clearTimeout(timer)
    window.cancelAnimationFrame(raf)
    timer = window.setTimeout(() => {
      raf = window.requestAnimationFrame(() => {
        const instance = getGraph()
        if (!instance) return
        instance.setOptions?.({ padding: graphViewportPadding(isFullscreen()) })
        instance.resize?.()
        void instance.fitView?.({ when: 'always' }, { duration: animate ? 180 : 0 })
      })
    }, 60)
  }

  let observer: ResizeObserver | null = null
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect
      if (!rect || rect.width <= 0 || rect.height <= 0) return
      if (Math.abs(rect.width - lastWidth) <= 4 && Math.abs(rect.height - lastHeight) <= 4) return
      lastWidth = rect.width
      lastHeight = rect.height
      syncFit(false)
    })
    observer.observe(host)
  }

  return {
    syncFit,
    destroy() {
      window.clearTimeout(timer)
      window.cancelAnimationFrame(raf)
      observer?.disconnect()
      observer = null
    },
  }
}
