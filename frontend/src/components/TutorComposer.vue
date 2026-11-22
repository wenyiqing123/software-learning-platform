<script setup lang="ts">
import { computed } from 'vue'
import { Send, Square } from '@lucide/vue'
import TutorModeSelector, { type TutorModeOption } from './TutorModeSelector.vue'
import type { TutorContextChip } from '../domain/tutorChat'

const props = defineProps<{
  question: string
  mode: string
  compareAll: boolean
  loading: boolean
  context?: TutorContextChip[]
  quickPrompts: string[]
  modes: TutorModeOption[]
}>()

const emit = defineEmits<{
  'update:question': [value: string]
  'update:compareAll': [value: boolean]
  'select-mode': [mode: string]
  submit: []
  stop: []
  'quick-prompt': [value: string]
}>()

const canSubmit = computed(() => !props.loading && Boolean(props.question.trim()))

function onInput(event: Event) {
  emit('update:question', (event.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <section class="tutor-composer" aria-label="学习助手输入区">
    <div v-if="context?.length" class="tutor-context-row" aria-label="当前学习上下文">
      <span class="tutor-context-caption">当前上下文</span>
      <span v-for="item in context" :key="item.label + item.value" class="tutor-context-chip"><strong>{{ item.label }}</strong>{{ item.value }}</span>
    </div>

    <div v-if="quickPrompts.length" class="tutor-quick-prompts" aria-label="快捷提问">
      <span>可以这样问</span>
      <button v-for="prompt in quickPrompts" :key="prompt" class="tutor-quick-prompt" type="button" data-testid="tutor-quick-prompt" :disabled="loading" @click="emit('quick-prompt', prompt)">{{ prompt }}</button>
    </div>

    <TutorModeSelector :mode="mode" :compare-all="compareAll" :modes="modes" @select="emit('select-mode', $event)" />

    <label class="tutor-composer-label" for="question-input">你的问题</label>
    <textarea id="question-input" :value="question" rows="4" maxlength="2000" placeholder="例如：软件需求规格说明书包含哪些内容？" :disabled="loading" @input="onInput" @keydown.enter.exact.prevent="emit('submit')"></textarea>

    <div class="tutor-composer-footer">
      <label class="compare-toggle"><input :checked="compareAll" type="checkbox" :disabled="loading" @change="emit('update:compareAll', ($event.target as HTMLInputElement).checked)" /><span class="toggle-track"></span><span>对比三种方式</span></label>
      <span class="tutor-composer-tip">回答会结合软件工程课程知识</span>
      <button v-if="loading" class="button button-outline" type="button" data-testid="tutor-stop" @click="emit('stop')"><Square :size="14" />停止生成</button>
      <button v-else class="button button-dark" type="button" data-testid="tutor-send" :disabled="!canSubmit" @click="emit('submit')">发送问题<Send :size="15" /></button>
    </div>
  </section>
</template>

<style scoped>
.tutor-composer{display:grid;gap:12px;padding:16px 18px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface);box-shadow:var(--shadow-sm)}.tutor-context-row,.tutor-quick-prompts{display:flex;align-items:center;flex-wrap:wrap;gap:7px}.tutor-context-caption,.tutor-quick-prompts>span{color:var(--text-subtle);font-size:10px}.tutor-context-chip{display:inline-flex;align-items:center;gap:5px;padding:5px 8px;border:1px solid var(--accent-border);border-radius:999px;background:var(--accent-soft);color:var(--accent);font-size:10px}.tutor-context-chip strong{font-weight:700}.tutor-quick-prompt{padding:5px 9px;border:1px solid var(--line);border-radius:999px;background:var(--surface-soft);color:var(--text-muted);font-size:10px;cursor:pointer;transition:color 150ms ease,border-color 150ms ease,background-color 150ms ease}.tutor-quick-prompt:hover{border-color:var(--accent-border);background:var(--accent-soft);color:var(--accent)}.tutor-quick-prompt:disabled{cursor:not-allowed;opacity:.55}.tutor-composer-label{color:var(--ink);font-size:12px;font-weight:650}.tutor-composer textarea{width:100%;min-height:104px;resize:vertical}.tutor-composer-footer{display:flex;align-items:center;flex-wrap:wrap;gap:10px}.tutor-composer-tip{flex:1;color:var(--text-subtle);font-size:10px}.compare-toggle{display:inline-flex;align-items:center;gap:7px;color:var(--text-muted);font-size:10px}.compare-toggle input{position:absolute;width:1px;height:1px;opacity:0}.toggle-track{position:relative;width:28px;height:16px;border-radius:999px;background:var(--border-strong);transition:background-color 150ms ease}.toggle-track::after{position:absolute;top:3px;left:3px;width:10px;height:10px;border-radius:50%;background:var(--surface);content:"";transition:transform 150ms ease}.compare-toggle input:checked + .toggle-track{background:var(--accent)}.compare-toggle input:checked + .toggle-track::after{transform:translateX(12px)}
@media(max-width:640px){.tutor-composer{padding:14px}.tutor-composer-footer{align-items:stretch}.tutor-composer-tip{order:3;width:100%;flex-basis:100%}.tutor-composer-footer .button{flex:1;justify-content:center}}
</style>
