import * as echarts from 'echarts'

export interface ChartSurfacePalette {
  text: string
  muted: string
  line: string
  surface: string
}

export function getChartSurfacePalette(isDark = false): ChartSurfacePalette {
  return isDark
    ? {
        text: '#e6f0ed',
        muted: '#adc2bc',
        line: '#293c37',
        surface: '#172320',
      }
    : {
        text: '#172b27',
        muted: '#52645f',
        line: '#dce5e3',
        surface: '#ffffff',
      }
}

/**
 * 构建支持屏幕阅读器与无障碍网纹图案的 ECharts ARIA 配置。
 * 在柱状图等分类系列开启 decal 后，色盲或灰度打印仍可通过纹理区分系列。
 */
export function buildAriaConfig(description: string, enableDecal = true) {
  return {
    enabled: true,
    label: {
      description,
    },
    decal: {
      show: enableDecal,
    },
  }
}

/**
 * 构建论文级高清（2x DPI）图片导出工具栏配置，显式指定导出文件名与主题底色。
 */
export function buildSaveAsImageToolbox(exportName: string, isDark = false) {
  const palette = getChartSurfacePalette(isDark)
  return {
    show: true,
    right: 4,
    top: -2,
    itemSize: 13,
    iconStyle: {
      borderColor: palette.muted,
    },
    emphasis: {
      iconStyle: {
        borderColor: palette.text,
      },
    },
    feature: {
      saveAsImage: {
        show: true,
        type: 'png' as const,
        name: exportName,
        title: '导出高清 PNG',
        pixelRatio: 2,
        backgroundColor: palette.surface,
        excludeComponents: ['toolbox'],
      },
    },
  }
}

/**
 * 探测当前运行环境是否具备可用 2D Canvas 上下文（防止 JSDOM / SSR 环境报错）。
 */
export function canRenderCanvas(): boolean {
  if (typeof document === 'undefined') return false
  try {
    const probe = document.createElement('canvas')
    return Boolean(typeof probe.getContext === 'function' && probe.getContext('2d'))
  } catch {
    return false
  }
}

export function safeDisposeChart(chart: echarts.ECharts | null): void {
  if (!chart) return
  try {
    chart.dispose()
  } catch {
    // 忽略无 Canvas 环境下的销毁异常
  }
}

export function safeInitChart(el: HTMLElement | null): echarts.ECharts | null {
  if (!el || !canRenderCanvas()) return null
  try {
    return echarts.init(el)
  } catch {
    return null
  }
}

/**
 * 将已挂载的 ECharts 实例导出为 2x DPI PNG 下载（自动剔除内部工具箱图标，适配论文与汇报插图）。
 */
export function exportChartAsPng(
  chart: Pick<echarts.ECharts, 'getDataURL'> | null | undefined,
  filename: string,
  isDark = false,
): boolean {
  if (!chart || typeof document === 'undefined') return false
  try {
    const dataUrl = chart.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: getChartSurfacePalette(isDark).surface,
      excludeComponents: ['toolbox'],
    })
    if (!dataUrl || !dataUrl.startsWith('data:image/')) return false
    const link = document.createElement('a')
    const safeName = (filename.trim() || 'chart-export').replace(/[^a-zA-Z0-9\u4e00-\u9fa5_-]+/g, '-')
    link.href = dataUrl
    link.download = safeName.endsWith('.png') ? safeName : `${safeName}.png`
    document.body.appendChild(link)
    link.click()
    link.remove()
    return true
  } catch {
    return false
  }
}
