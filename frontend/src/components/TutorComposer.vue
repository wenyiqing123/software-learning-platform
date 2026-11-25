<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowUp, Lightbulb, Square, X } from '@lucide/vue'
import type { TutorContextChip } from '../domain/tutorChat'

const props = withDefaults(defineProps<{
  question: string
  loading: boolean
  hintLoading?: boolean
  context?: TutorContextChip[]
}>(), {
  hintLoading: false,
  context: () => [],
})

const emit = defineEmits<{
  'update:question': [value: string]
  submit: []
  stop: []
  'request-hint': []
  'dismiss-context': []
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const isComposing = ref(false)
const canSubmit = computed(() => !props.loading && Boolean(props.question.trim()))
const canHint = computed(() => !props.loading && !props.hintLoading && Boolean(props.question.trim()))

function adjustHeight() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const nextHeight = Math.min(Math.max(el.scrollHeight, 36), 180)
  el.style.height = `${nextHeight}px`
}

function onInput(event: Event) {
  emit('update:question', (event.target as HTMLTextAreaElement).value)
  adjustHeight()
}

function onEnterKey(event: KeyboardEvent) {
  if (event.shiftKey || event.isComposing || isComposing.value || event.keyCode === 229) {
    return
  }
  event.preventDefault()
  if (canSubmit.value) {
    emit('submit')
  }
}

watch(
  () => props.question,
  async () => {
    await nextTick()
    adjustHeight()
  },
  { immediate: true }
)

defineExpose({
  focus: () => textareaRef.value?.focus(),
})
</script>

<template>
  <section class="tutor-composer" aria-label="学习助手输入区">
    <div class="tutor-composer-capsule">
      <label class="tutor-sr-label" for="question-input">输入你的学习问题</label>
      <textarea
        ref="textareaRef"
        id="question-input"
        class="tutor-composer-textarea"
        :value="question"
        rows="1"
        maxlength="2000"
        placeholder="向助教提问，如：软件需求规格说明书包含哪些内容？（Enter 发送，Shift + Enter 换行）"
        :disabled="loading"
        @input="onInput"
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
        @keydown.enter="onEnterKey"
      />

      <div class="tutor-composer-footer">
        <div class="tutor-composer-left">
          <div v-if="context?.length" class="tutor-context-row" aria-label="当前学习上下文">
            <span class="tutor-context-chip">
              <span v-for="(item, idx) in context" :key="item.label + item.value" class="tutor-context-item">
                <span v-if="idx > 0" class="tutor-context-sep">·</span>
                <strong>{{ item.label }}</strong>{{ item.value }}
              </span>
              <button
                type="button"
                class="tutor-context-close"
                aria-label="关闭当前学习上下文标签"
                title="关闭上下文标签"
                @click="emit('dismiss-context')"
              >
                <X :size="12" />
              </button>
            </span>
          </div>

          <button
            type="button"
            class="tutor-hint-chip"
            data-testid="tutor-hint-trigger"
            title="先获取思考方向提示，不直接展示完整答案"
            :disabled="!canHint"
            @click="emit('request-hint')"
          >
            <Lightbulb :size="14" />
            <span>{{ hintLoading ? '正在生成提示…' : '分步提示' }}</span>
          </button>
        </div>

        <div class="tutor-composer-right">
          <button
            v-if="loading"
            class="tutor-stop-btn"
            type="button"
            data-testid="tutor-stop"
            aria-label="停止生成"
            @click="emit('stop')"
          >
            <Square :size="12" />
            <span>停止生成</span>
          </button>
          <button
            v-else
            class="tutor-send-btn"
            type="button"
            data-testid="tutor-send"
            aria-label="发送问题"
            title="发送问题"
            :disabled="!canSubmit"
            @click="emit('submit')"
          >
            <ArrowUp :size="17" />
          </button>
        </div>
      </div>
    </div>

    <p class="tutor-composer-disclaimer">内容结合软件工程课程知识图谱生成，重要结论请对照教材核对。</p>
  </section>
</template>

<style scoped>
.tutor-composer {
  max-width: 780px;
  width: 100%;
  margin: 0 auto;
  min-width: 0;
}

.tutor-composer-capsule {
  display: grid;
  gap: 6px;
  padding: 10px 14px 9px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: 18px;
  box-shadow: var(--shadow-sm);
  transition: border-color var(--motion), box-shadow var(--motion);
}

.tutor-composer-capsule:focus-within {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-sm), var(--focus-ring);
}

.tutor-sr-label {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.tutor-composer-textarea {
  width: 100%;
  min-height: 36px;
  max-height: 180px;
  padding: 3px 4px;
  color: var(--text);
  background: transparent;
  border: 0;
  border-radius: 10px;
  font-size: 14.5px;
  line-height: 1.55;
  resize: none;
  outline: none;
  overflow-y: auto;
}

.tutor-composer-textarea:focus {
  outline: none;
  border: 0;
}

.tutor-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.tutor-composer-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.tutor-context-row {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-width: 0;
}

.tutor-context-chip {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;
  padding: 3px 8px 3px 10px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 20%, transparent);
  border-radius: var(--radius-pill);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.tutor-context-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.tutor-context-sep {
  margin-right: 2px;
  color: var(--text-subtle);
}

.tutor-context-close {
  display: inline-grid;
  place-items: center;
  width: 18px;
  height: 18px;
  min-height: 18px;
  margin-left: 2px;
  padding: 0;
  color: var(--accent-strong);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.tutor-context-close:hover {
  background: color-mix(in srgb, var(--accent) 16%, transparent);
}

.tutor-hint-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 4px 10px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 550;
  cursor: pointer;
}

.tutor-hint-chip:hover:not(:disabled) {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--border-default);
}

.tutor-composer-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.tutor-send-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  min-height: 34px;
  padding: 0;
  color: var(--surface);
  background: var(--accent);
  border: 1px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.tutor-send-btn:hover:not(:disabled) {
  background: var(--accent-strong);
}

.tutor-stop-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  color: var(--text);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.tutor-stop-btn:hover {
  color: var(--danger);
  background: var(--danger-soft);
  border-color: var(--danger-border);
}

.tutor-composer-disclaimer {
  margin: 7px 0 0;
  color: var(--text-subtle);
  font-size: 11.5px;
  text-align: center;
}

@media (max-width: 600px) {
  .tutor-composer-capsule {
    padding: 10px 12px 8px;
    border-radius: 16px;
  }

  .tutor-composer-disclaimer {
    display: none;
  }
}
</style>
