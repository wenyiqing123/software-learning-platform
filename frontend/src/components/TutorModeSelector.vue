<script setup lang="ts">
import type { Component } from 'vue'
import { Check } from '@lucide/vue'

export interface TutorModeOption { key: string; title: string; note: string; icon: Component }
defineProps<{ mode: string; compareAll: boolean; modes: TutorModeOption[] }>()
const emit = defineEmits<{ select: [mode: string] }>()
</script>

<template>
  <div class="mode-selector tutor-mode-selector" role="group" aria-label="问答模式">
    <button v-for="item in modes" :key="item.key" class="mode-option" :class="{ selected: mode === item.key }" :aria-pressed="mode === item.key && !compareAll" @click="emit('select', item.key)">
      <span class="mode-icon"><component :is="item.icon" :size="17" /></span>
      <span><strong>{{ item.title }}</strong><small>{{ item.note }}</small></span>
      <Check v-if="mode === item.key && !compareAll" class="mode-check" :size="16" />
    </button>
  </div>
</template>

<style scoped>
.tutor-mode-selector{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.tutor-mode-selector .mode-option{min-width:0;min-height:58px;display:flex;align-items:center;gap:9px;padding:10px 11px}.tutor-mode-selector .mode-option>span:nth-child(2){min-width:0;display:grid;gap:3px;text-align:left}.tutor-mode-selector small{color:var(--text-muted);font-size:10px}.tutor-mode-selector .mode-check{margin-left:auto;color:var(--accent)}
@media(max-width:640px){.tutor-mode-selector{grid-template-columns:1fr}.tutor-mode-selector .mode-option{min-height:50px}}
</style>
