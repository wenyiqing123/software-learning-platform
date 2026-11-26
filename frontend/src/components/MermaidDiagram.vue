<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, CircleAlert, Code2, Copy, Download, GitBranch, Maximize2, RotateCcw, X, ZoomIn, ZoomOut } from '@lucide/vue'
import { useTheme } from '../composables/useTheme'

const props = defineProps<{
  code: string
}>()

type ViewMode = 'diagram' | 'source'

const viewMode = ref<ViewMode>('diagram')
const loading = ref(true)
const syntaxError = ref(false)
const copyState = ref<'idle' | 'copied' | 'failed'>('idle')
const svgMarkup = ref('')
const zoomOpen = ref(false)
const zoomScale = ref(1)

const inlineContainer = ref<HTMLElement | null>(null)
const zoomContainer = ref<HTMLElement | null>(null)

const { isDark } = useTheme()
let renderSequence = 0
let copyResetTimer: ReturnType<typeof setTimeout> | null = null
let mermaidModulePromise: Promise<typeof import('mermaid')['default']> | null = null

function buildMermaidConfig(dark: boolean) {
  return {
    startOnLoad: false,
    securityLevel: 'strict' as const,
    theme: (dark ? 'dark' : 'base') as 'dark' | 'base',
    themeVariables: dark
      ? {
          background: '#111a18',
          primaryColor: '#183631',
          primaryTextColor: '#e6f0ed',
          primaryBorderColor: '#40b8a8',
          lineColor: '#86a099',
          secondaryColor: '#172320',
          tertiaryColor: '#1e2e2a',
          fontFamily: "'DM Sans','Noto Sans SC','Microsoft YaHei',sans-serif",
        }
      : {
          background: '#ffffff',
          primaryColor: '#e9f3f1',
          primaryTextColor: '#172b27',
          primaryBorderColor: '#2c7c72',
          lineColor: '#52645f',
          secondaryColor: '#f7f8fa',
          tertiaryColor: '#eef2f3',
          fontFamily: "'DM Sans','Noto Sans SC','Microsoft YaHei',sans-serif",
        },
  }
}

async function getMermaid() {
  if (!mermaidModulePromise) {
    mermaidModulePromise = import('mermaid').then((mod) => mod.default)
  }
  const instance = await mermaidModulePromise
  instance.initialize(buildMermaidConfig(isDark.value))
  return instance
}

function sanitizeSvgToNode(rawSvg: string): SVGElement | null {
  if (!rawSvg || !rawSvg.includes('<svg')) return null
  const parser = new DOMParser()
  const doc = parser.parseFromString(rawSvg, 'image/svg+xml')
  const root = doc.documentElement
  if (!root || root.tagName.toLowerCase() !== 'svg' || doc.querySelector('parsererror')) {
    return null
  }
  // 防御性清理任何潜在脚本元素与内联事件句柄
  root.querySelectorAll('script, foreignObject iframe, foreignObject object').forEach(el => el.remove())
  for (const el of [root, ...root.querySelectorAll('*')]) {
    for (const attr of [...el.attributes]) {
      const name = attr.name.toLowerCase()
      const val = attr.value.trim().toLowerCase()
      if (name.startsWith('on') || ((name === 'href' || name === 'xlink:href') && val.startsWith('javascript:'))) {
        el.removeAttribute(attr.name)
      }
    }
  }
  return root as unknown as SVGElement
}

function mountSvgInto(container: HTMLElement | null, rawSvg: string) {
  if (!container || !rawSvg) return
  const safeSvg = sanitizeSvgToNode(rawSvg)
  if (!safeSvg) {
    container.replaceChildren()
    return
  }
  container.replaceChildren(document.importNode(safeSvg, true))
}

async function renderDiagram() {
  const source = props.code.trim()
  const seq = ++renderSequence
  if (!source) {
    loading.value = false
    syntaxError.value = true
    viewMode.value = 'source'
    svgMarkup.value = ''
    return
  }

  loading.value = true
  syntaxError.value = false
  const renderId = `mermaid-svg-${seq}-${Math.random().toString(36).slice(2, 8)}`

  try {
    const mermaid = await getMermaid()
    if (typeof mermaid.parse === 'function') {
      await mermaid.parse(source)
    }
    const result = await mermaid.render(renderId, source)
    if (seq !== renderSequence) return

    const safeNode = sanitizeSvgToNode(result.svg)
    if (!safeNode) {
      throw new Error('Rendered SVG invalid')
    }
    svgMarkup.value = new XMLSerializer().serializeToString(safeNode)
    syntaxError.value = false
    viewMode.value = 'diagram'
    await nextTick()
    if (seq === renderSequence) {
      mountSvgInto(inlineContainer.value, svgMarkup.value)
      if (zoomOpen.value) {
        mountSvgInto(zoomContainer.value, svgMarkup.value)
      }
    }
  } catch {
    if (seq !== renderSequence) return
    document.getElementById(renderId)?.remove()
    document.getElementById(`d${renderId}`)?.remove()
    svgMarkup.value = ''
    syntaxError.value = true
    viewMode.value = 'source'
  } finally {
    if (seq === renderSequence) {
      loading.value = false
    }
  }
}

async function switchMode(next: ViewMode) {
  if (next === 'diagram' && syntaxError.value) return
  viewMode.value = next
  if (next === 'diagram' && svgMarkup.value) {
    await nextTick()
    mountSvgInto(inlineContainer.value, svgMarkup.value)
  }
}

async function copySource() {
  try {
    await window.navigator.clipboard.writeText(props.code)
    copyState.value = 'copied'
  } catch {
    copyState.value = 'failed'
  }
  if (copyResetTimer) clearTimeout(copyResetTimer)
  copyResetTimer = setTimeout(() => {
    copyState.value = 'idle'
  }, 1800)
}

async function openZoom() {
  if (!svgMarkup.value || syntaxError.value) return
  zoomScale.value = 1
  zoomOpen.value = true
  await nextTick()
  mountSvgInto(zoomContainer.value, svgMarkup.value)
}

function closeZoom() {
  zoomOpen.value = false
  zoomScale.value = 1
}

function adjustZoom(delta: number) {
  zoomScale.value = Number(Math.min(2.6, Math.max(0.4, zoomScale.value + delta)).toFixed(2))
}

function resetZoom() {
  zoomScale.value = 1
}

function downloadSvg() {
  if (!svgMarkup.value || syntaxError.value) return
  const blob = new Blob([svgMarkup.value], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'mermaid-diagram.svg'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && zoomOpen.value) {
    event.preventDefault()
    event.stopImmediatePropagation()
    closeZoom()
  }
}

watch(() => props.code, () => {
  void renderDiagram()
})

watch(isDark, () => {
  void renderDiagram()
})

onMounted(() => {
  window.addEventListener('keydown', onKeydown, true)
  void renderDiagram()
})

onBeforeUnmount(() => {
  renderSequence++
  if (copyResetTimer) clearTimeout(copyResetTimer)
  window.removeEventListener('keydown', onKeydown, true)
})
</script>

<template>
  <section class="mermaid-card" data-testid="mermaid-diagram-card" aria-label="Mermaid 交互图表">
    <header class="mermaid-toolbar">
      <div class="mermaid-title-group">
        <GitBranch :size="14" aria-hidden="true" />
        <span class="mermaid-badge">Mermaid 图表</span>
        <span v-if="copyState === 'copied'" class="mermaid-status-text" role="status">已复制源码</span>
        <span v-else-if="copyState === 'failed'" class="mermaid-status-text" role="status">复制失败</span>
      </div>
      <div class="mermaid-actions">
        <div class="mermaid-mode-tabs" role="group" aria-label="图表视图切换">
          <button
            type="button"
            class="mermaid-tab-btn"
            :class="{ active: viewMode === 'diagram' }"
            :disabled="syntaxError"
            data-testid="mermaid-tab-diagram"
            @click="switchMode('diagram')"
          >
            图表
          </button>
          <button
            type="button"
            class="mermaid-tab-btn"
            :class="{ active: viewMode === 'source' }"
            data-testid="mermaid-tab-source"
            @click="switchMode('source')"
          >
            <Code2 :size="13" aria-hidden="true" />
            <span>源码</span>
          </button>
        </div>
        <button
          type="button"
          class="mermaid-tool-btn"
          title="复制 Mermaid 源码"
          aria-label="复制 Mermaid 源码"
          data-testid="mermaid-copy-btn"
          @click="copySource"
        >
          <Check v-if="copyState === 'copied'" :size="14" />
          <Copy v-else :size="14" />
        </button>
        <button
          type="button"
          class="mermaid-tool-btn"
          title="弹窗放大查看"
          aria-label="放大查看 Mermaid 图表"
          :disabled="syntaxError || !svgMarkup"
          data-testid="mermaid-zoom-btn"
          @click="openZoom"
        >
          <Maximize2 :size="14" />
        </button>
        <button
          type="button"
          class="mermaid-tool-btn"
          title="下载 SVG"
          aria-label="下载 SVG 矢量图"
          :disabled="syntaxError || !svgMarkup"
          data-testid="mermaid-download-btn"
          @click="downloadSvg"
        >
          <Download :size="14" />
        </button>
      </div>
    </header>

    <div v-if="syntaxError" class="mermaid-fallback-banner" role="alert" data-testid="mermaid-syntax-fallback">
      <CircleAlert :size="15" aria-hidden="true" />
      <span>图表语法暂无法渲染，已切换为源码视图</span>
    </div>

    <div v-if="viewMode === 'diagram' && !syntaxError" class="mermaid-canvas-wrap">
      <div v-if="loading" class="mermaid-loading" role="status">
        <span class="spinner"></span>
        <span>正在加载并渲染 Mermaid 图表…</span>
      </div>
      <div v-show="!loading" ref="inlineContainer" class="mermaid-svg-stage" data-testid="mermaid-svg-stage"></div>
    </div>

    <pre v-else class="mermaid-source-pre" data-testid="mermaid-source-view"><code class="language-mermaid">{{ code }}</code></pre>

    <Teleport to="body">
      <div v-if="zoomOpen" class="mermaid-zoom-backdrop" data-testid="mermaid-zoom-modal" @click.self="closeZoom">
        <div class="mermaid-zoom-card" role="dialog" aria-modal="true" aria-label="Mermaid 图表放大预览">
          <header class="mermaid-zoom-header">
            <div class="mermaid-title-group">
              <GitBranch :size="15" aria-hidden="true" />
              <strong>Mermaid 图表详情视图</strong>
              <span class="mermaid-zoom-scale">{{ Math.round(zoomScale * 100) }}%</span>
            </div>
            <div class="mermaid-zoom-controls">
              <button type="button" class="mermaid-tool-btn" aria-label="缩小图表" title="缩小" @click="adjustZoom(-0.2)">
                <ZoomOut :size="15" />
              </button>
              <button type="button" class="mermaid-tool-btn" aria-label="重置缩放" title="重置为 100%" data-testid="mermaid-zoom-reset" @click="resetZoom">
                <RotateCcw :size="14" />
              </button>
              <button type="button" class="mermaid-tool-btn" aria-label="放大图表" title="放大" data-testid="mermaid-zoom-in" @click="adjustZoom(0.2)">
                <ZoomIn :size="15" />
              </button>
              <button type="button" class="mermaid-tool-btn" aria-label="下载 SVG" title="下载 SVG" @click="downloadSvg">
                <Download :size="15" />
              </button>
              <button type="button" class="mermaid-tool-btn" aria-label="关闭放大视图" title="关闭" @click="closeZoom">
                <X :size="16" />
              </button>
            </div>
          </header>
          <div class="mermaid-zoom-stage">
            <div ref="zoomContainer" class="mermaid-zoom-canvas" :style="{ transform: `scale(${zoomScale})` }"></div>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.mermaid-card{margin:14px 0;min-width:0;border:1px solid var(--border-default);border-radius:var(--radius-md);background:var(--surface);overflow:hidden;box-shadow:var(--shadow-xs)}
.mermaid-toolbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px 12px;padding:8px 12px;background:var(--surface-soft);border-bottom:1px solid var(--border-soft)}
.mermaid-title-group,.mermaid-actions,.mermaid-zoom-controls{display:flex;align-items:center;gap:8px;min-width:0}
.mermaid-title-group{color:var(--text-muted);font-size:12px}
.mermaid-badge{color:var(--text);font-weight:600;font-family:ui-monospace,monospace}
.mermaid-status-text{color:var(--accent-strong);font-size:12px;font-weight:500}
.mermaid-mode-tabs{display:inline-flex;gap:2px;padding:2px;background:var(--surface-tint);border-radius:var(--radius-sm)}
.mermaid-tab-btn{display:inline-flex;align-items:center;gap:4px;min-height:26px;padding:2px 9px;color:var(--text-muted);background:transparent;border:0;border-radius:6px;font-size:12px;font-weight:500;cursor:pointer}
.mermaid-tab-btn.active{color:var(--accent-strong);background:var(--surface);box-shadow:var(--shadow-xs);font-weight:600}
.mermaid-tab-btn:disabled{opacity:.45;cursor:not-allowed}
.mermaid-tool-btn{display:grid;place-items:center;width:28px;height:28px;min-height:28px;padding:0;color:var(--text-muted);background:transparent;border:1px solid transparent;border-radius:6px;cursor:pointer;transition:color var(--motion),background-color var(--motion),border-color var(--motion)}
.mermaid-tool-btn:hover:not(:disabled){color:var(--accent-strong);background:var(--surface);border-color:var(--border-default)}
.mermaid-tool-btn:disabled{opacity:.4;cursor:not-allowed}
.mermaid-fallback-banner{display:flex;align-items:center;gap:8px;padding:9px 14px;color:var(--warning);background:var(--warning-soft);border-bottom:1px solid var(--border-soft);font-size:12.5px}
.mermaid-canvas-wrap{padding:16px;background:var(--surface);overflow-x:auto}
.mermaid-loading{display:flex;align-items:center;justify-content:center;gap:10px;min-height:96px;color:var(--text-muted);font-size:13px}
.mermaid-svg-stage{display:flex;justify-content:center;min-width:0}
.mermaid-svg-stage :deep(svg){max-width:100%;height:auto}
.mermaid-source-pre{margin:0;padding:14px;background:var(--surface-soft);overflow-x:auto;font-size:13px;line-height:1.6}
.mermaid-source-pre .language-mermaid{font-family:ui-monospace,monospace}
.mermaid-zoom-backdrop{position:fixed;inset:0;z-index:1250;display:grid;place-items:center;padding:20px;background:rgb(23 43 39 / .44)}
.mermaid-zoom-card{width:min(1120px,96vw);height:min(84vh,820px);display:grid;grid-template-rows:auto minmax(0,1fr);background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-md);overflow:hidden}
.mermaid-zoom-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 16px;background:var(--surface-soft);border-bottom:1px solid var(--border-soft)}
.mermaid-zoom-scale{padding:2px 8px;color:var(--accent-strong);background:var(--accent-soft);border-radius:var(--radius-pill);font-size:11.5px;font-weight:600;font-variant-numeric:tabular-nums}
.mermaid-zoom-stage{overflow:auto;display:grid;place-items:center;padding:24px;background:var(--surface-soft)}
.mermaid-zoom-canvas{transform-origin:center center;transition:transform var(--motion)}
.mermaid-zoom-canvas :deep(svg){display:block;max-width:none;height:auto}
</style>
