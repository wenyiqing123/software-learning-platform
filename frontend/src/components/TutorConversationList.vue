<script setup lang="ts">
import { ref } from 'vue'
import { Check, MessageSquarePlus, Pencil, Trash2, X } from '@lucide/vue'
import type { TutorSession } from '../api/tutor'

defineProps<{
  sessions: TutorSession[]
  activeId: number | null
  disabled?: boolean
  error?: string
}>()

const emit = defineEmits<{
  select: [id: number]
  create: []
  rename: [id: number, title: string]
  remove: [id: number]
}>()

const editingId = ref<number | null>(null)
const draftTitle = ref('')

function beginRename(session: TutorSession) {
  editingId.value = session.id
  draftTitle.value = session.title
}

function cancelRename() {
  editingId.value = null
  draftTitle.value = ''
}

function submitRename(id: number) {
  const title = draftTitle.value.trim()
  if (!title) return
  emit('rename', id, title)
  cancelRename()
}
</script>

<template>
  <aside class="tutor-conversation-list" aria-label="学习会话列表">
    <div class="tutor-conversation-heading">
      <div>
        <p class="eyebrow">CONVERSATIONS</p>
        <h2>学习会话</h2>
      </div>
      <button
        class="icon-button"
        type="button"
        title="新建学习会话"
        aria-label="新建学习会话"
        data-testid="tutor-new-session"
        :disabled="disabled"
        @click="emit('create')"
      >
        <MessageSquarePlus :size="16" />
      </button>
    </div>

    <p v-if="error" class="tutor-conversation-error" role="alert">{{ error }}</p>

    <div v-if="!sessions.length" class="tutor-conversation-empty">
      <MessageSquarePlus :size="18" />
      <strong>还没有学习会话</strong>
      <span>新建会话后，课程问答会自动保存在这里。</span>
      <button class="button button-outline button-small" type="button" :disabled="disabled" @click="emit('create')">开始学习</button>
    </div>

    <nav v-else class="tutor-conversation-items">
      <div v-for="session in sessions" :key="session.id" class="tutor-conversation-item" :class="{ active: activeId === session.id }">
        <form v-if="editingId === session.id" class="tutor-session-rename" @submit.prevent="submitRename(session.id)">
          <input v-model="draftTitle" data-testid="tutor-rename-input" maxlength="120" aria-label="会话标题" />
          <button class="icon-button icon-button-small" type="submit" title="保存会话标题" aria-label="保存会话标题" data-testid="tutor-rename-submit" :disabled="disabled || !draftTitle.trim()"><Check :size="14" /></button>
          <button class="icon-button icon-button-small" type="button" title="取消重命名" aria-label="取消重命名" @click="cancelRename"><X :size="14" /></button>
        </form>
        <template v-else>
          <button class="tutor-session-select" type="button" :disabled="disabled" @click="emit('select', session.id)">
            <span class="tutor-session-dot" aria-hidden="true"></span>
            <span class="tutor-session-title">{{ session.title }}</span>
          </button>
          <div class="tutor-session-actions">
            <button class="icon-button icon-button-small" type="button" title="重命名会话" :aria-label="`重命名${session.title}`" :data-testid="`tutor-rename-${session.id}`" :disabled="disabled" @click="beginRename(session)"><Pencil :size="13" /></button>
            <button class="icon-button icon-button-small danger" type="button" title="删除会话" :aria-label="`删除${session.title}`" :data-testid="`tutor-delete-${session.id}`" :disabled="disabled" @click="emit('remove', session.id)"><Trash2 :size="13" /></button>
          </div>
        </template>
      </div>
    </nav>
  </aside>
</template>

<style scoped>
.tutor-conversation-list{display:grid;align-content:start;gap:14px;min-width:0;padding:18px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface);box-shadow:var(--shadow-sm)}
.tutor-conversation-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.tutor-conversation-heading .eyebrow{margin:0 0 4px}.tutor-conversation-heading h2{margin:0;color:var(--ink);font-size:15px}
.tutor-conversation-error{margin:0;padding:9px 10px;color:var(--danger);border:1px solid var(--danger-border);border-radius:var(--radius-sm);background:var(--danger-soft);font-size:11px;line-height:1.55}
.tutor-conversation-items{display:grid;gap:5px;max-height:430px;overflow:auto;padding-right:2px}.tutor-conversation-item{display:flex;align-items:center;gap:4px;min-width:0;padding:4px;border:1px solid transparent;border-radius:var(--radius-sm);transition:background-color 150ms ease,border-color 150ms ease}.tutor-conversation-item:hover,.tutor-conversation-item.active{border-color:var(--accent-border);background:var(--accent-soft)}
.tutor-session-select{display:flex;align-items:center;gap:8px;min-width:0;flex:1;padding:8px;border:0;background:transparent;color:var(--text-muted);text-align:left;cursor:pointer}.tutor-session-select:disabled{cursor:not-allowed;opacity:.6}.tutor-session-dot{width:6px;height:6px;flex:0 0 auto;border-radius:50%;background:var(--border-strong)}.active .tutor-session-dot{background:var(--accent)}.tutor-session-title{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.active .tutor-session-title{color:var(--ink);font-weight:650}
.tutor-session-actions{display:flex;gap:2px;opacity:0;transition:opacity 150ms ease}.tutor-conversation-item:hover .tutor-session-actions,.tutor-conversation-item.active .tutor-session-actions{opacity:1}.icon-button{display:inline-grid;place-items:center;width:30px;height:30px;padding:0;border:1px solid var(--line);border-radius:var(--radius-sm);background:var(--surface);color:var(--text-muted);cursor:pointer;transition:color 150ms ease,background-color 150ms ease,border-color 150ms ease}.icon-button:hover{border-color:var(--accent-border);background:var(--accent-soft);color:var(--accent)}.icon-button:disabled{cursor:not-allowed;opacity:.55}.icon-button-small{width:25px;height:25px}.icon-button.danger:hover{border-color:var(--danger-border);background:var(--danger-soft);color:var(--danger)}
.tutor-session-rename{display:flex;align-items:center;gap:4px;width:100%;padding:3px}.tutor-session-rename input{min-width:0;flex:1;height:30px;padding:0 8px;border:1px solid var(--accent-border);border-radius:var(--radius-sm);background:var(--surface);color:var(--ink);font:inherit;font-size:12px}.tutor-conversation-empty{display:grid;justify-items:center;gap:8px;padding:22px 10px;color:var(--text-muted);text-align:center;background:var(--surface-soft);border:1px dashed var(--line);border-radius:var(--radius-sm)}.tutor-conversation-empty strong{color:var(--ink);font-size:12px}.tutor-conversation-empty span{font-size:10px;line-height:1.55}
@media(max-width:860px){.tutor-conversation-list{padding:14px}.tutor-conversation-items{max-height:250px}}
</style>
