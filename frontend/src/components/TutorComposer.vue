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
.tutor-composer{display:grid;gap:10px;min-width:0;padding:16px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-sm)}
.tutor-context-row,.tutor-quick-prompts,.tutor-composer-footer{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.tutor-context-caption,.tutor-quick-prompts>span,.tutor-composer-tip{color:var(--text-muted);font-size:12px}
.tutor-context-chip,.tutor-quick-prompt{display:inline-flex;align-items:center;gap:5px;max-width:100%;padding:4px 9px;border-radius:var(--radius-pill);font-size:12px;overflow-wrap:anywhere}
.tutor-context-chip{color:var(--accent-strong);background:var(--accent-soft)}
.tutor-quick-prompt{min-height:30px;color:var(--text-muted);background:var(--surface-soft);border-color:var(--border-soft);text-align:left}
.tutor-quick-prompt:hover{color:var(--accent-strong);background:var(--accent-soft)}
.tutor-composer-label{color:var(--text);font-size:13px;font-weight:650}
.tutor-composer textarea{width:100%;min-height:78px;background:var(--surface-soft);font-size:14px;line-height:1.6}
.tutor-composer textarea:focus{background:var(--surface);box-shadow:var(--focus-ring)}
.tutor-composer-tip{flex:1}
.compare-toggle{display:inline-flex;align-items:center;gap:7px;color:var(--text-muted);font-size:12px;cursor:pointer}
.compare-toggle input{position:absolute;width:1px;height:1px;opacity:0}
.toggle-track{position:relative;width:30px;height:18px;border-radius:var(--radius-pill);background:var(--border-strong);transition:background-color var(--motion)}
.toggle-track::after{position:absolute;top:3px;left:3px;width:12px;height:12px;border-radius:50%;background:var(--surface);content:"";transition:transform var(--motion)}
.compare-toggle input:checked+.toggle-track{background:var(--accent)}
.compare-toggle input:checked+.toggle-track::after{transform:translateX(12px)}
.compare-toggle input:focus-visible+.toggle-track{outline:2px solid var(--accent);outline-offset:2px}
@media(max-width:550px){.tutor-composer{padding:12px}.tutor-composer-tip{order:1;flex-basis:100%}.tutor-composer-footer>.button{margin-left:auto}}
</style>
