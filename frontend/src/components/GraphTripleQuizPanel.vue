<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, CircleAlert, CircleCheck, CircleX, Database, Network, Plus, RotateCcw } from '@lucide/vue'
import type { GraphResponse } from '../api/client'
import { buildDeterministicKgTripleQuiz } from '../domain/kgTripleQuiz'

const props = defineProps<{
  graphData: GraphResponse
  selectedEntity: string
  graphError: string
  expanding?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  'expand-hop': []
  'focus-entity': [entity: string]
  retry: []
}>()

const quizResult = computed(() => buildDeterministicKgTripleQuiz(props.graphData, 5))
const currentIdx = ref(0)
const selectedKeyByQuestion = ref<Record<string, string>>({})
const checkedByQuestion = ref<Record<string, boolean>>({})

watch(
  () => quizResult.value.questions.map((q) => q.id + q.correctValue).join('|'),
  () => {
    currentIdx.value = 0
    selectedKeyByQuestion.value = {}
    checkedByQuestion.value = {}
  },
)

const currentQuestion = computed(() => quizResult.value.questions[currentIdx.value] ?? null)
const selectedKey = computed(() => (currentQuestion.value ? selectedKeyByQuestion.value[currentQuestion.value.id] ?? '' : ''))
const isChecked = computed(() => (currentQuestion.value ? Boolean(checkedByQuestion.value[currentQuestion.value.id]) : false))

const selectedOption = computed(() =>
  currentQuestion.value?.options.find((opt) => opt.key === selectedKey.value) ?? null,
)
const isCurrentCorrect = computed(() =>
  Boolean(isChecked.value && currentQuestion.value && selectedOption.value?.value === currentQuestion.value.correctValue),
)

const checkedCount = computed(() =>
  quizResult.value.questions.filter((q) => Boolean(checkedByQuestion.value[q.id])).length,
)
const correctCount = computed(() =>
  quizResult.value.questions.filter((q) => {
    if (!checkedByQuestion.value[q.id]) return false
    const key = selectedKeyByQuestion.value[q.id]
    const opt = q.options.find((o) => o.key === key)
    return opt?.value === q.correctValue
  }).length,
)

function chooseOption(key: string) {
  if (!currentQuestion.value || isChecked.value) return
  selectedKeyByQuestion.value = {
    ...selectedKeyByQuestion.value,
    [currentQuestion.value.id]: key,
  }
}

function submitCheck() {
  if (!currentQuestion.value || !selectedKey.value) return
  checkedByQuestion.value = {
    ...checkedByQuestion.value,
    [currentQuestion.value.id]: true,
  }
}

function resetQuiz() {
  currentIdx.value = 0
  selectedKeyByQuestion.value = {}
  checkedByQuestion.value = {}
}
</script>

<template>
  <section class="content-section kg-quiz-panel" data-testid="kg-triple-quiz-panel" aria-label="图谱概念关系辨析快测">
    <header class="kg-quiz-head">
      <div>
        <p class="eyebrow">KG TRIPLE CLOZE · 图谱真值自测</p>
        <h2>概念关系辨析快测（中心实体：{{ selectedEntity }}）</h2>
      </div>
      <div class="kg-quiz-meta">
        <span class="kg-quiz-chip"><Database :size="13" aria-hidden="true" />可用三元组 {{ quizResult.tripleCount }} 条</span>
        <button
          type="button"
          class="button button-outline button-small"
          :disabled="expanding || loading || !selectedEntity"
          data-testid="kg-quiz-expand-btn"
          @click="emit('expand-hop')"
        >
          <Plus :size="14" aria-hidden="true" />
          <span>{{ expanding ? '扩展中…' : '扩展邻域 +1 跳' }}</span>
        </button>
      </div>
    </header>

    <div v-if="loading" class="kg-quiz-state" role="status">
      <span class="spinner"></span>
      <span>正在从 NebulaGraph 检索「{{ selectedEntity }}」真实子图三元组…</span>
    </div>

    <div v-else-if="graphError || !quizResult.ready" class="kg-quiz-unavailable" data-testid="kg-quiz-unavailable" role="status">
      <CircleAlert :size="18" aria-hidden="true" />
      <div>
        <strong>当前暂不可开启关系辨析快测</strong>
        <p>{{ graphError || quizResult.reason }}</p>
        <div class="kg-quiz-unavailable-actions">
          <button v-if="graphError" type="button" class="text-button" @click="emit('retry')">重新检查图谱服务</button>
          <button type="button" class="text-button" @click="emit('focus-entity', selectedEntity)">
            <Network :size="14" aria-hidden="true" />
            <span>返回图谱浏览</span>
          </button>
        </div>
      </div>
    </div>

    <div v-else-if="currentQuestion" class="kg-quiz-body">
      <div class="kg-quiz-progress-bar">
        <div class="kg-quiz-pills">
          <button
            v-for="(item, idx) in quizResult.questions"
            :key="item.id"
            type="button"
            class="kg-quiz-pill"
            :class="{
              active: idx === currentIdx,
              correct: Boolean(checkedByQuestion[item.id] && item.options.find(o => o.key === selectedKeyByQuestion[item.id])?.value === item.correctValue),
              wrong: Boolean(checkedByQuestion[item.id] && item.options.find(o => o.key === selectedKeyByQuestion[item.id])?.value !== item.correctValue),
            }"
            :data-testid="`kg-quiz-step-${idx + 1}`"
            @click="currentIdx = idx"
          >
            {{ String(idx + 1).padStart(2, '0') }}
          </button>
        </div>
        <span class="kg-quiz-score" data-testid="kg-quiz-score">
          已核对 {{ checkedCount }}/{{ quizResult.questions.length }} · 正确 {{ correctCount }} 题
        </span>
      </div>

      <article class="kg-quiz-card" data-testid="kg-quiz-card">
        <span class="kg-quiz-kind-badge">
          {{ currentQuestion.kind === 'object' ? '补全目标实体（Subject — Predicate — ?）' : '辨析知识关系（Subject — ? — Object）' }}
        </span>
        <div class="kg-triple-prompt" data-testid="kg-triple-prompt">
          <span class="kg-triple-node">{{ currentQuestion.promptParts.left }}</span>
          <span class="kg-triple-arrow" :class="{ missing: currentQuestion.kind === 'predicate' }">
            — {{ currentQuestion.promptParts.middle }} —
          </span>
          <span class="kg-triple-node" :class="{ missing: currentQuestion.kind === 'object' }">
            {{ currentQuestion.promptParts.right }}
          </span>
        </div>

        <div class="kg-option-grid">
          <button
            v-for="opt in currentQuestion.options"
            :key="opt.key"
            type="button"
            class="kg-option-btn"
            :class="{
              selected: selectedKey === opt.key,
              correct: isChecked && opt.value === currentQuestion.correctValue,
              wrong: isChecked && selectedKey === opt.key && opt.value !== currentQuestion.correctValue,
            }"
            :disabled="isChecked"
            :data-testid="`kg-quiz-option-${opt.key}`"
            @click="chooseOption(opt.key)"
          >
            <span class="kg-opt-key">{{ opt.key }}</span>
            <span>{{ opt.value }}</span>
          </button>
        </div>

        <div class="kg-quiz-actions">
          <button
            v-if="!isChecked"
            type="button"
            class="button button-dark button-small"
            :disabled="!selectedKey"
            data-testid="kg-quiz-submit"
            @click="submitCheck"
          >
            <span>核对真实图谱证据</span>
          </button>
          <template v-else>
            <button
              v-if="currentIdx + 1 < quizResult.questions.length"
              type="button"
              class="button button-dark button-small"
              data-testid="kg-quiz-next"
              @click="currentIdx += 1"
            >
              <span>下一组三元组</span>
              <ArrowRight :size="14" aria-hidden="true" />
            </button>
            <button
              type="button"
              class="button button-outline button-small"
              @click="resetQuiz"
            >
              <RotateCcw :size="14" aria-hidden="true" />
              <span>重新作答</span>
            </button>
          </template>
          <button
            type="button"
            class="text-button"
            data-testid="kg-quiz-focus-node"
            @click="emit('focus-entity', currentQuestion.sourceTriple.subject)"
          >
            <Network :size="14" aria-hidden="true" />
            <span>在图谱中查看「{{ currentQuestion.sourceTriple.subject }}」</span>
          </button>
        </div>

        <div
          v-if="isChecked"
          class="kg-quiz-feedback"
          :class="isCurrentCorrect ? 'is-correct' : 'is-wrong'"
          data-testid="kg-quiz-feedback"
        >
          <component :is="isCurrentCorrect ? CircleCheck : CircleX" :size="18" aria-hidden="true" />
          <div>
            <strong>{{ isCurrentCorrect ? '辨析准确，符合图谱真值记录' : `再对照一次：真实值为「${currentQuestion.correctValue}」` }}</strong>
            <p>
              溯源三元组：{{ currentQuestion.sourceTriple.subject }} — {{ currentQuestion.sourceTriple.predicate }} — {{ currentQuestion.sourceTriple.object }}
              <span v-if="typeof currentQuestion.sourceTriple.score === 'number'">（证据置信分 {{ currentQuestion.sourceTriple.score.toFixed(2) }}）</span>
            </p>
          </div>
        </div>
      </article>

      <p class="kg-quiz-disclaimer">
        题目与干扰项全部确定性抽取自当前在线图谱子图的真实三元组，仅作知识关联自测，不写入成绩。
      </p>
    </div>
  </section>
</template>

<style scoped>
.kg-quiz-panel {
  display: grid;
  gap: 14px;
}

.kg-quiz-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px 14px;
}

.kg-quiz-head h2,
.kg-quiz-head .eyebrow {
  margin: 0;
}

.kg-quiz-head h2 {
  margin-top: 3px;
  font-size: 18px;
}

.kg-quiz-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.kg-quiz-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  color: var(--text-muted);
  font-size: 12px;
}

.kg-quiz-state,
.kg-quiz-unavailable {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  color: var(--text-muted);
  font-size: 13px;
}

.kg-quiz-unavailable strong {
  color: var(--text);
}

.kg-quiz-unavailable p {
  margin: 4px 0 8px;
  line-height: 1.55;
}

.kg-quiz-unavailable-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.kg-quiz-body {
  display: grid;
  gap: 12px;
}

.kg-quiz-progress-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kg-quiz-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.kg-quiz-pill {
  min-width: 34px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.kg-quiz-pill.active {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
  color: var(--text);
}

.kg-quiz-pill.correct {
  background: var(--accent-soft);
  border-color: var(--border-focus);
  color: var(--accent-strong);
}

.kg-quiz-pill.wrong {
  background: var(--danger-soft);
  border-color: var(--danger-border);
  color: var(--danger);
}

.kg-quiz-score {
  font-size: 12.5px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.kg-quiz-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.kg-quiz-kind-badge {
  width: max-content;
  max-width: 100%;
  padding: 2px 9px;
  border-radius: var(--radius-pill);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 11.5px;
  font-weight: 600;
}

.kg-triple-prompt {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  font-size: 15px;
}

.kg-triple-node {
  font-weight: 700;
  color: var(--text);
}

.kg-triple-arrow {
  color: var(--accent-strong);
  font-size: 13.5px;
  font-weight: 600;
}

.kg-triple-node.missing,
.kg-triple-arrow.missing {
  padding: 2px 8px;
  border: 1px dashed var(--warning);
  border-radius: var(--radius-xs);
  background: var(--warning-soft);
  color: var(--warning);
}

.kg-option-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.kg-option-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}

.kg-option-btn.selected {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.kg-option-btn.correct {
  border-color: var(--border-focus);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.kg-option-btn.wrong {
  border-color: var(--danger-border);
  background: var(--danger-soft);
  color: var(--danger);
}

.kg-opt-key {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: var(--radius-xs);
  background: var(--surface-tint);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.kg-quiz-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.kg-quiz-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.kg-quiz-feedback.is-correct {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.kg-quiz-feedback.is-wrong {
  background: var(--danger-soft);
  color: var(--danger);
}

.kg-quiz-feedback p {
  margin: 4px 0 0;
  color: var(--text);
}

.kg-quiz-disclaimer {
  margin: 0;
  font-size: 12px;
  color: var(--text-subtle);
}

@media (max-width: 680px) {
  .kg-option-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
