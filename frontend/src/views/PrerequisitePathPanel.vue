<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, CircleAlert, CircleCheck, Clock3, LoaderCircle, RotateCcw } from '@lucide/vue'
import { learningApi, type KnowledgePoint, type PrerequisitePath } from '../api/client'
import {
  effectiveMasteryPercent,
  normalizeReviewUrgency,
  peakMasteryPercent,
  retentionAlertLabel,
  RETENTION_RULE_SUMMARY,
} from '../domain/masteryRetention'

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
            <div class="path-node-heading">
              <strong>{{ node.name }}</strong>
              <span v-if="node.id === path.targetId" class="path-target-badge" aria-label="当前目标知识点">★ 当前目标</span>
              <span class="path-status"><CircleCheck v-if="node.status === 'MASTERED'" :size="13" />{{ statusText(node.status) }}</span>
              <span
                v-if="retentionAlertLabel(node)"
                class="path-retention-badge"
                :class="'urgency-' + normalizeReviewUrgency(node).toLowerCase()"
                :data-testid="`path-retention-alert-${node.id}`"
              >
                <Clock3 :size="12" aria-hidden="true" />
                {{ retentionAlertLabel(node) }}
              </span>
            </div>
            <div class="path-node-meta">
              <span>当前保持度 {{ effectiveMasteryPercent(node) }}%</span>
              <span>历史峰值 {{ peakMasteryPercent(node) }}%<template v-if="peakMasteryPercent(node) >= 60">（峰值达标已解锁）</template></span>
              <span v-if="node.prerequisiteIds.length">先修 {{ node.prerequisiteIds.length }} 项</span>
              <span v-else>基础节点</span>
            </div>
            <div class="path-node-actions">
              <RouterLink :to="courseHref(node)" :aria-label="'学习' + node.name + '课程内容'">课程阅读</RouterLink>
              <RouterLink :to="practiceHref(node)" :aria-label="'练习' + node.name">专项练习</RouterLink>
              <RouterLink :to="graphHref(node)" :aria-label="'查看' + node.name + '图谱'">查看图谱</RouterLink>
              <RouterLink v-if="retentionAlertLabel(node)" to="/review" :aria-label="'去复习' + node.name">间隔复习</RouterLink>
            </div>
          </div>
          <ArrowRight v-if="index < path.nodes.length - 1" class="path-arrow" :size="16" />
        </li>
      </ol>
      <p class="path-retention-rule" data-testid="path-retention-rule"><Clock3 :size="14" aria-hidden="true" /><span>{{ RETENTION_RULE_SUMMARY }}</span></p>
    </template>
  </section>
</template>

<style scoped>
.prerequisite-panel{display:grid;gap:16px;padding:20px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}
.path-toolbar{display:flex;align-items:end;flex-wrap:wrap;gap:12px}
.path-target-picker{display:grid;gap:6px;min-width:min(240px,100%);color:var(--text-muted);font-size:13px}
.path-target-note{padding-bottom:8px;color:var(--text-muted);font-size:12px}
.path-state,.path-warning{display:flex;flex-wrap:wrap;align-items:center;gap:8px;font-size:13px}
.path-state{justify-content:center;min-height:100px;color:var(--text-muted)}
.path-error,.path-warning{padding:12px;border-radius:var(--radius-sm)}
.path-error{color:var(--danger);background:var(--danger-soft)}
.path-warning{color:var(--warning);background:var(--warning-soft)}
.path-list{display:grid;gap:6px;margin:0;padding:0;list-style:none}
.path-node{position:relative;display:grid;grid-template-columns:28px minmax(0,1fr) auto;gap:12px;align-items:start;padding:12px}
.path-node:not(:last-child)::after{content:"";position:absolute;left:25px;top:44px;bottom:-10px;width:1px;background:var(--border-soft)}
.path-node-index{display:grid;place-items:center;width:28px;height:28px;color:var(--accent-strong);background:var(--accent-soft);border-radius:50%;font-size:12px;font-weight:650;font-variant-numeric:tabular-nums}
.path-node-main{min-width:0}
.path-node-heading,.path-node-meta,.path-node-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px 12px}
.path-node-heading strong{font-size:15px}
.path-target-badge{display:inline-flex;align-items:center;padding:2px 8px;color:var(--accent-strong);background:var(--accent-soft);border:1px solid var(--border-focus);border-radius:var(--radius-pill);font-size:11px;font-weight:600}
.path-status{display:inline-flex;align-items:center;gap:4px;padding:2px 8px;color:var(--text-muted);background:var(--surface-tint);border-radius:var(--radius-pill);font-size:12px}
.path-retention-badge{display:inline-flex;align-items:center;gap:4px;padding:2px 8px;border-radius:var(--radius-pill);font-size:11.5px;font-weight:600}
.path-retention-badge.urgency-fading{color:var(--warning-strong);background:var(--warning-soft)}
.path-retention-badge.urgency-due{color:var(--danger);background:var(--danger-soft)}
.path-retention-rule{display:flex;align-items:flex-start;gap:8px;margin:0;padding:10px 12px;color:var(--text-muted);background:var(--surface-tint);border-radius:var(--radius-sm);font-size:12.5px;line-height:1.55}
.path-retention-rule svg{flex:none;margin-top:2px;color:var(--accent-strong)}
.path-target{background:var(--surface-soft);border-radius:var(--radius-sm);box-shadow:inset 2px 0 var(--accent)}
.path-mastered .path-status{color:var(--accent-strong);background:var(--accent-soft)}
.path-in_progress .path-status,.path-in-progress .path-status{color:var(--warning-strong, #8a5300);background:var(--warning-soft)}
.path-up_next .path-status,.path-up-next .path-status{color:var(--info-strong, #125b82);background:var(--info-soft)}
.path-blocked .path-status{color:var(--danger-strong, #9e2a2b);background:var(--danger-soft)}
.path-node-meta{margin-top:4px;color:var(--text-muted);font-size:12px;font-variant-numeric:tabular-nums}
.path-node-actions{margin-top:8px}
.path-node-actions a{color:var(--accent-strong);font-size:13px;text-decoration:none}
.path-node-actions a:hover{text-decoration:underline}
.path-arrow{align-self:center;color:var(--text-subtle)}
@media(max-width:540px){.path-node{grid-template-columns:28px minmax(0,1fr)}.path-arrow{display:none}}
</style>
