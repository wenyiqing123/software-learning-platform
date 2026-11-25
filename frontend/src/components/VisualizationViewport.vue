<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Box, Expand, LocateFixed, Minimize2, Network, RefreshCw, RotateCcw, Search } from '@lucide/vue'
import ElButton from 'element-plus/es/components/button/index.mjs'
import ElTag from 'element-plus/es/components/tag/index.mjs'
import ElTooltip from 'element-plus/es/components/tooltip/index.mjs'

withDefaults(defineProps<{
  title: string
  description?: string
  mode?: '2d' | '3d'
  status?: string
  loading?: boolean
  error?: string
  unavailable?: boolean
  empty?: boolean
  searchable?: boolean
  searchValue?: string
  searchPlaceholder?: string
}>(), {
  mode: '2d',
  description: '',
  status: 'G6 2D',
  loading: false,
  error: '',
  unavailable: false,
  empty: false,
  searchable: false,
  searchValue: '',
  searchPlaceholder: '搜索节点',
})

const emit = defineEmits<{
  retry: []
  reset: []
  'mode-change': [mode: '2d' | '3d']
  search: [value: string]
  focus: []
  'fullscreen-change': [fullscreen: boolean]
}>()

const viewport = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const usedNativeFullscreen = ref(false)

async function enterFullscreen() {
  if (!viewport.value) return
  if (typeof viewport.value.requestFullscreen === 'function') {
    try {
      await viewport.value.requestFullscreen()
      usedNativeFullscreen.value = true
      isFullscreen.value = true
      return
    } catch {
      // 浏览器禁用或无头环境下平滑回退至页内固定全屏
    }
  }
  usedNativeFullscreen.value = false
  isFullscreen.value = true
}

async function exitFullscreen() {
  if (document.fullscreenElement) {
    try {
      await document.exitFullscreen()
    } catch {
      // 忽略退出全屏异常，直接复位组件状态
    }
  }
  usedNativeFullscreen.value = false
  isFullscreen.value = false
}

async function toggleFullscreen() {
  if (!viewport.value) return
  if (isFullscreen.value || document.fullscreenElement === viewport.value) await exitFullscreen()
  else await enterFullscreen()
}

function handleFullscreenChange() {
  if (!viewport.value) return
  const active = document.fullscreenElement === viewport.value
  if (active) {
    usedNativeFullscreen.value = true
    isFullscreen.value = true
  } else if (usedNativeFullscreen.value) {
    usedNativeFullscreen.value = false
    isFullscreen.value = false
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && isFullscreen.value && !document.fullscreenElement) {
    isFullscreen.value = false
  }
}

watch(isFullscreen, (next) => {
  emit('fullscreen-change', next)
})

onMounted(() => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('keydown', handleKeydown)
  if (viewport.value && document.fullscreenElement === viewport.value) {
    void document.exitFullscreen?.().catch(() => {})
  }
})
</script>

<template>
  <section ref="viewport" class="visualization-viewport" :class="{ 'is-fullscreen': isFullscreen }">
    <header class="visualization-toolbar">
      <div class="visualization-heading">
        <span class="visualization-mark"><Network :size="17" /></span>
        <div>
          <div class="visualization-title-row"><strong>{{ title }}</strong><ElTag size="small" effect="plain">{{ status }}</ElTag></div>
          <p v-if="description">{{ description }}</p>
        </div>
      </div>
      <div v-if="searchable" class="visualization-search"><Search :size="14" /><input :value="searchValue" :placeholder="searchPlaceholder" aria-label="搜索图谱节点" @input="emit('search', ($event.target as HTMLInputElement).value)" @keydown.enter.prevent="emit('search', ($event.target as HTMLInputElement).value)" /></div>
      <div class="visualization-actions" aria-label="图谱工具">
        <ElTooltip content="2D 图谱" :placement="isFullscreen ? 'bottom' : 'top'" :teleported="!isFullscreen">
          <ElButton text :type="mode === '2d' ? 'primary' : 'info'" aria-label="2D 图谱" :aria-pressed="mode === '2d'" @click="emit('mode-change', '2d')"><Network :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="Three.js 3D 图谱" :placement="isFullscreen ? 'bottom' : 'top'" :teleported="!isFullscreen">
          <ElButton text :type="mode === '3d' ? 'primary' : 'info'" aria-label="3D 图谱" :aria-pressed="mode === '3d'" @click="emit('mode-change', '3d')"><Box :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="重置视图" :placement="isFullscreen ? 'bottom' : 'top'" :teleported="!isFullscreen">
          <ElButton text aria-label="重置视图" @click="emit('reset')"><RotateCcw :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="居中当前节点" :placement="isFullscreen ? 'bottom' : 'top'" :teleported="!isFullscreen">
          <ElButton text aria-label="居中当前节点" @click="emit('focus')"><LocateFixed :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip :content="isFullscreen ? '退出全屏' : '全屏图谱'" :placement="isFullscreen ? 'bottom' : 'top'" :teleported="!isFullscreen">
          <ElButton text :type="isFullscreen ? 'primary' : 'info'" :aria-label="isFullscreen ? '退出全屏' : '全屏图谱'" :aria-pressed="isFullscreen" @click="toggleFullscreen"><Minimize2 v-if="isFullscreen" :size="15" /><Expand v-else :size="15" /></ElButton>
        </ElTooltip>
      </div>
    </header>
    <div class="visualization-stage" :class="{ 'has-feedback': loading || error || unavailable || empty }">
      <slot :fullscreen="isFullscreen" />
      <div v-if="loading" class="visualization-state" role="status"><RefreshCw class="spin" :size="23" /><strong>正在加载可视化数据</strong><span>请稍候，正在整理知识节点与关系。</span></div>
      <div v-else-if="error" class="visualization-state visualization-error" role="alert"><Network :size="26" /><strong>可视化数据暂时不可用</strong><span>{{ error }}</span><ElButton type="primary" plain size="small" aria-label="重试图谱" @click="emit('retry')">重新连接</ElButton></div>
      <div v-else-if="unavailable" class="visualization-state visualization-error" role="status"><Network :size="26" /><strong>等待知识服务连接</strong><span>图谱暂未返回数据，恢复连接后才能查看真实知识关系。</span><ElButton plain size="small" aria-label="重试图谱" @click="emit('retry')">检查连接</ElButton></div>
      <div v-else-if="empty" class="visualization-state" role="status"><Network :size="26" /><strong>暂无可显示的知识关系</strong><span>尝试搜索其他课程概念或调整筛选条件。</span></div>
    </div>
    <footer v-if="$slots.footer" class="visualization-footer"><slot name="footer" :fullscreen="isFullscreen" /></footer>
    <div v-if="isFullscreen && $slots.overlay" class="visualization-overlay"><slot name="overlay" :fullscreen="isFullscreen" /></div>
  </section>
</template>

<style scoped>
.visualization-viewport{position:relative;display:grid;grid-template-rows:auto minmax(0,1fr);grid-auto-rows:auto;min-width:0;overflow:hidden;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}
.visualization-viewport.is-fullscreen,.visualization-viewport:fullscreen{position:fixed;inset:0;z-index:1200;display:block;width:100vw;height:100vh;height:100dvh;border:0;border-radius:0;box-shadow:none;background:var(--surface-soft)}
.visualization-toolbar{position:relative;z-index:2;display:flex;flex-wrap:wrap;align-items:center;gap:8px 14px;padding:12px 16px;border-bottom:1px solid var(--border-soft)}
.visualization-viewport.is-fullscreen .visualization-toolbar,.visualization-viewport:fullscreen .visualization-toolbar{position:absolute;top:16px;left:16px;right:16px;z-index:4;padding:0;border-bottom:0;align-items:flex-start;justify-content:space-between;gap:12px;pointer-events:none}
.visualization-heading,.visualization-title-row,.visualization-actions,.visualization-search{display:flex;align-items:center;gap:8px}
.visualization-heading{flex:1 1 190px;min-width:0}
.visualization-viewport.is-fullscreen .visualization-heading,.visualization-viewport:fullscreen .visualization-heading{flex:0 1 auto;max-width:min(560px,calc(100vw - 32px));padding:10px 14px;background:color-mix(in srgb,var(--surface) 88%,transparent);backdrop-filter:blur(12px);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-sm);pointer-events:auto}
.visualization-mark{display:grid;place-items:center;flex:0 0 32px;height:32px;color:var(--accent);background:var(--accent-soft);border-radius:var(--radius-sm)}
.visualization-title-row{flex-wrap:wrap;font-size:14px}
.visualization-heading p{margin:3px 0 0;color:var(--text-muted);font-size:12px;line-height:1.5}
.visualization-search{flex:1 1 140px;min-width:0;padding:0 10px;color:var(--text-muted);background:var(--surface-soft);border:1px solid var(--border-default);border-radius:var(--radius-sm)}
.visualization-viewport.is-fullscreen .visualization-search,.visualization-viewport:fullscreen .visualization-search{flex:0 1 260px;max-width:100%;min-height:46px;background:color-mix(in srgb,var(--surface) 92%,transparent);backdrop-filter:blur(12px);box-shadow:var(--shadow-sm);pointer-events:auto}
.visualization-search input{width:100%;min-height:34px;padding:4px 0;border:0;background:transparent;outline:0;font-size:13px}
.visualization-search:focus-within{border-color:var(--border-focus);box-shadow:var(--focus-ring)}
.visualization-actions{gap:8px;padding:3px;background:var(--surface-tint);border-radius:var(--radius-sm)}
.visualization-viewport.is-fullscreen .visualization-actions,.visualization-viewport:fullscreen .visualization-actions{margin-left:auto;background:color-mix(in srgb,var(--surface) 90%,transparent);backdrop-filter:blur(12px);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-sm);pointer-events:auto}
.visualization-actions :deep(.el-button){width:40px;height:40px;padding:0;margin:0;color:var(--text-muted);border-radius:var(--radius-sm)}
.visualization-actions :deep(.el-button:hover),.visualization-actions :deep(.el-button[aria-pressed=true]){color:var(--accent-strong);background:var(--surface);box-shadow:var(--shadow-xs)}
.visualization-stage{position:relative;display:grid;grid-template-rows:minmax(0,1fr);isolation:isolate;min-height:390px;background:var(--surface-soft) radial-gradient(var(--border-default) 1px,transparent 1px) 0 0 / 20px 20px}
.visualization-viewport.is-fullscreen .visualization-stage,.visualization-viewport:fullscreen .visualization-stage{position:absolute;inset:0;width:100%;height:100%;min-height:100%;z-index:0}
.visualization-stage > :slotted(*){width:100%;height:100%;min-width:0}
.visualization-viewport.is-fullscreen .visualization-stage > :slotted(*),.visualization-viewport:fullscreen .visualization-stage > :slotted(*){width:100%;height:100%;min-height:100%}
.visualization-stage.has-feedback{min-height:240px;background:var(--surface-soft)}
.visualization-viewport.is-fullscreen .visualization-stage.has-feedback,.visualization-viewport:fullscreen .visualization-stage.has-feedback{min-height:100%}
.visualization-state{display:grid;place-items:center;align-content:center;gap:8px;padding:24px;color:var(--text-muted);text-align:center;font-size:13px}
.visualization-stage:has(.visualization-state) > :slotted(*){display:none}
.visualization-state strong{color:var(--text);font-size:16px}
.visualization-state span{max-width:44ch}
.visualization-state svg{color:var(--accent)}
.visualization-error{background:var(--danger-soft)}
.visualization-error :is(strong,svg){color:var(--danger)}
.visualization-footer{padding:10px 16px;border-top:1px solid var(--border-soft)}
.visualization-viewport.is-fullscreen .visualization-footer,.visualization-viewport:fullscreen .visualization-footer{position:absolute;bottom:16px;left:16px;right:auto;z-index:4;max-width:calc(100vw - 32px);padding:8px 14px;border-top:0;background:color-mix(in srgb,var(--surface) 88%,transparent);backdrop-filter:blur(12px);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-sm);pointer-events:auto}
.visualization-overlay{position:absolute;right:16px;bottom:56px;z-index:4;max-width:min(320px,calc(100vw - 32px));pointer-events:auto}
@media(max-width:700px){.visualization-stage{min-height:320px}.visualization-toolbar,.visualization-footer{padding-inline:12px}.visualization-viewport.is-fullscreen .visualization-toolbar,.visualization-viewport:fullscreen .visualization-toolbar{top:10px;left:10px;right:10px}.visualization-viewport.is-fullscreen .visualization-footer,.visualization-viewport:fullscreen .visualization-footer{bottom:10px;left:10px;max-width:calc(100vw - 20px)}.visualization-overlay{right:10px;bottom:62px;max-width:calc(100vw - 20px)}}
</style>
