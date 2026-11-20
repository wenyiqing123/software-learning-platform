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
          <ElButton text :type="mode === '2d' ? 'primary' : 'info'" aria-label="2D 图谱" @click="emit('mode-change', '2d')"><Network :size="15" /></ElButton>
        </ElTooltip>
        <ElTooltip content="Three.js 3D 图谱" placement="top">
          <ElButton text :type="mode === '3d' ? 'primary' : 'info'" aria-label="3D 图谱" @click="emit('mode-change', '3d')"><Box :size="15" /></ElButton>
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
    <div class="visualization-stage">
      <slot />
      <div v-if="loading" class="visualization-state" role="status"><RefreshCw class="spin" :size="23" /><strong>正在加载可视化数据</strong><span>请稍候，正在整理知识节点与关系。</span></div>
      <div v-else-if="error" class="visualization-state visualization-error" role="alert"><Network :size="26" /><strong>可视化数据暂时不可用</strong><span>{{ error }}</span><ElButton type="primary" plain size="small" aria-label="重试图谱" @click="emit('retry')">重新连接</ElButton></div>
      <div v-else-if="unavailable" class="visualization-state" role="status"><Network :size="26" /><strong>等待知识服务连接</strong><span>启动图谱服务后，这里会显示课程知识关系。</span><ElButton plain size="small" aria-label="重试图谱" @click="emit('retry')">检查连接</ElButton></div>
      <div v-else-if="empty" class="visualization-state" role="status"><Network :size="26" /><strong>暂无可显示的知识关系</strong><span>尝试搜索其他课程概念或调整筛选条件。</span></div>
    </div>
    <footer v-if="$slots.footer" class="visualization-footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.visualization-viewport { --viewport-border: var(--border-default); --viewport-bg: var(--surface-soft); display: grid; overflow: hidden; min-height: 500px; background: var(--viewport-bg); border: 1px solid var(--viewport-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-md); }
.visualization-viewport:fullscreen { width: 100vw; height: 100vh; min-height: 100vh; border-radius: 0; background: var(--surface-soft); }
.visualization-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 65px; padding: 12px 16px; background: rgba(255,255,255,.94); border-bottom: 1px solid var(--viewport-border); }
.visualization-heading,.visualization-title-row,.visualization-actions { display: flex; align-items: center; }.visualization-heading { min-width: 0; gap: 10px; }.visualization-mark { width: 32px; height: 32px; display: grid; flex: 0 0 auto; place-items: center; color: #176b57; background: #e2f1e8; border-radius: 9px; }.visualization-title-row { flex-wrap: wrap; gap: 8px; }.visualization-title-row strong { color: #25362c; font-size: 13px; }.visualization-heading p { margin: 3px 0 0; color: #819087; font-size: 10px; }.visualization-actions { gap: 2px; }.visualization-actions :deep(.el-button) { width: 32px; height: 32px; margin: 0; border-radius: 8px; }.visualization-actions :deep(.el-button:hover) { background: #edf6ef; }
.visualization-stage { position: relative; min-height: 430px; isolation: isolate; background-color: #f8fbf9; background-image: linear-gradient(rgba(94, 139, 111, .07) 1px, transparent 1px), linear-gradient(90deg, rgba(94, 139, 111, .07) 1px, transparent 1px); background-size: 32px 32px; }.visualization-stage > :slotted(*) { position: relative; z-index: 1; }.visualization-state { position: absolute; inset: 0; z-index: 3; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 24px; color: #718078; text-align: center; background: rgba(248,251,249,.88); backdrop-filter: blur(3px); }.visualization-state strong { color: #44574b; font-size: 12px; }.visualization-state span { max-width: 340px; color: #84928a; font-size: 10px; line-height: 1.7; }.visualization-error { color: #a35c43; }.visualization-error strong { color: #914e38; }.visualization-footer { display: flex; align-items: center; min-height: 48px; padding: 8px 16px; background: #fff; border-top: 1px solid var(--viewport-border); }
.spin { animation: viewport-spin .9s linear infinite; color: #4b986b; }@keyframes viewport-spin { to { transform: rotate(360deg); } }
@media(max-width:700px){.visualization-viewport{min-height:390px;border-radius:10px}.visualization-toolbar{align-items:flex-start;min-height:58px;padding:10px}.visualization-heading p{display:none}.visualization-stage{min-height:320px;background-size:24px 24px}.visualization-actions :deep(.el-button){width:29px;height:29px}.visualization-footer{padding:7px 10px}}
</style>
