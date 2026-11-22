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
.answer-card-meta,.evidence-title,.entity-tags{display:flex;align-items:center;gap:8px}
.answer-card-meta{flex-wrap:wrap;margin-bottom:10px;color:var(--text-muted);font-size:12px;font-variant-numeric:tabular-nums}
.answer-card-meta .text-button,.evidence-title span{margin-left:auto}
.answer-state,.entity-tags span{padding:3px 8px;border-radius:var(--radius-pill);font-weight:600;font-size:12px}
.answer-state.is-ready,.entity-tags span{color:var(--accent-strong);background:var(--accent-soft)}
.answer-state.is-error,.compare-error{color:var(--danger);background:var(--danger-soft)}
.compare-error{padding:10px;border-radius:var(--radius-sm)}
.result-mode-label{margin-bottom:6px;font-weight:650}
.entity-tags{flex-wrap:wrap;margin-top:10px}
.evidence-block{margin-top:12px;padding:0 12px;background:var(--surface-tint);border-radius:var(--radius-sm)}
.evidence-title{min-height:40px;cursor:pointer;font-size:13px}
.evidence-title svg{flex:none;color:var(--accent)}
.evidence-title span,.evidence-triple small{color:var(--text-muted);font-size:12px}
.evidence-triple{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr) auto;gap:8px;padding:8px 0;border-top:1px solid var(--border-default);font-size:13px;overflow-wrap:anywhere;font-variant-numeric:tabular-nums}
.evidence-triple i{color:var(--accent-strong);font-style:normal}
@media(max-width:520px){.evidence-triple small{grid-column:1/-1}}
</style>
