<script setup lang="ts">
import { ref } from 'vue'
import { Box, Expand, LocateFixed, Network, RefreshCw, RotateCcw, Search } from '@lucide/vue'
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
}>()

const viewport = ref<HTMLElement | null>(null)

async function toggleFullscreen() {
  if (!viewport.value) return
  if (document.fullscreenElement) await document.exitFullscreen()
  else await viewport.value.requestFullscreen?.()
}
</script>

<template>
  <section ref="viewport" class="visualization-viewport">
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
        <ElTooltip content="2D 图谱" placement="top">
          <ElButton text :type="mode === '2d' ? 'primary' : 'info'" aria-label="2D 图谱" :aria-pressed="mode === '2d'" @click="emit('mode-change', '2d')"><Network :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="Three.js 3D 图谱" placement="top">
          <ElButton text :type="mode === '3d' ? 'primary' : 'info'" aria-label="3D 图谱" :aria-pressed="mode === '3d'" @click="emit('mode-change', '3d')"><Box :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="重置视图" placement="top">
          <ElButton text aria-label="重置视图" @click="emit('reset')"><RotateCcw :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="居中当前节点" placement="top">
          <ElButton text aria-label="居中当前节点" @click="emit('focus')"><LocateFixed :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="全屏图谱" placement="top">
          <ElButton text aria-label="全屏图谱" @click="toggleFullscreen"><Expand :size="15" /></ElButton>
        </ElTooltip>
      </div>
    </header>
    <div class="visualization-stage" :class="{ 'has-feedback': loading || error || unavailable || empty }">
      <slot />
      <div v-if="loading" class="visualization-state" role="status"><RefreshCw class="spin" :size="23" /><strong>正在加载可视化数据</strong><span>请稍候，正在整理知识节点与关系。</span></div>
      <div v-else-if="error" class="visualization-state visualization-error" role="alert"><Network :size="26" /><strong>可视化数据暂时不可用</strong><span>{{ error }}</span><ElButton type="primary" plain size="small" aria-label="重试图谱" @click="emit('retry')">重新连接</ElButton></div>
      <div v-else-if="unavailable" class="visualization-state visualization-error" role="status"><Network :size="26" /><strong>等待知识服务连接</strong><span>图谱暂未返回数据，恢复连接后才能查看真实知识关系。</span><ElButton plain size="small" aria-label="重试图谱" @click="emit('retry')">检查连接</ElButton></div>
      <div v-else-if="empty" class="visualization-state" role="status"><Network :size="26" /><strong>暂无可显示的知识关系</strong><span>尝试搜索其他课程概念或调整筛选条件。</span></div>
    </div>
    <footer v-if="$slots.footer" class="visualization-footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.visualization-viewport{display:grid;min-width:0;overflow:hidden;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}
.visualization-viewport:fullscreen{height:100vh;width:100vw;border:0;border-radius:0}
.visualization-toolbar{position:relative;z-index:2;display:flex;flex-wrap:wrap;align-items:center;gap:8px 14px;padding:12px 16px;border-bottom:1px solid var(--border-soft)}
.visualization-heading,.visualization-title-row,.visualization-actions,.visualization-search{display:flex;align-items:center;gap:8px}
.visualization-heading{flex:1 1 190px;min-width:0}
.visualization-mark{display:grid;place-items:center;flex:0 0 32px;height:32px;color:var(--accent);background:var(--accent-soft);border-radius:var(--radius-sm)}
.visualization-title-row{flex-wrap:wrap;font-size:14px}
.visualization-heading p{margin:3px 0 0;color:var(--text-muted);font-size:12px;line-height:1.5}
.visualization-search{flex:1 1 140px;min-width:0;padding:0 10px;color:var(--text-muted);background:var(--surface-soft);border:1px solid var(--border-default);border-radius:var(--radius-sm)}
.visualization-search input{width:100%;min-height:34px;padding:4px 0;border:0;background:transparent;outline:0;font-size:13px}
.visualization-search:focus-within{border-color:var(--border-focus);box-shadow:var(--focus-ring)}
.visualization-actions{gap:2px;padding:3px;background:var(--surface-tint);border-radius:var(--radius-sm)}
.visualization-actions :deep(.el-button){width:32px;height:32px;padding:0;margin:0;color:var(--text-muted);border-radius:var(--radius-sm)}
.visualization-actions :deep(.el-button:hover),.visualization-actions :deep(.el-button[aria-pressed=true]){color:var(--accent-strong);background:var(--surface);box-shadow:var(--shadow-xs)}
.visualization-stage{position:relative;display:grid;isolation:isolate;min-height:390px;background:var(--surface-soft) radial-gradient(var(--border-default) 1px,transparent 1px) 0 0 / 20px 20px}
.visualization-stage > :slotted(*){width:100%;min-width:0}
.visualization-stage.has-feedback{min-height:240px;background:var(--surface-soft)}
.visualization-state{display:grid;place-items:center;align-content:center;gap:8px;padding:24px;color:var(--text-muted);text-align:center;font-size:13px}
.visualization-stage:has(.visualization-state) > :slotted(*){display:none}
.visualization-state strong{color:var(--text);font-size:16px}
.visualization-state span{max-width:44ch}
.visualization-state svg{color:var(--accent)}
.visualization-error{background:var(--danger-soft)}
.visualization-error :is(strong,svg){color:var(--danger)}
.visualization-footer{padding:10px 16px;border-top:1px solid var(--border-soft)}
@media(max-width:700px){.visualization-stage{min-height:320px}.visualization-toolbar,.visualization-footer{padding-inline:12px}}
</style>
