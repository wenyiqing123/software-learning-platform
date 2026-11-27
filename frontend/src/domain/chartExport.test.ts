import { describe, expect, it, vi } from 'vitest'
import { buildAriaConfig, buildSaveAsImageToolbox, exportChartAsPng, safeInitChart } from './chartExport'

describe('chartExport utilities', () => {
  it('builds accessible aria decal config and 2x DPI PNG toolbox options', () => {
    const aria = buildAriaConfig('实验柱状图', true)
    expect(aria.enabled).toBe(true)
    expect(aria.decal.show).toBe(true)

    const lightBox = buildSaveAsImageToolbox('slp-experiment-comparison', false)
    expect(lightBox.feature.saveAsImage.pixelRatio).toBe(2)
    expect(lightBox.feature.saveAsImage.name).toBe('slp-experiment-comparison')
    expect(lightBox.feature.saveAsImage.backgroundColor).toBe('#ffffff')

    const darkBox = buildSaveAsImageToolbox('slp-experiment-comparison', true)
    expect(darkBox.feature.saveAsImage.backgroundColor).toBe('#172320')
  })

  it('degrades safely when chart instance is missing or canvas is unavailable in JSDOM', () => {
    const host = document.createElement('div')
    expect(safeInitChart(host)).toBeNull()
    expect(exportChartAsPng(null, 'missing')).toBe(false)
    expect(
      exportChartAsPng({ getDataURL: () => 'invalid-data-url' }, 'broken'),
    ).toBe(false)
  })

  it('triggers anchor download with sanitized png filename when getDataURL succeeds', () => {
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const ok = exportChartAsPng(
      { getDataURL: () => 'data:image/png;base64,iVBORw0KGgo=' },
      'slp report / fig 1',
      true,
    )
    expect(ok).toBe(true)
    expect(clickSpy).toHaveBeenCalledOnce()
    clickSpy.mockRestore()
  })
})
