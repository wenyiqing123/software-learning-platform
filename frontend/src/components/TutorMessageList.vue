<script setup lang="ts">
import { CircleAlert, Database, RefreshCw, Sparkles } from '@lucide/vue'
import MarkdownContent from './MarkdownContent.vue'
import TutorAnswerCard from './TutorAnswerCard.vue'
import type { TutorAnswerResult, TutorChatMessage } from '../domain/tutorChat'

const props = defineProps<{
  messages: TutorChatMessage[]
  streamingAnswer: string
  streamingEvidence: { triples?: unknown[] } | null
  loading: boolean
  error: string
  retryQuestion?: string
  retryMode?: string
}>()

const emit = defineEmits<{
  retry: [question: string, mode: string]
  copy: [value: string]
}>()

function resultsOf(message: TutorChatMessage): TutorAnswerResult[] {
  return message.metadata?.results || []
}

function retry() {
  if (props.retryQuestion?.trim()) emit('retry', props.retryQuestion, props.retryMode || 'RANKED')
}
</script>

<template>
  <section class="tutor-message-list" aria-label="学习助手对话">
    <div v-if="!messages.length && !loading && !error" class="tutor-message-empty">
      <Sparkles :size="22" />
      <strong>从一个课程问题开始</strong>
      <span>回答会保存在当前学习会话中，方便你继续追问和复习。</span>
    </div>

    <div v-for="message in messages" :key="message.id || `${message.role}-${message.content}`" class="tutor-message-row" :class="message.role === 'USER' ? 'is-user' : 'is-assistant'">
      <div class="tutor-message-avatar" aria-hidden="true"><span v-if="message.role === 'USER'">我</span><Sparkles v-else :size="15" /></div>
      <article class="tutor-message-bubble">
        <p class="tutor-message-role">{{ message.role === 'USER' ? '你的问题' : '学习助教' }}</p>
        <template v-if="message.role === 'USER'">
          <p class="tutor-user-content">{{ message.content }}</p>
        </template>
        <template v-else-if="resultsOf(message).length">
          <TutorAnswerCard v-for="(result, index) in resultsOf(message)" :key="result.mode || index" :item="result" :label="resultsOf(message).length > 1 ? (result.mode || '问答结果') : undefined" :show-prompt="false" @copy="emit('copy', $event)" />
        </template>
        <MarkdownContent v-else :content="message.content" />
      </article>
    </div>

    <article v-if="loading" class="tutor-streaming-row" aria-live="polite">
      <div class="tutor-message-avatar"><Sparkles :size="15" /></div>
      <div class="tutor-streaming-card">
        <div class="tutor-streaming-heading"><span class="spinner"></span><strong>{{ streamingEvidence ? '正在生成有依据的回答' : '正在整理课程知识' }}</strong></div>
        <small>{{ streamingEvidence ? `已获取 ${streamingEvidence.triples?.length || 0} 条图谱证据` : '检索图谱、评估相关性并准备回答' }}</small>
        <MarkdownContent v-if="streamingAnswer" :content="streamingAnswer" />
      </div>
    </article>

    <div v-if="error" class="tutor-message-error" role="alert">
      <CircleAlert :size="16" />
      <span>{{ error }}</span>
      <button v-if="retryQuestion" class="button button-outline button-small" type="button" data-testid="tutor-retry" @click="retry"><RefreshCw :size="14" />重试</button>
    </div>
  </section>
</template>

<style scoped>
.tutor-message-list{display:grid;align-content:start;gap:16px;min-width:0;min-height:145px}
.tutor-message-row,.tutor-streaming-row{display:grid;grid-template-columns:30px minmax(0,1fr);gap:10px;align-items:start;min-width:0}
.tutor-message-row.is-user{justify-self:end;grid-template-columns:minmax(0,1fr) 30px;max-width:min(90%,var(--reading-width))}
.is-user .tutor-message-avatar{grid-column:2}
.is-user .tutor-message-bubble{grid-column:1;grid-row:1;background:var(--accent-soft);border-color:transparent}
.tutor-message-avatar{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:var(--accent-soft);color:var(--accent-strong);font-size:12px;font-weight:650}
.tutor-message-bubble,.tutor-streaming-card{min-width:0;padding:14px 16px;border:1px solid var(--border-default);border-radius:var(--radius-md);background:var(--surface)}
.tutor-message-role{margin:0 0 8px;color:var(--text-muted);font-size:12px;font-weight:650}
.tutor-user-content{margin:0;color:var(--text);font-size:14px;line-height:1.65;white-space:pre-wrap;overflow-wrap:anywhere}
.tutor-message-bubble :deep(.tutor-answer-card+.tutor-answer-card){margin-top:12px;padding-top:12px;border-top:1px solid var(--border-soft)}
.tutor-streaming-card{display:grid;gap:8px;border-color:var(--border-focus)}
.tutor-streaming-heading,.tutor-message-error{display:flex;align-items:center;gap:8px}
.tutor-streaming-heading strong{font-size:14px}.tutor-streaming-card small{color:var(--text-muted);font-size:12px}
.tutor-message-error{flex-wrap:wrap;padding:12px;color:var(--danger);border-radius:var(--radius-sm);background:var(--danger-soft);font-size:13px}
.tutor-message-error span{min-width:0;flex:1}
.tutor-message-empty{display:grid;justify-items:center;place-content:center;gap:6px;min-height:145px;padding:24px 16px;color:var(--text-muted);text-align:center}
.tutor-message-empty svg{color:var(--accent)}
.tutor-message-empty strong{color:var(--text);font-size:16px}
.tutor-message-empty span{max-width:44ch;font-size:13px}
@media(max-width:620px){.tutor-message-row,.tutor-streaming-row{grid-template-columns:26px minmax(0,1fr);gap:6px}.tutor-message-row.is-user{grid-template-columns:minmax(0,1fr) 26px}.tutor-message-avatar{width:26px;height:26px}.tutor-message-bubble,.tutor-streaming-card{padding:12px}}
</style>
