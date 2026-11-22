<script setup lang="ts">
import { ChevronDown, Database, Sparkles, X } from '@lucide/vue'

export interface EvidenceTriple { subject: string; predicate: string; object: string; score?: number }
const props = withDefaults(defineProps<{ node?: string; triples?: EvidenceTriple[]; title?: string; emptyText?: string }>(), { node: '', triples: () => [], title: '证据三元组', emptyText: '当前真实图谱响应没有可展示的证据三元组。' })
const open = defineModel<boolean>({ default: false })
const emit = defineEmits<{ ask: [entity: string] }>()
</script>

<template>
  <aside class="evidence-drawer" :class="{ open }" aria-label="图谱证据详情">
    <header class="evidence-drawer-heading"><h3><Database :size="16" />{{ title }}</h3><button v-if="open" class="icon-button" aria-label="关闭证据详情" title="关闭" @click="open = false"><X :size="16" /></button><button v-else class="icon-button" type="button" aria-label="查看详情" title="查看证据详情" :aria-expanded="open" @click="open = true"><ChevronDown :size="17" /></button></header>
    <div v-if="open" class="evidence-drawer-body">
      <p v-if="node" class="evidence-focus">当前节点：<strong>{{ node }}</strong></p>
      <div v-if="props.triples.length" class="evidence-triples"><article v-for="triple in props.triples" :key="triple.subject + triple.predicate + triple.object" class="evidence-triple-card"><strong>{{ triple.subject }}</strong><span>{{ triple.predicate }}</span><strong>{{ triple.object }}</strong><small v-if="triple.score !== undefined">证据分 {{ Number(triple.score).toFixed(3) }}</small></article></div>
      <p v-else class="evidence-empty">{{ props.emptyText }}</p>
      <button v-if="node && props.triples.length" class="text-button" type="button" @click="emit('ask', node)"><Sparkles :size="14" />围绕此节点提问</button>
    </div>
  </aside>
</template>
