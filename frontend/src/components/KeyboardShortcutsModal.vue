<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Keyboard, X } from '@lucide/vue'
import { getShortcutGroups } from '../domain/shortcuts'
import KbdBadge from './KbdBadge.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    role?: string
  }>(),
  {
    role: 'STUDENT',
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const closeBtnRef = ref<HTMLButtonElement | null>(null)
const groups = computed(() => getShortcutGroups(props.role))

let prevBodyOverflow: string | null = null
let prevFocusedEl: HTMLElement | null = null

function closeModal() {
  emit('update:open', false)
}

function onWindowKeydown(event: KeyboardEvent) {
  if (!props.open || event.key !== 'Escape') return
  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()
  closeModal()
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

watch(
  () => props.open,
  async (isOpen) => {
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
      closeBtnRef.value?.focus()
    } else {
      restoreEnvironment()
    }
  },
)

onBeforeUnmount(restoreEnvironment)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="shortcuts-backdrop"
      data-testid="shortcuts-help-backdrop"
      @click.self="closeModal"
    >
      <section
        class="shortcuts-card"
        role="dialog"
        aria-modal="true"
        aria-label="全局快捷键速查"
        data-testid="shortcuts-help-dialog"
      >
        <header class="shortcuts-head">
          <div class="shortcuts-title">
            <span class="shortcuts-icon" aria-hidden="true">
              <Keyboard :size="17" />
            </span>
            <div>
              <h2>快捷键速查</h2>
              <p>在非输入焦点下按 <KbdBadge :keys="['?']" /> 随时唤起或关闭</p>
            </div>
          </div>
          <button
            ref="closeBtnRef"
            type="button"
            class="shortcuts-close"
            aria-label="关闭快捷键速查弹窗"
            data-testid="shortcuts-help-close"
            @click="closeModal"
          >
            <X :size="16" />
          </button>
        </header>

        <div class="shortcuts-body">
          <section v-for="group in groups" :key="group.id" class="shortcuts-group">
            <h3>{{ group.title }}</h3>
            <ul class="shortcuts-list">
              <li v-for="(item, idx) in group.items" :key="`${group.id}-${idx}`" class="shortcuts-row">
                <span class="shortcuts-desc">{{ item.label }}</span>
                <KbdBadge :keys="item.keys" :separator="item.separator" />
              </li>
            </ul>
          </section>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.shortcuts-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2050;
  display: grid;
  place-items: center;
  padding: 16px;
  background: var(--surface-overlay);
}

.shortcuts-card {
  width: min(620px, 100%);
  max-height: min(82dvh, 660px);
  display: flex;
  flex-direction: column;
  background: var(--surface);
  color: var(--text);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.shortcuts-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--border-soft);
}

.shortcuts-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.shortcuts-title h2 {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
}

.shortcuts-title p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.shortcuts-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.shortcuts-close {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex: none;
  color: var(--text-muted);
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.shortcuts-close:hover {
  color: var(--accent-strong);
  border-color: var(--border-focus);
}

.shortcuts-body {
  display: grid;
  gap: 12px;
  padding: 16px 18px 18px;
  overflow-y: auto;
}

.shortcuts-group {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.shortcuts-group h3 {
  margin: 0;
  color: var(--text);
  font-size: 13px;
  font-weight: 650;
}

.shortcuts-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.shortcuts-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border-soft);
}

.shortcuts-row:first-child {
  padding-top: 0;
  border-top: 0;
}

.shortcuts-desc {
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}
</style>
