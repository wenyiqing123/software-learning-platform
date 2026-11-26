<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { AlertTriangle, ShieldAlert, X } from '@lucide/vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'danger' | 'warning' | 'default'
  busy?: boolean
}>(), {
  confirmLabel: '确认',
  cancelLabel: '取消',
  tone: 'danger',
  busy: false,
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const confirmBtnRef = ref<HTMLButtonElement | null>(null)
let prevBodyOverflow: string | null = null
let prevFocusedEl: HTMLElement | null = null

function handleCancel() {
  if (props.busy) return
  emit('cancel')
}

function handleConfirm() {
  if (props.busy) return
  emit('confirm')
}

function onWindowKeydown(event: KeyboardEvent) {
  if (!props.open || event.key !== 'Escape') return
  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()
  handleCancel()
}

function restoreEnvironment() {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onWindowKeydown, true)
  }
  if (typeof document !== 'undefined' && prevBodyOverflow !== null) {
    document.body.style.overflow = prevBodyOverflow
    prevBodyOverflow = null
  }
  if (prevFocusedEl && typeof prevFocusedEl.focus === 'function') {
    prevFocusedEl.focus()
    prevFocusedEl = null
  }
}

watch(() => props.open, async (isOpen) => {
  if (isOpen) {
    if (typeof document !== 'undefined') {
      prevFocusedEl = document.activeElement instanceof HTMLElement ? document.activeElement : null
      if (prevBodyOverflow === null) {
        prevBodyOverflow = document.body.style.overflow
      }
      document.body.style.overflow = 'hidden'
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('keydown', onWindowKeydown, true)
    }
    await nextTick()
    confirmBtnRef.value?.focus()
  } else {
    restoreEnvironment()
  }
}, { immediate: true })

onBeforeUnmount(restoreEnvironment)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="confirm-dialog-backdrop"
      data-testid="confirm-dialog-backdrop"
      @click.self="handleCancel"
    >
      <div
        class="confirm-dialog-card"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        data-testid="confirm-dialog"
      >
        <header class="confirm-dialog-head">
          <div class="confirm-dialog-title">
            <span class="confirm-dialog-icon" :class="`tone-${tone}`" aria-hidden="true">
              <AlertTriangle v-if="tone === 'warning'" :size="16" />
              <ShieldAlert v-else :size="16" />
            </span>
            <h3>{{ title }}</h3>
          </div>
          <button
            type="button"
            class="icon-button"
            aria-label="关闭确认窗口"
            :disabled="busy"
            @click="handleCancel"
          >
            <X :size="16" />
          </button>
        </header>

        <div class="confirm-dialog-body">
          <p class="confirm-dialog-message">{{ message }}</p>
        </div>

        <footer class="confirm-dialog-actions">
          <button
            type="button"
            class="button button-outline"
            :disabled="busy"
            data-testid="confirm-dialog-cancel"
            @click="handleCancel"
          >
            {{ cancelLabel }}
          </button>
          <button
            ref="confirmBtnRef"
            type="button"
            class="button"
            :class="tone === 'danger' ? 'button-danger' : 'button-dark'"
            :disabled="busy"
            data-testid="confirm-dialog-confirm"
            @click="handleConfirm"
          >
            {{ busy ? '处理中…' : confirmLabel }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  padding: 16px;
  background: var(--surface-overlay);
  backdrop-filter: blur(2px);
}

.confirm-dialog-card {
  width: min(440px, 100%);
  display: grid;
  gap: 14px;
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.confirm-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.confirm-dialog-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.confirm-dialog-title h3 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
}

.confirm-dialog-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.confirm-dialog-icon.tone-danger {
  color: var(--danger);
  background: var(--danger-soft);
}

.confirm-dialog-icon.tone-warning {
  color: var(--warning-strong);
  background: var(--warning-soft);
}

.confirm-dialog-body {
  min-width: 0;
}

.confirm-dialog-message {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.confirm-dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 4px;
}
</style>
