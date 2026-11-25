<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, ChevronDown, Copy, Database, Lightbulb, Network, RotateCcw } from '@lucide/vue'
import MarkdownContent from './MarkdownContent.vue'
import { stripLegacyModePrefix, type TutorAnswerResult } from '../domain/tutorChat'

const props = withDefaults(defineProps<{
  item: TutorAnswerResult
  canRegenerate?: boolean
  hintDisabled?: boolean
}>(), {
  canRegenerate: false,
  hintDisabled: false,
})

const emit = defineEmits<{
  copy: [value: string]
  regenerate: []
  hint: []
  'open-graph': [entity?: string]
}>()

const copied = ref(false)
let copyResetTimer: ReturnType<typeof setTimeout> | null = null

const cleanedAnswer = computed(() => stripLegacyModePrefix(props.item.answer || props.item.content || ''))
const primaryEntity = computed(() => {
  const fromEntities = props.item.entities?.find(e => e.canonicalName)?.canonicalName
  if (fromEntities) return fromEntities
  return props.item.triples?.find(t => t.subject)?.subject
})

function handleCopy() {
  if (!cleanedAnswer.value) return
  emit('copy', cleanedAnswer.value)
  copied.value = true
  if (copyResetTimer) clearTimeout(copyResetTimer)
  copyResetTimer = setTimeout(() => {
    copied.value = false
  }, 1800)
}
</script>

<template>
  <article class="tutor-answer-card" :class="{ 'answer-result-failed': item.error }">
    <!-- 知识图谱证据收纳为低调“引用来源胶囊”（默认折叠） -->
    <details v-if="item.triples?.length" class="evidence-block">
      <summary class="evidence-capsule-summary">
        <Database :size="14" class="evidence-capsule-icon" />
        <span class="evidence-capsule-label">已参考 {{ item.triples.length }} 条课程图谱证据</span>
        <ChevronDown :size="14" class="evidence-chevron" />
      </summary>
      <div class="evidence-panel">
        <div v-if="item.entities?.length || primaryEntity" class="evidence-panel-top">
          <div v-if="item.entities?.length" class="entity-tags">
            <button
              v-for="entity in item.entities"
              :key="entity.canonicalName"
              type="button"
              class="entity-tag-btn"
              @click="emit('open-graph', entity.canonicalName)"
            >
              {{ entity.canonicalName }}
            </button>
          </div>
          <button
            v-if="primaryEntity"
            type="button"
            class="evidence-graph-jump"
            @click="emit('open-graph', primaryEntity)"
          >
            <Network :size="13" />
            <span>在图谱中查看</span>
          </button>
        </div>
        <div class="evidence-triple-list">
          <div
            v-for="triple in item.triples"
            :key="triple.subject + triple.predicate + triple.object"
            class="evidence-triple"
          >
            <span class="triple-subject">{{ triple.subject }}</span>
            <i class="triple-predicate">— {{ triple.predicate }} —</i>
            <span class="triple-object">{{ triple.object }}</span>
          </div>
        </div>
      </div>
    </details>

    <!-- 失败或正常 Markdown 回答排版 -->
    <p v-if="item.error" class="tutor-answer-error" role="alert">{{ item.error }}</p>
    <div v-else class="tutor-answer-prose">
      <MarkdownContent :content="cleanedAnswer" />
    </div>

    <!-- 回复底部轻量操作条（仅生成完成且无错时展示） -->
    <div v-if="!item.error && cleanedAnswer" class="tutor-answer-actions">
      <button
        type="button"
        class="tutor-action-btn"
        :title="copied ? '已复制全文' : '复制全文'"
        @click="handleCopy"
      >
        <Check v-if="copied" :size="14" />
        <Copy v-else :size="14" />
        <span>{{ copied ? '已复制' : '复制' }}</span>
      </button>
      <button
        v-if="canRegenerate"
        type="button"
        class="tutor-action-btn"
        title="重新生成回答"
        data-testid="tutor-regenerate"
        @click="emit('regenerate')"
      >
        <RotateCcw :size="14" />
        <span>重新生成</span>
      </button>
      <button
        type="button"
        class="tutor-action-btn"
        title="获取分步引导提示"
        :disabled="hintDisabled"
        @click="emit('hint')"
      >
        <Lightbulb :size="14" />
        <span>分步提示</span>
      </button>
      <button
        v-if="primaryEntity"
        type="button"
        class="tutor-action-btn"
        title="前往知识图谱查看结构关系"
        @click="emit('open-graph', primaryEntity)"
      >
        <Network :size="14" />
        <span>前往图谱</span>
      </button>
    </div>
  </article>
</template>

<style scoped>
.tutor-answer-card {
  min-width: 0;
  display: grid;
  gap: 10px;
}

.answer-result-failed {
  padding-left: 12px;
  border-left: 3px solid var(--danger);
}

.evidence-block {
  min-width: 0;
  border-radius: var(--radius-sm);
}

.evidence-capsule-summary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 4px 10px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  list-style: none;
  transition: background-color var(--motion), border-color var(--motion), color var(--motion);
}

.evidence-capsule-summary::-webkit-details-marker {
  display: none;
}

.evidence-capsule-summary:hover {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--border-default);
}

.evidence-capsule-icon {
  color: var(--accent);
  flex-shrink: 0;
}

.evidence-capsule-label {
  line-height: 1.4;
}

.evidence-chevron {
  color: var(--text-subtle);
  flex-shrink: 0;
  transition: transform var(--motion);
}

.evidence-block[open] .evidence-chevron {
  transform: rotate(180deg);
}

.evidence-panel {
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  display: grid;
  gap: 8px;
}

.evidence-panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.entity-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.entity-tag-btn {
  min-height: 24px;
  padding: 2px 8px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.entity-tag-btn:hover {
  border-color: var(--border-focus);
}

.evidence-graph-jump {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 26px;
  padding: 2px 8px;
  color: var(--accent);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.evidence-graph-jump:hover {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.evidence-triple-list {
  display: grid;
}

.evidence-triple {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 6px 10px;
  align-items: baseline;
  padding: 7px 0;
  border-top: 1px solid var(--border-soft);
  font-size: 12.5px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.evidence-triple:first-child {
  border-top: 0;
  padding-top: 2px;
}

.triple-subject {
  color: var(--text);
  font-weight: 550;
}

.triple-predicate {
  color: var(--accent-strong);
  font-style: normal;
  font-size: 12px;
  text-align: center;
}

.triple-object {
  color: var(--text-muted);
}

.tutor-answer-error {
  margin: 0;
  padding: 10px 12px;
  color: var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius-sm);
  font-size: 13px;
  line-height: 1.6;
}

.tutor-answer-prose {
  min-width: 0;
  color: var(--text);
}

.tutor-answer-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  opacity: 0.78;
  transition: opacity var(--motion);
}

.tutor-answer-card:hover .tutor-answer-actions,
.tutor-answer-card:focus-within .tutor-answer-actions {
  opacity: 1;
}

.tutor-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 3px 9px;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.tutor-action-btn:hover:not(:disabled) {
  color: var(--accent-strong);
  background: var(--surface-tint);
}

@media (max-width: 560px) {
  .evidence-triple {
    grid-template-columns: 1fr;
    gap: 2px;
  }

  .triple-predicate {
    text-align: left;
  }
}
</style>
