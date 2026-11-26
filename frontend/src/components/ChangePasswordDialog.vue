<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CircleAlert, KeyRound, X } from '@lucide/vue'
import { apiError, learningApi } from '../api/client'
import { resetSession } from '../state/session'
import { resetLearningMetrics } from '../composables/useLearningMetrics'

const props = withDefaults(defineProps<{
  modelValue?: boolean
}>(), {
  modelValue: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
  success: []
}>()

const router = useRouter()
const currentPasswordInput = ref<HTMLInputElement | null>(null)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const submitting = ref(false)

function resetForm() {
  currentPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  errorMessage.value = ''
  submitting.value = false
}

function handleClose() {
  if (submitting.value) return
  resetForm()
  emit('update:modelValue', false)
  emit('close')
}

let prevBodyOverflow: string | null = null

function onDialogKeydown(event: KeyboardEvent) {
  if (!props.modelValue || event.key !== 'Escape') return
  event.preventDefault()
  event.stopPropagation()
  handleClose()
}

function restoreBodyScroll() {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onDialogKeydown, true)
  }
  if (typeof document !== 'undefined' && prevBodyOverflow !== null) {
    document.body.style.overflow = prevBodyOverflow
    prevBodyOverflow = null
  }
}

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    resetForm()
    if (typeof document !== 'undefined') {
      if (prevBodyOverflow === null) prevBodyOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('keydown', onDialogKeydown, true)
    }
    void nextTick(() => {
      currentPasswordInput.value?.focus()
    })
  } else {
    restoreBodyScroll()
  }
})

onBeforeUnmount(restoreBodyScroll)

async function onSubmit() {
  if (submitting.value) return
  errorMessage.value = ''

  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    errorMessage.value = '请完整填写所有密码字段'
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的新密码不一致'
    return
  }

  const codePoints = Array.from(newPassword.value).length
  if (codePoints < 8) {
    errorMessage.value = '新密码长度至少需要 8 个字符'
    return
  }

  if (new TextEncoder().encode(newPassword.value).length > 72) {
    errorMessage.value = '新密码 UTF-8 字节长度不能超过 72'
    return
  }

  submitting.value = true
  try {
    await learningApi.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })

    // 成功后清理本地凭据与状态
    localStorage.removeItem('slp-access-token')
    localStorage.removeItem('slp-refresh-token')
    localStorage.removeItem('slp-user-name')
    localStorage.removeItem('slp-user-role')
    resetSession()
    resetLearningMetrics()

    emit('update:modelValue', false)
    emit('success')
    void router.replace('/login')
  } catch (err) {
    // 失败不回显密码、不清已输入新密码，当前旧密码可清
    currentPassword.value = ''
    errorMessage.value = apiError(err, '密码修改失败，请核对当前密码')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="modelValue" class="dialog-backdrop" @click.self="handleClose">
    <div
      class="dialog-content"
      role="dialog"
      aria-modal="true"
      aria-labelledby="change-password-title"
    >
      <header class="dialog-header">
        <div class="dialog-title-group">
          <span class="dialog-icon"><KeyRound :size="18" /></span>
          <h2 id="change-password-title">修改密码</h2>
        </div>
        <button
          type="button"
          class="dialog-close-button"
          aria-label="关闭对话框"
          :disabled="submitting"
          @click="handleClose"
        >
          <X :size="16" />
        </button>
      </header>

      <form class="dialog-form" @submit.prevent="onSubmit">
        <div v-if="errorMessage" class="dialog-error" role="alert">
          <CircleAlert :size="16" />
          <span>{{ errorMessage }}</span>
        </div>

        <div class="form-item">
          <label for="current-password-input">当前密码</label>
          <input
            id="current-password-input"
            ref="currentPasswordInput"
            v-model="currentPassword"
            type="password"
            autocomplete="current-password"
            placeholder="请输入当前密码"
            :disabled="submitting"
          />
        </div>

        <div class="form-item">
          <label for="new-password-input">新密码</label>
          <input
            id="new-password-input"
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            placeholder="至少 8 个字符"
            :disabled="submitting"
          />
        </div>

        <div class="form-item">
          <label for="confirm-password-input">确认新密码</label>
          <input
            id="confirm-password-input"
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            :disabled="submitting"
          />
        </div>

        <footer class="dialog-footer">
          <button
            type="button"
            class="button button-outline"
            :disabled="submitting"
            @click="handleClose"
          >
            取消
          </button>
          <button
            type="submit"
            class="button button-dark"
            :disabled="submitting"
          >
            {{ submitting ? '正在修改...' : '确认修改' }}
          </button>
        </footer>
      </form>
    </div>
  </div>
</template>

<style scoped>
.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 16px;
  background: var(--surface-overlay);
  backdrop-filter: blur(4px);
}

.dialog-content {
  width: 100%;
  max-width: 440px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-default);
}

.dialog-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dialog-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-sm);
}

.dialog-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.dialog-close-button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.dialog-close-button:hover:not(:disabled) {
  color: var(--text);
  background: var(--surface-hover);
}

.dialog-form {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.dialog-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--danger);
  background: var(--danger-soft);
  border: 1px solid var(--danger-border);
  border-radius: var(--radius-sm);
}

.form-item {
  display: grid;
  gap: 6px;
}

.form-item label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
}

.form-item input {
  height: 38px;
  padding: 0 12px;
  font-size: 14px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.form-item input:focus {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus, 0 0 0 2px var(--accent-soft));
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--border-default);
}
</style>
