<script setup lang="ts">
import type { Component } from 'vue'
import { Check } from '@lucide/vue'

export interface TutorModeOption { key: string; title: string; note: string; icon: Component }
defineProps<{ mode: string; compareAll: boolean; modes: TutorModeOption[] }>()
const emit = defineEmits<{ select: [mode: string] }>()
</script>

<template>
  <div class="mode-selector tutor-mode-selector" role="group" aria-label="问答模式">
    <button v-for="item in modes" :key="item.key" type="button" class="mode-option" :class="{ selected: mode === item.key && !compareAll }" :aria-pressed="mode === item.key && !compareAll" @click="emit('select', item.key)">
      <span class="mode-icon"><component :is="item.icon" :size="17" /></span>
      <span><strong>{{ item.title }}</strong><small>{{ item.note }}</small></span>
      <Check v-if="mode === item.key && !compareAll" class="mode-check" :size="16" />
    </button>
  </div>
</template>

<style scoped>
.tutor-mode-selector{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:4px;padding:4px;background:var(--surface-tint);border-radius:var(--radius-sm)}
.mode-option{display:flex;align-items:center;gap:8px;min-width:0;min-height:52px;padding:7px 9px;color:var(--text-muted);border:1px solid transparent;background:transparent;text-align:left}
.mode-option:hover,.mode-option.selected{color:var(--accent-strong);background:var(--surface)}
.mode-option.selected{border-color:var(--border-default);box-shadow:var(--shadow-xs)}
.mode-icon,.mode-check{display:inline-flex;flex:none;color:var(--accent)}
.mode-option>span:nth-child(2){min-width:0;flex:1;display:grid;overflow-wrap:anywhere}
.mode-option strong{font-size:13px}.mode-option small{font-size:12px;line-height:1.45}
@media(max-width:530px){.tutor-mode-selector{grid-template-columns:1fr}.mode-option{min-height:42px}}
</style>
