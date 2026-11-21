<script setup lang="ts">
import { Database, ChevronDown } from '@lucide/vue'
import MarkdownContent from './MarkdownContent.vue'

interface TutorAnswer { mode?: string; answer?: string; content?: string; error?: string; entities?: { canonicalName: string }[]; triples?: { subject: string; predicate: string; object: string; score?: number }[]; prompt?: string; latencyMs?: number }
defineProps<{ item: TutorAnswer; label?: string; showPrompt?: boolean }>()
const emit = defineEmits<{ copy: [value: string] }>()
</script>

<template>
  <article class="answer-result tutor-answer-card" :class="{ 'answer-result-failed': item.error }">
    <div v-if="label" class="result-mode-label">{{ label }}</div>
    <div class="answer-card-meta"><span :class="['answer-state', item.error ? 'is-error' : 'is-ready']">{{ item.error ? '失败' : '已生成' }}</span><span v-if="item.latencyMs">{{ item.latencyMs }} ms</span><button v-if="!item.error && (item.answer || item.content)" class="text-button" type="button" @click="emit('copy', item.answer || item.content || '')">复制回答</button></div>
    <p v-if="item.error" class="compare-error">{{ item.error }}</p>
    <MarkdownContent v-else :content="item.answer || item.content" />
    <div v-if="item.entities?.length" class="entity-tags"><span v-for="entity in item.entities" :key="entity.canonicalName">{{ entity.canonicalName }}</span></div>
    <details v-if="item.triples?.length" class="evidence-block"><summary class="evidence-title"><Database :size="15" /><strong>图谱证据</strong><span>{{ item.triples.length }} 条三元组</span><ChevronDown :size="14" /></summary><div v-for="triple in item.triples" :key="triple.subject + triple.predicate + triple.object" class="evidence-triple"><span>{{ triple.subject }}</span><i>{{ triple.predicate }}</i><span>{{ triple.object }}</span><small v-if="triple.score !== undefined">{{ Number(triple.score).toFixed(3) }}</small></div></details>
    <details v-if="showPrompt && item.prompt && item.mode !== 'RANKED'" class="prompt-details"><summary>查看检索提示（RANKED 后台约束不展示）<ChevronDown :size="14" /></summary><pre>{{ item.prompt }}</pre></details>
  </article>
</template>

<style scoped>
.tutor-answer-card{position:relative}.answer-card-meta{display:flex;align-items:center;gap:9px;margin-bottom:10px;color:var(--text-subtle);font-size:10px}.answer-card-meta .text-button{margin-left:auto}.answer-state{padding:3px 7px;border-radius:var(--radius-sm);font-weight:650}.answer-state.is-ready{color:var(--accent);background:var(--accent-soft)}.answer-state.is-error{color:var(--danger);background:var(--danger-soft)}.answer-result-failed{border-color:#e9cfc7;background:var(--danger-soft)}
</style>
