<script setup lang="ts">
import { onBeforeUnmount, ref, useId } from 'vue'
import { Network } from '@lucide/vue'
import type { NormalizedCitationTriple } from '../domain/citationAlignment'

const props = defineProps<{
  index: number
  triple?: NormalizedCitationTriple
  preview: string
}>()

const emit = defineEmits<{
  'select-citation': [index: number]
  'open-graph': [entity: string]
}>()

const tooltipId = useId()
const wrapRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const popoverOpen = ref(false)
const align = ref<'center' | 'start' | 'end'>('center')
const vertical = ref<'top' | 'bottom'>('top')

let closeTimer: ReturnType<typeof setTimeout> | null = null

function clearCloseTimer() {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
}

function updatePlacement() {
  if (typeof window === 'undefined' || !wrapRef.value) return
  const rect = wrapRef.value.getBoundingClientRect()
  if (rect.width === 0 && rect.height === 0) {
    align.value = 'center'
    vertical.value = 'top'
    return
  }
  const container = wrapRef.value.closest('.tutor-message-scroll, .tutor-answer-card, main')
  const bounds = container?.getBoundingClientRect()
  const leftEdge = bounds ? bounds.left : 0
  const rightEdge = bounds ? bounds.right : window.innerWidth
  const topEdge = bounds ? bounds.top : 0

  if (rect.left - leftEdge < 150) {
    align.value = 'start'
  } else if (rightEdge - rect.right < 150) {
    align.value = 'end'
  } else {
    align.value = 'center'
  }

  vertical.value = rect.top - topEdge < 120 ? 'bottom' : 'top'
}

function openPopover() {
  clearCloseTimer()
  updatePlacement()
  popoverOpen.value = true
}

function scheduleClose() {
  clearCloseTimer()
  closeTimer = setTimeout(() => {
    popoverOpen.value = false
  }, 110)
}

function closePopover(restoreFocus = false) {
  clearCloseTimer()
  popoverOpen.value = false
  if (restoreFocus && wrapRef.value?.contains(document.activeElement)) {
    triggerRef.value?.focus()
  }
}

function handleFocusOut(event: FocusEvent) {
  const nextTarget = event.relatedTarget as Node | null
  if (nextTarget && wrapRef.value?.contains(nextTarget)) return
  popoverOpen.value = false
}

function openGraphNode(entity: string) {
  closePopover(false)
  emit('open-graph', entity)
}

onBeforeUnmount(() => {
  clearCloseTimer()
})
</script>

<template>
  <span
    ref="wrapRef"
    class="citation-ref-wrap"
    @mouseenter="openPopover"
    @mouseleave="scheduleClose"
    @focusin="openPopover"
    @focusout="handleFocusOut"
    @keydown.esc.stop="closePopover(true)"
  >
    <button
      ref="triggerRef"
      type="button"
      class="inline-citation-btn"
      :aria-label="`查看图谱证据 [${index}]：${preview}`"
      :aria-describedby="tooltipId"
      :aria-expanded="popoverOpen ? 'true' : 'false'"
      :data-testid="`inline-citation-${index}`"
      @mouseenter="openPopover"
      @click="emit('select-citation', index)"
    >[{{ index }}]</button>
    <span
      :id="tooltipId"
      class="citation-hover-preview citation-hover-popover"
      :class="{
        'is-open': popoverOpen,
        'align-start': align === 'start',
        'align-end': align === 'end',
        'side-bottom': vertical === 'bottom',
      }"
      role="tooltip"
      :data-testid="`citation-popover-${index}`"
      @mouseenter="clearCloseTimer"
      @mouseleave="scheduleClose"
    >
      <span class="citation-popover-head">
        <strong>[{{ index }}]</strong>
        <span class="citation-popover-label">图谱证据预览</span>
      </span>
      <span v-if="triple" class="citation-popover-triple">
        <span class="citation-popover-subject">{{ triple.subject }}</span>
        <span class="citation-popover-predicate"> — {{ triple.predicate }} — </span>
        <span class="citation-popover-object">{{ triple.object }}</span>
      </span>
      <span v-else class="citation-popover-triple">{{ preview }}</span>
      <span class="citation-popover-footer">
        <span class="citation-popover-hint">点击 [{{ index }}] 高亮证据抽屉</span>
        <button
          v-if="triple?.subject"
          type="button"
          class="citation-popover-graph-link"
          :data-testid="`citation-popover-graph-${index}`"
          @click.stop="openGraphNode(triple.subject)"
        >
          <Network :size="12" aria-hidden="true" />
          <span>在图谱中查看</span>
        </button>
      </span>
    </span>
  </span>
</template>

<style scoped>
.citation-ref-wrap {
  position: relative;
  display: inline-block;
  margin-left: 2px;
  vertical-align: baseline;
}

.inline-citation-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 18px;
  padding: 0 4px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 24%, transparent);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.2;
  vertical-align: super;
  cursor: pointer;
  transition: background-color var(--motion), border-color var(--motion);
}

.inline-citation-btn:hover,
.inline-citation-btn:focus-visible {
  background: color-mix(in srgb, var(--accent) 18%, var(--surface));
  border-color: var(--border-focus);
  outline: none;
}

.citation-hover-preview,
.citation-hover-popover {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 6px);
  transform: translateX(-50%);
  z-index: 40;
  display: none;
  gap: 6px;
  width: max-content;
  min-width: 220px;
  max-width: min(320px, 82vw);
  padding: 10px 12px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-md);
  font-size: 12px;
  font-weight: 450;
  line-height: 1.45;
  white-space: normal;
  overflow-wrap: anywhere;
  pointer-events: auto;
}

/* Hover bridge between trigger button and popover so moving pointer up to graph link never drops focus */
.citation-hover-popover::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  height: 10px;
}

.citation-hover-popover.side-bottom {
  bottom: auto;
  top: calc(100% + 6px);
}

.citation-hover-popover.side-bottom::after {
  top: auto;
  bottom: 100%;
}

.citation-hover-popover.align-start {
  left: 0;
  transform: none;
}

.citation-hover-popover.align-end {
  left: auto;
  right: 0;
  transform: none;
}

.citation-hover-popover.is-open {
  display: grid;
}

.citation-popover-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
}

.citation-popover-head strong {
  color: var(--accent-strong);
  font-weight: 700;
}

.citation-popover-label {
  color: var(--text-muted);
  font-weight: 600;
}

.citation-popover-triple {
  display: block;
  padding: 6px 8px;
  color: var(--text);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-xs);
  line-height: 1.5;
}

.citation-popover-subject {
  font-weight: 600;
}

.citation-popover-predicate {
  color: var(--accent-strong);
}

.citation-popover-object {
  color: var(--text-muted);
}

.citation-popover-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.citation-popover-hint {
  color: var(--text-muted);
  font-size: 11px;
}

.citation-popover-graph-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 24px;
  padding: 2px 8px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 26%, transparent);
  border-radius: var(--radius-xs);
  font-size: 11.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--motion), border-color var(--motion);
}

.citation-popover-graph-link:hover,
.citation-popover-graph-link:focus-visible {
  background: color-mix(in srgb, var(--accent) 20%, var(--surface));
  border-color: var(--border-focus);
  outline: none;
}
</style>
