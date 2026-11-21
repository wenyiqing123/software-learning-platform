<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, CircleAlert, CircleCheck, LoaderCircle, RotateCcw } from '@lucide/vue'
import { learningApi, type KnowledgePoint, type PrerequisitePath } from '../api/client'

const props = defineProps<{ courseId?: number; initialTargetId?: number }>()
const emit = defineEmits<{ targetChange: [graphEntity: string] }>()
const points = ref<KnowledgePoint[]>([])
const targetId = ref<number | null>(props.initialTargetId ?? null)
const path = ref<PrerequisitePath | null>(null)
const loading = ref(false)
const pointLoading = ref(false)
const error = ref('')

const target = computed(() => points.value.find(point => point.id === targetId.value))
const statusLabel: Record<string, string> = {
  MASTERED: '已掌握',
  IN_PROGRESS: '学习中',
  UP_NEXT: '下一步',
  BLOCKED: '先修未完成',
}

function statusText(value: string) { return statusLabel[value] || '待学习' }
function failure() { return '暂时无法读取先修路径，请稍后重试。' }

async function loadPoints() {
  pointLoading.value = true
  error.value = ''
  try {
    points.value = (await learningApi.knowledgePoints(props.courseId)) as KnowledgePoint[]
    if (!targetId.value || !points.value.some(point => point.id === targetId.value)) targetId.value = points.value[0]?.id ?? null
  } catch {
    points.value = []
    error.value = failure()
  } finally {
    pointLoading.value = false
  }
}

async function loadPath() {
  if (!targetId.value) {
    path.value = null
    return
  }
  loading.value = true
  error.value = ''
  try {
    path.value = await learningApi.learningPath(targetId.value) as PrerequisitePath
    const selected = path.value.nodes.find(node => node.id === path.value?.targetId)
    if (selected) emit('targetChange', selected.graphEntity)
  }
  catch { path.value = null; error.value = failure() }
  finally { loading.value = false }
}

async function retry() {
  if (!points.value.length) await loadPoints()
  await loadPath()
}

function courseHref(node: PrerequisitePath['nodes'][number]) {
  return { path: '/courses', query: { courseId: node.courseId, knowledgePointId: node.id } }
}
function practiceHref(node: PrerequisitePath['nodes'][number]) {
  return { path: '/practice', query: { knowledgePointId: node.id, courseId: node.courseId } }
}
function graphHref(node: PrerequisitePath['nodes'][number]) {
  return { path: '/knowledge', query: { entity: node.graphEntity, courseId: node.courseId, knowledgePointId: node.id } }
}

watch(() => props.initialTargetId, value => {
  if (value && value !== targetId.value) { targetId.value = value; void loadPath() }
})
watch(() => props.courseId, async () => { await loadPoints(); await loadPath() })
onMounted(async () => { await loadPoints(); await loadPath() })
</script>

<template>
  <section class="prerequisite-panel">
    <div class="path-toolbar">
      <label class="path-target-picker">目标知识点
        <select v-model="targetId" aria-label="先修路径目标" :disabled="pointLoading || loading" @change="loadPath">
          <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option>
        </select>
      </label>
      <span v-if="target" class="path-target-note">围绕“{{ target.name }}”安排学习顺序</span>
    </div>
    <div v-if="loading" class="path-state" role="status"><LoaderCircle class="spin" :size="20" />正在整理先修路径…</div>
    <div v-else-if="error" class="path-state path-error" role="alert"><CircleAlert :size="20" /><span>{{ error }}</span><button class="button button-outline button-small" aria-label="重试读取先修路径" @click="retry"><RotateCcw :size="14" />重试</button></div>
    <div v-else-if="!path?.nodes.length" class="path-state"><CircleAlert :size="20" />当前知识点暂无可用的先修路径。</div>
    <template v-else>
      <div v-if="path.cycleDetected" class="path-warning" role="alert"><CircleAlert :size="16" />先修关系存在循环，系统已去重并停止递归；请联系管理员检查课程关系。</div>
      <ol class="path-list">
        <li v-for="(node, index) in path.nodes" :key="node.id" data-testid="path-node" class="path-node" :class="['path-' + node.status.toLowerCase(), { 'path-target': node.id === path.targetId }]">
          <div class="path-node-index">{{ index + 1 }}</div>
          <div class="path-node-main">
            <div class="path-node-heading"><strong>{{ node.name }}</strong><span class="path-status"><CircleCheck v-if="node.status === 'MASTERED'" :size="13" />{{ statusText(node.status) }}</span></div>
            <div class="path-node-meta"><span>系统掌握度 {{ Math.round(node.mastery) }}%</span><span v-if="node.prerequisiteIds.length">先修 {{ node.prerequisiteIds.length }} 项</span><span v-else>基础节点</span></div>
            <div class="path-node-actions">
              <RouterLink :to="courseHref(node)" :aria-label="'学习' + node.name + '课程内容'">课程阅读</RouterLink>
              <RouterLink :to="practiceHref(node)" :aria-label="'练习' + node.name">专项练习</RouterLink>
              <RouterLink :to="graphHref(node)" :aria-label="'查看' + node.name + '图谱'">查看图谱</RouterLink>
            </div>
          </div>
          <ArrowRight v-if="index < path.nodes.length - 1" class="path-arrow" :size="16" />
        </li>
      </ol>
    </template>
  </section>
</template>

<style scoped>
.prerequisite-panel { display: grid; gap: 16px; padding: 20px; background: var(--surface); border: 1px solid var(--border-default); border-radius: var(--radius-md); box-shadow: var(--shadow-sm); }
.path-toolbar { display: flex; align-items: end; flex-wrap: wrap; gap: 15px; }
.path-target-picker { display: grid; gap: 6px; min-width: 240px; color: var(--muted); font-size: 11px; }
.path-target-picker select { padding: 9px 10px; color: var(--ink); background: #fbfcfa; border: 1px solid #dbe3dc; border-radius: 6px; font: inherit; }
.path-target-note { padding-bottom: 9px; color: var(--muted); font-size: 11px; }
.path-state { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 100px; color: var(--muted); font-size: 12px; }
.path-error { justify-content: flex-start; color: #964632; background: #fff7f1; padding: 14px; border-radius: 6px; }
.path-error .button { margin-left: auto; }
.path-warning { display: flex; align-items: center; gap: 7px; padding: 10px 12px; color: #87512c; background: #fff7e8; border-radius: 6px; font-size: 11px; line-height: 1.6; }
.path-list { display: grid; gap: 10px; padding: 0; margin: 0; list-style: none; }
.path-node { position: relative; display: grid; grid-template-columns: 32px minmax(0, 1fr) 18px; gap: 10px; align-items: start; padding: 13px 12px; background: var(--surface-soft); border: 1px solid var(--border-soft); border-radius: var(--radius-md); }
.path-node:not(:last-child)::after { content: ""; position: absolute; left: 25px; bottom: -11px; width: 2px; height: 11px; background: linear-gradient(var(--accent), var(--border-default)); } .path-node-index { display: grid; place-items: center; width: 26px; height: 26px; color: var(--green); background: var(--green-pale); border-radius: 50%; font-size: 11px; font-weight: 700; }
.path-node-main { min-width: 0; }
.path-node-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.path-node-heading strong { color: var(--ink); font-size: 14px; }
.path-status { display: inline-flex; align-items: center; gap: 3px; padding: 3px 7px; color: #65776b; background: #edf1ed; border-radius: 4px; font-size: 10px; }
.path-target { border-color: var(--accent); box-shadow: var(--focus-ring); } .path-mastered .path-status { color: #176b57; background: #e6f3ed; }.path-in_progress .path-status { color: #8a642f; background: #fff3da; }.path-blocked .path-status { color: #9a5037; background: #fff0e8; }
.path-node-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 5px; color: var(--muted); font-size: 10px; }
.path-node-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; }
.path-node-actions a { color: var(--accent); font-size: 11px; text-decoration: none; }.path-node-actions a:hover { text-decoration: underline; }
.path-arrow { align-self: center; color: var(--accent); opacity:.7; }
@media(max-width:600px) { .path-error { align-items: flex-start; flex-wrap: wrap; }.path-error .button { margin-left: 0; }.path-node { grid-template-columns: 28px minmax(0,1fr); }.path-arrow { display: none; } }
</style>
