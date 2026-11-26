<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeftRight, CircleAlert, GitCompare, Network, RotateCcw, Sparkles } from '@lucide/vue'
import { learningApi, type GraphResponse, type KnowledgePoint } from '../api/client'
import {
  buildConceptCompareTutorPrompt,
  compareConcepts,
  CONCEPT_COMPARISON_PRESETS,
  type ComparisonPreset,
  type ConceptComparisonResult,
} from '../domain/conceptComparison'
import ContentSkeleton from './ContentSkeleton.vue'

const props = withDefaults(defineProps<{
  coursePoints?: KnowledgePoint[]
  initialLeft?: string
  initialRight?: string
}>(), {
  coursePoints: () => [],
  initialLeft: '结构化分析SA',
  initialRight: '面向对象分析OOA',
})

const emit = defineEmits<{
  'focus-entity': [entity: string]
  'ask-tutor': [payload: { entity: string; prompt: string }]
}>()

const leftInput = ref(props.initialLeft || '结构化分析SA')
const rightInput = ref(
  props.initialRight && props.initialRight !== leftInput.value
    ? props.initialRight
    : (leftInput.value === '面向对象分析OOA' ? '结构化分析SA' : '面向对象分析OOA'),
)
const loading = ref(false)
const sameEntityWarning = ref('')
const serviceError = ref('')
const comparison = ref<ConceptComparisonResult | null>(null)

let requestSeq = 0
let active = true

const suggestionNames = computed(() => {
  const names = new Set<string>()
  for (const preset of CONCEPT_COMPARISON_PRESETS) {
    names.add(preset.left)
    names.add(preset.right)
  }
  for (const kp of props.coursePoints) {
    const label = (kp.graphEntity || kp.name || '').trim()
    if (label) names.add(label)
  }
  return Array.from(names)
})

const partialUnavailableMessage = computed(() => {
  const res = comparison.value
  if (!res || serviceError.value) return ''
  if (!res.leftAvailable && !res.rightAvailable) {
    return '知识图谱服务暂不可用，请确认 Python Bridge 与 NebulaGraph 已启动。'
  }
  if (!res.leftAvailable) {
    return `左侧概念「${res.leftEntity}」图谱查询不可用或尚未连通，仅保留已成功检索的右侧信息。`
  }
  if (!res.rightAvailable) {
    return `右侧概念「${res.rightEntity}」图谱查询不可用或尚未连通，仅保留已成功检索的左侧信息。`
  }
  return ''
})

const canShowMatrix = computed(() =>
  Boolean(
    comparison.value
    && !comparison.value.sameEntity
    && !serviceError.value
    && (comparison.value.leftAvailable || comparison.value.rightAvailable),
  ),
)

async function runComparison() {
  const left = leftInput.value.trim()
  const right = rightInput.value.trim()
  serviceError.value = ''

  if (!left || !right) {
    sameEntityWarning.value = '请同时输入两个课程概念名称后再执行对比。'
    comparison.value = null
    return
  }
  if (left === right) {
    sameEntityWarning.value = '请选择或输入两个不同的概念进行结构化对比。'
    comparison.value = null
    return
  }

  sameEntityWarning.value = ''
  const seq = ++requestSeq
  loading.value = true

  try {
    const [leftSettled, rightSettled] = await Promise.allSettled([
      learningApi.graph(left),
      learningApi.graph(right),
    ])
    if (!active || seq !== requestSeq) return

    const emptyOffline: GraphResponse = { nodes: [], edges: [], evidence: [], available: false }
    const leftFailed = leftSettled.status === 'rejected'
    const rightFailed = rightSettled.status === 'rejected'

    if (leftFailed && rightFailed) {
      comparison.value = null
      serviceError.value = '知识图谱服务暂时不可用，请确认 Python Bridge 与 NebulaGraph 已启动后重试。'
      return
    }

    const leftRes = leftSettled.status === 'fulfilled' && leftSettled.value ? leftSettled.value : emptyOffline
    const rightRes = rightSettled.status === 'fulfilled' && rightSettled.value ? rightSettled.value : emptyOffline

    if (leftFailed || rightFailed) {
      serviceError.value = '部分概念图谱查询失败（知识服务可能离线），已停止生成对比矩阵以避免不完整对比。'
      comparison.value = null
      return
    }

    comparison.value = compareConcepts(left, leftRes, right, rightRes)
  } finally {
    if (active && seq === requestSeq) {
      loading.value = false
    }
  }
}

function applyPreset(preset: ComparisonPreset) {
  leftInput.value = preset.left
  rightInput.value = preset.right
  void runComparison()
}

function swapConcepts() {
  const temp = leftInput.value
  leftInput.value = rightInput.value
  rightInput.value = temp
  void runComparison()
}

function triggerAskTutor() {
  if (!comparison.value) return
  emit('ask-tutor', {
    entity: comparison.value.leftEntity,
    prompt: buildConceptCompareTutorPrompt(comparison.value),
  })
}

onMounted(() => {
  void runComparison()
})

onBeforeUnmount(() => {
  active = false
  requestSeq++
})
</script>

<template>
  <section class="compare-workspace" data-testid="concept-comparison-panel" aria-label="双概念知识图谱结构化对比">
    <div class="compare-controls-card">
      <div class="compare-controls-top">
        <div>
          <p class="eyebrow">图谱集合对比</p>
          <h2>双概念知识结构对比矩阵</h2>
        </div>
        <div class="compare-presets" data-testid="compare-presets">
          <span class="compare-presets-label">高频预设：</span>
          <button
            v-for="preset in CONCEPT_COMPARISON_PRESETS"
            :key="preset.id"
            type="button"
            class="compare-preset-btn"
            :class="{ active: leftInput.trim() === preset.left && rightInput.trim() === preset.right }"
            :data-testid="`compare-preset-${preset.id}`"
            @click="applyPreset(preset)"
          >
            {{ preset.label }}
          </button>
        </div>
      </div>

      <form class="compare-form" @submit.prevent="runComparison">
        <label class="compare-field">
          <span>概念 A</span>
          <input
            v-model="leftInput"
            type="text"
            list="compare-concept-suggestions"
            placeholder="输入左侧概念（如 结构化分析SA）"
            data-testid="compare-left-input"
          />
        </label>
        <button
          type="button"
          class="icon-button compare-swap-btn"
          aria-label="对调左右概念"
          title="对调左右概念"
          data-testid="compare-swap-btn"
          @click="swapConcepts"
        >
          <ArrowLeftRight :size="16" />
        </button>
        <label class="compare-field">
          <span>概念 B</span>
          <input
            v-model="rightInput"
            type="text"
            list="compare-concept-suggestions"
            placeholder="输入右侧概念（如 面向对象分析OOA）"
            data-testid="compare-right-input"
          />
        </label>
        <datalist id="compare-concept-suggestions">
          <option v-for="name in suggestionNames" :key="name" :value="name" />
        </datalist>
        <button
          type="button"
          class="button button-dark compare-submit-btn"
          :disabled="loading"
          data-testid="compare-submit-btn"
          @click="runComparison"
        >
          <GitCompare :size="15" />
          <span>{{ loading ? '对比查询中…' : '对比分析' }}</span>
        </button>
      </form>

      <div v-if="sameEntityWarning" class="inline-notice" role="alert" data-testid="compare-same-guard">
        <CircleAlert :size="16" />
        <span>{{ sameEntityWarning }}</span>
      </div>
      <div v-else-if="serviceError" class="inline-notice" role="alert" data-testid="compare-offline-alert">
        <CircleAlert :size="16" />
        <span>{{ serviceError }}</span>
        <button type="button" class="text-button" :disabled="loading" @click="runComparison">重试查询 <RotateCcw :size="13" /></button>
      </div>
      <div v-else-if="partialUnavailableMessage" class="inline-notice" role="status" data-testid="compare-offline-alert">
        <CircleAlert :size="16" />
        <span>{{ partialUnavailableMessage }}</span>
        <button type="button" class="text-button" :disabled="loading" @click="runComparison">刷新图谱 <RotateCcw :size="13" /></button>
      </div>
    </div>

    <ContentSkeleton v-if="loading" variant="cards" :rows="3" label="正在查询两侧图谱并计算关系交并集" />

    <template v-else-if="canShowMatrix && comparison">
      <div class="compare-matrix-grid" data-testid="compare-matrix">
        <article class="compare-column-card" data-testid="compare-col-direct">
          <header class="compare-col-head">
            <div>
              <p class="eyebrow">栏 1 / 3</p>
              <h3>直接关联</h3>
            </div>
            <span class="compare-count-pill">{{ comparison.directLinks.length }} 条</span>
          </header>
          <ul v-if="comparison.directLinks.length" class="compare-triple-list">
            <li v-for="(edge, idx) in comparison.directLinks" :key="`d-${idx}`" class="compare-triple-item">
              <span class="compare-node-tag">{{ edge.subject }}</span>
              <span class="compare-predicate-text">— {{ edge.predicate }} →</span>
              <span class="compare-node-tag">{{ edge.object }}</span>
            </li>
          </ul>
          <p v-else class="compare-empty-note">两概念在当前 1 跳邻域中暂无直接连边，可借助右侧共同关联节点观察间接桥梁。</p>
        </article>

        <article class="compare-column-card" data-testid="compare-col-shared">
          <header class="compare-col-head">
            <div>
              <p class="eyebrow">栏 2 / 3</p>
              <h3>共同关联节点</h3>
            </div>
            <span class="compare-count-pill">{{ comparison.sharedNeighbors.length }} 个</span>
          </header>
          <ul v-if="comparison.sharedNeighbors.length" class="compare-shared-list">
            <li v-for="shared in comparison.sharedNeighbors" :key="shared.entity" class="compare-shared-item">
              <button
                type="button"
                class="compare-entity-btn"
                :data-testid="`compare-focus-shared-${shared.entity}`"
                @click="emit('focus-entity', shared.entity)"
              >
                <Network :size="13" aria-hidden="true" />
                <span>{{ shared.entity }}</span>
              </button>
              <div class="compare-shared-edges">
                <span><strong>{{ comparison.leftEntity }}</strong>：{{ shared.leftPredicates.join(' / ') }}</span>
                <span><strong>{{ comparison.rightEntity }}</strong>：{{ shared.rightPredicates.join(' / ') }}</span>
              </div>
            </li>
          </ul>
          <p v-else class="compare-empty-note">当前 1 跳邻域内暂无同时连接两个概念的共同交集节点。</p>
        </article>

        <article class="compare-column-card" data-testid="compare-col-exclusive">
          <header class="compare-col-head">
            <div>
              <p class="eyebrow">栏 3 / 3</p>
              <h3>各自独有邻接关系</h3>
            </div>
            <span class="compare-count-pill">{{ comparison.leftExclusive.length }} / {{ comparison.rightExclusive.length }}</span>
          </header>
          <div class="compare-exclusive-split">
            <div class="compare-exclusive-group">
              <h4>{{ comparison.leftEntity }} 独有（{{ comparison.leftExclusive.length }}）</h4>
              <ul v-if="comparison.leftExclusive.length" class="compare-exclusive-list">
                <li v-for="(rel, idx) in comparison.leftExclusive" :key="`le-${idx}`">
                  <span class="compare-predicate-text">{{ rel.predicateLabel }}</span>
                  <button
                    type="button"
                    class="compare-entity-link"
                    @click="emit('focus-entity', rel.counterpart)"
                  >{{ rel.counterpart }}</button>
                </li>
              </ul>
              <p v-else class="compare-empty-note">暂无独有邻接实体。</p>
            </div>
            <div class="compare-exclusive-group">
              <h4>{{ comparison.rightEntity }} 独有（{{ comparison.rightExclusive.length }}）</h4>
              <ul v-if="comparison.rightExclusive.length" class="compare-exclusive-list">
                <li v-for="(rel, idx) in comparison.rightExclusive" :key="`re-${idx}`">
                  <span class="compare-predicate-text">{{ rel.predicateLabel }}</span>
                  <button
                    type="button"
                    class="compare-entity-link"
                    @click="emit('focus-entity', rel.counterpart)"
                  >{{ rel.counterpart }}</button>
                </li>
              </ul>
              <p v-else class="compare-empty-note">暂无独有邻接实体。</p>
            </div>
          </div>
        </article>
      </div>

      <footer class="compare-footer-bar">
        <div class="compare-footer-focus">
          <button type="button" class="button button-outline button-small" @click="emit('focus-entity', comparison.leftEntity)">
            <Network :size="14" />在主图谱聚焦「{{ comparison.leftEntity }}」
          </button>
          <button type="button" class="button button-outline button-small" @click="emit('focus-entity', comparison.rightEntity)">
            <Network :size="14" />在主图谱聚焦「{{ comparison.rightEntity }}」
          </button>
        </div>
        <button type="button" class="button button-dark button-small" data-testid="compare-ask-tutor" @click="triggerAskTutor">
          <Sparkles :size="14" /><span>带对比上下文问助教</span>
        </button>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.compare-workspace {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.compare-controls-card,
.compare-footer-bar {
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.compare-controls-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 16px;
}

.compare-controls-top h2,
.compare-col-head h3 {
  margin: 2px 0 0;
  color: var(--text);
  font-size: 16px;
}

.compare-presets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.compare-presets-label {
  color: var(--text-muted);
  font-size: 12px;
}

.compare-preset-btn {
  min-height: 28px;
  padding: 3px 10px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion), border-color var(--motion), color var(--motion);
}

.compare-preset-btn:hover,
.compare-preset-btn:focus-visible {
  color: var(--accent-strong);
  border-color: var(--border-focus);
  outline: none;
}

.compare-preset-btn.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--accent);
  font-weight: 600;
}

.compare-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px;
}

.compare-field {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.compare-field span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.compare-field input {
  width: 100%;
  min-width: 0;
  min-height: 36px;
  padding: 6px 10px;
  color: var(--text);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.compare-field input:focus {
  border-color: var(--border-focus);
  box-shadow: var(--focus-ring);
  outline: none;
}

.compare-swap-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.compare-submit-btn {
  min-height: 36px;
}

.compare-matrix-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  align-items: stretch;
}

.compare-column-card {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.compare-col-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-soft);
}

.compare-count-pill {
  padding: 2px 9px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.compare-triple-list,
.compare-shared-list,
.compare-exclusive-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.compare-triple-item,
.compare-shared-item {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  font-size: 12.5px;
}

.compare-triple-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.compare-node-tag {
  font-weight: 600;
  color: var(--text);
  overflow-wrap: anywhere;
}

.compare-predicate-text {
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 600;
}

.compare-entity-btn,
.compare-entity-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: fit-content;
  max-width: 100%;
  padding: 0;
  color: var(--accent-strong);
  background: transparent;
  border: 0;
  font-size: 13px;
  font-weight: 650;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.compare-entity-btn:hover,
.compare-entity-link:hover {
  text-decoration: underline;
}

.compare-shared-edges {
  display: grid;
  gap: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.compare-exclusive-split {
  display: grid;
  gap: 12px;
}

.compare-exclusive-group {
  display: grid;
  gap: 6px;
}

.compare-exclusive-group h4 {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
}

.compare-exclusive-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  padding: 6px 9px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-xs);
  font-size: 12.5px;
}

.compare-empty-note {
  margin: 0;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.55;
}

.compare-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.compare-footer-focus {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 980px) {
  .compare-matrix-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .compare-form {
    grid-template-columns: 1fr;
  }
  .compare-swap-btn {
    justify-self: start;
  }
}
</style>
