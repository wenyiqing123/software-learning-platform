<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDown, ArrowRight, BookOpen, CircleAlert, Copy, Database, GraduationCap, Lightbulb, RefreshCw, Sparkles } from '@lucide/vue'
import MarkdownContent from './MarkdownContent.vue'
import TutorAnswerCard from './TutorAnswerCard.vue'
import { stripLegacyModePrefix, type TutorAnswerResult, type TutorChatMessage, type TutorQuickPrompt } from '../domain/tutorChat'

const DEFAULT_PROMPTS: TutorQuickPrompt[] = [
  { tag: '核心概念', title: '加工如何规范命名', prompt: '数据流图中的加工应该如何规范命名？' },
  { tag: '需求规格', title: '需求说明书核心结构', prompt: '软件需求规格说明书通常包含哪些核心内容？' },
  { tag: '方法对比', title: 'SA 与 OOA 方法区别', prompt: '对比结构化分析（SA）与面向对象分析（OOA）的核心思路' },
  { tag: '自测辨析', title: '黑盒测试与白盒测试', prompt: '黑盒测试与白盒测试的核心区别与适用场景是什么？' },
]

const props = withDefaults(defineProps<{
  messages: TutorChatMessage[]
  streamingAnswer: string
  streamingEvidence: { triples?: unknown[] } | null
  loading: boolean
  error: string
  retryQuestion?: string
  retryMode?: string
  quickPrompts?: TutorQuickPrompt[]
  hintLoading?: boolean
}>(), {
  quickPrompts: () => [],
  hintLoading: false,
})

const emit = defineEmits<{
  retry: [question: string, mode: string]
  copy: [value: string]
  regenerate: [question: string]
  'request-hint': [question: string, level: number]
  'open-graph': [entity?: string]
  'quick-prompt': [prompt: string]
}>()

const scrollContainer = ref<HTMLElement | null>(null)
const isUserScrolledUp = ref(false)
const activePrompts = computed(() => props.quickPrompts.length ? props.quickPrompts : DEFAULT_PROMPTS)

const lastAssistantIndex = computed(() => {
  for (let i = props.messages.length - 1; i >= 0; i--) {
    if (props.messages[i].role === 'ASSISTANT') return i
  }
  return -1
})

function resultsOf(message: TutorChatMessage): TutorAnswerResult[] {
  return message.metadata?.results || []
}

function hintOf(message: TutorChatMessage) {
  return message.metadata?.hint
}

function questionForMessage(index: number): string {
  for (let i = index - 1; i >= 0; i--) {
    if (props.messages[i].role === 'USER' && props.messages[i].content.trim()) {
      return props.messages[i].content.trim()
    }
  }
  return ''
}

function retry() {
  if (props.retryQuestion?.trim()) {
    emit('retry', props.retryQuestion, 'RANKED')
  }
}

function onScroll() {
  const el = scrollContainer.value
  if (!el) return
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  isUserScrolledUp.value = distanceFromBottom > 60
}

function scrollToBottom(smooth = true) {
  const el = scrollContainer.value
  if (!el) return
  isUserScrolledUp.value = false
  if (typeof el.scrollTo === 'function') {
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  } else {
    el.scrollTop = el.scrollHeight
  }
}

watch(
  () => [props.messages.length, props.streamingAnswer, props.loading, props.error],
  async () => {
    if (isUserScrolledUp.value) return
    await nextTick()
    scrollToBottom(false)
  }
)

defineExpose({ scrollToBottom })
</script>

<template>
  <section class="tutor-message-viewport" aria-label="学习助手对话">
    <div ref="scrollContainer" class="tutor-message-scroll" @scroll="onScroll">
      <div class="tutor-thread-container">
        <!-- 居中现代空态欢迎页与 2x2 建议提问卡片 -->
        <div v-if="!messages.length && !loading && !error" class="tutor-welcome">
          <div class="tutor-welcome-badge" aria-hidden="true">
            <GraduationCap :size="24" />
          </div>
          <h2>软件工程智能学习助教</h2>
          <p class="tutor-welcome-subtitle">结合课程知识图谱检索与推理，为你提供可溯源问答与分步引导。</p>
          <div class="tutor-quick-grid" aria-label="推荐学习提问">
            <button
              v-for="item in activePrompts"
              :key="item.prompt"
              type="button"
              class="tutor-quick-card"
              data-testid="tutor-quick-prompt"
              @click="emit('quick-prompt', item.prompt)"
            >
              <span class="tutor-quick-tag">
                <BookOpen :size="12" aria-hidden="true" />
                {{ item.tag }}
              </span>
              <strong class="tutor-quick-title">{{ item.title }}</strong>
              <span class="tutor-quick-preview">{{ item.prompt }}</span>
            </button>
          </div>
        </div>

        <!-- 历史对话流 -->
        <div
          v-for="(message, index) in messages"
          :key="message.id || `${index}-${message.role}-${message.content.slice(0, 20)}`"
          class="tutor-message-row"
          :class="message.role === 'USER' ? 'is-user' : 'is-assistant'"
        >
          <template v-if="message.role === 'USER'">
            <div class="tutor-user-bubble">
              <p class="tutor-user-content">{{ message.content }}</p>
            </div>
          </template>
          <template v-else>
            <div class="tutor-assistant-avatar" aria-hidden="true">
              <Sparkles :size="15" />
            </div>
            <article class="tutor-assistant-body">
              <!-- 分步引导提示消息 -->
              <template v-if="hintOf(message)">
                <div class="tutor-hint-badge">
                  <Lightbulb :size="13" />
                  <span>分步学习提示 · 第 {{ hintOf(message)!.level }} 级</span>
                </div>
                <div class="tutor-assistant-prose">
                  <MarkdownContent :content="hintOf(message)!.content || stripLegacyModePrefix(message.content)" />
                </div>
                <details v-if="hintOf(message)!.evidence?.length" class="evidence-block">
                  <summary class="evidence-capsule-summary">
                    <Database :size="14" class="evidence-capsule-icon" />
                    <span>查看本提示依据的 {{ hintOf(message)!.evidence.length }} 条图谱证据</span>
                  </summary>
                  <div class="hint-evidence-panel">
                    <div
                      v-for="ev in hintOf(message)!.evidence"
                      :key="ev.subject + ev.predicate + ev.object"
                      class="hint-evidence-item"
                    >
                      <span>{{ ev.subject }}</span>
                      <i>— {{ ev.predicate }} —</i>
                      <span>{{ ev.object }}</span>
                    </div>
                  </div>
                </details>
                <div v-if="hintOf(message)!.nextLevelAvailable" class="tutor-hint-next-row">
                  <button
                    type="button"
                    class="button button-outline button-small"
                    :disabled="loading || hintLoading"
                    @click="emit('request-hint', hintOf(message)!.question || questionForMessage(index), hintOf(message)!.level + 1)"
                  >
                    <span>展开第 {{ hintOf(message)!.level + 1 }} 级提示</span>
                    <ArrowRight :size="14" />
                  </button>
                </div>
              </template>

              <!-- 常规回答（含多结果旧历史兼容渲染，隐藏实验模式词） -->
              <template v-else-if="resultsOf(message).length">
                <TutorAnswerCard
                  v-for="(result, rIdx) in resultsOf(message)"
                  :key="result.mode ? `${result.mode}-${rIdx}` : rIdx"
                  :item="result"
                  :can-regenerate="index === lastAssistantIndex && rIdx === resultsOf(message).length - 1 && !loading && Boolean(questionForMessage(index))"
                  :hint-disabled="hintLoading || loading || !questionForMessage(index)"
                  @copy="emit('copy', $event)"
                  @regenerate="emit('regenerate', questionForMessage(index))"
                  @hint="emit('request-hint', questionForMessage(index), 1)"
                  @open-graph="emit('open-graph', $event)"
                />
              </template>

              <!-- 纯文本历史降级渲染 -->
              <template v-else>
                <div class="tutor-assistant-prose">
                  <MarkdownContent :content="stripLegacyModePrefix(message.content)" />
                </div>
                <div v-if="stripLegacyModePrefix(message.content).trim()" class="tutor-plain-actions">
                  <button
                    type="button"
                    class="tutor-plain-action-btn"
                    @click="emit('copy', stripLegacyModePrefix(message.content))"
                  >
                    <Copy :size="14" />
                    <span>复制</span>
                  </button>
                </div>
              </template>
            </article>
          </template>
        </div>

        <!-- 实时流式生成状态（去边框自然呈现） -->
        <div v-if="loading" class="tutor-message-row is-assistant is-streaming" aria-live="polite">
          <div class="tutor-assistant-avatar" aria-hidden="true">
            <Sparkles :size="15" />
          </div>
          <div class="tutor-assistant-body">
            <div class="tutor-streaming-pill">
              <span class="streaming-dot" aria-hidden="true"></span>
              <span v-if="streamingEvidence">
                已参考 {{ streamingEvidence.triples?.length || 0 }} 条课程图谱证据{{ streamingAnswer ? '' : '，正在组织回答…' }}
              </span>
              <span v-else>正在检索课程知识图谱…</span>
            </div>
            <div v-if="streamingAnswer" class="tutor-streaming-prose">
              <MarkdownContent :content="streamingAnswer" />
              <span class="streaming-cursor" aria-hidden="true"></span>
            </div>
          </div>
        </div>

        <!-- 诚实错误或中断提示 -->
        <div v-if="error" class="tutor-message-error" role="alert">
          <CircleAlert :size="16" class="error-icon" />
          <span>{{ error }}</span>
          <button
            v-if="retryQuestion"
            class="button button-outline button-small"
            type="button"
            data-testid="tutor-retry"
            @click="retry"
          >
            <RefreshCw :size="14" />
            <span>重试</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 用户主动上滑时浮现的回到底部按钮 -->
    <button
      v-show="isUserScrolledUp && (messages.length > 0 || loading)"
      type="button"
      class="tutor-scroll-bottom-btn"
      data-testid="tutor-scroll-bottom"
      aria-label="回到底部"
      title="回到底部"
      @click="scrollToBottom(true)"
    >
      <ArrowDown :size="16" />
    </button>
  </section>
</template>

<style scoped>
.tutor-message-viewport {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.tutor-message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px 10px;
  scrollbar-width: thin;
  scrollbar-color: var(--border-default) transparent;
}

.tutor-thread-container {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 780px;
  width: 100%;
  margin: 0 auto;
  min-height: 100%;
}

.tutor-welcome {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 6px;
  flex: 1;
  margin: auto 0;
  padding: 8px 0 4px;
  text-align: center;
}

.tutor-welcome-badge {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-radius: var(--radius-md);
}

.tutor-welcome h2 {
  margin: 2px 0 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 700;
}

.tutor-welcome-subtitle {
  max-width: 54ch;
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.tutor-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  margin-top: 8px;
}

.tutor-quick-card {
  display: grid;
  justify-items: start;
  align-content: start;
  gap: 3px;
  min-width: 0;
  padding: 10px 13px;
  color: var(--text);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--motion), background-color var(--motion), transform var(--motion), box-shadow var(--motion);
}

.tutor-quick-card:hover {
  background: var(--surface);
  border-color: var(--border-focus);
  box-shadow: var(--shadow-xs);
  transform: translateY(-1px);
}

.tutor-quick-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent-strong);
  font-size: 11px;
  font-weight: 650;
}

.tutor-quick-title {
  color: var(--text);
  font-size: 13.5px;
  font-weight: 650;
  line-height: 1.4;
}

.tutor-quick-preview {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.tutor-message-row {
  display: flex;
  min-width: 0;
}

.tutor-message-row.is-user {
  justify-content: flex-end;
}

.tutor-user-bubble {
  max-width: 76%;
  padding: 11px 16px;
  color: var(--text);
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 16%, transparent);
  border-radius: 16px 16px 4px 16px;
}

.tutor-user-content {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tutor-message-row.is-assistant {
  align-items: flex-start;
  gap: 12px;
}

.tutor-assistant-avatar {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  margin-top: 2px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-radius: 8px;
}

.tutor-assistant-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.tutor-assistant-body :deep(.tutor-answer-card + .tutor-answer-card) {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--border-soft);
}

.tutor-assistant-prose {
  min-width: 0;
  color: var(--text);
}

.tutor-hint-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: max-content;
  padding: 3px 10px;
  color: var(--warning);
  background: var(--warning-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
}

.evidence-block {
  min-width: 0;
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
  cursor: pointer;
  list-style: none;
}

.evidence-capsule-summary::-webkit-details-marker {
  display: none;
}

.evidence-capsule-icon {
  color: var(--accent);
}

.hint-evidence-panel {
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  display: grid;
  gap: 6px;
}

.hint-evidence-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 8px;
  font-size: 12.5px;
  overflow-wrap: anywhere;
}

.hint-evidence-item i {
  color: var(--accent-strong);
  font-style: normal;
}

.tutor-hint-next-row {
  display: flex;
  align-items: center;
}

.tutor-plain-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tutor-plain-action-btn {
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
  cursor: pointer;
}

.tutor-plain-action-btn:hover {
  color: var(--accent-strong);
  background: var(--surface-tint);
}

.tutor-streaming-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: max-content;
  max-width: 100%;
  min-height: 30px;
  padding: 4px 11px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
}

.streaming-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  animation: tutor-pulse 1.2s ease-in-out infinite;
}

@keyframes tutor-pulse {
  0%, 100% { opacity: 0.35; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); }
}

.tutor-streaming-prose {
  min-width: 0;
  color: var(--text);
}

.streaming-cursor {
  display: inline-block;
  width: 7px;
  height: 15px;
  margin-left: 3px;
  vertical-align: text-bottom;
  background: var(--accent);
  border-radius: 2px;
  animation: tutor-blink 0.9s steps(2, start) infinite;
}

@keyframes tutor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.15; }
}

.tutor-message-error {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 11px 14px;
  color: var(--danger);
  background: var(--danger-soft);
  border: 1px solid var(--danger-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.error-icon {
  flex-shrink: 0;
}

.tutor-message-error span {
  flex: 1;
  min-width: 0;
}

.tutor-scroll-bottom-btn {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  z-index: 5;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  min-height: 34px;
  padding: 0;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: 50%;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
}

.tutor-scroll-bottom-btn:hover {
  color: var(--accent-strong);
  border-color: var(--border-focus);
  background: var(--accent-soft);
}

@media (max-width: 640px) {
  .tutor-message-scroll {
    padding: 16px 14px 12px;
  }

  .tutor-quick-grid {
    grid-template-columns: 1fr;
  }

  .tutor-user-bubble {
    max-width: 88%;
  }

  .tutor-assistant-avatar {
    display: none;
  }
}
</style>
