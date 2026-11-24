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
          <button class="tutor-session-select" type="button" :title="session.title" :aria-current="activeId === session.id ? 'true' : undefined" :disabled="disabled" @click="emit('select', session.id)">
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
.tutor-conversation-list{display:grid;align-content:start;gap:12px;min-width:0;padding:14px;border:1px solid var(--border-default);border-radius:var(--radius-md);background:var(--surface)}
.tutor-conversation-heading,.tutor-conversation-item,.tutor-session-select,.tutor-session-rename,.tutor-session-actions{display:flex;align-items:center;gap:4px}
.tutor-conversation-heading{justify-content:space-between}
.tutor-conversation-heading h2{margin:0;font-size:16px}
.tutor-conversation-error{margin:0;padding:8px;color:var(--danger);background:var(--danger-soft);border-radius:var(--radius-sm);font-size:12px}
.tutor-conversation-items{display:grid;gap:4px;max-height:360px;overflow:auto}
.tutor-conversation-item{min-width:0;padding-inline:4px;border-radius:var(--radius-sm)}
.tutor-conversation-item:focus-within,.tutor-conversation-item:hover{background:var(--surface-soft)}
.tutor-conversation-item.active{color:var(--accent-strong);background:var(--accent-soft);box-shadow:inset 2px 0 var(--accent)}
.tutor-session-select{min-width:0;flex:1;padding:8px 4px;border:0;background:transparent;text-align:left}
.tutor-session-select:hover{background:transparent}
.tutor-session-dot{width:6px;height:6px;flex:none;border-radius:50%;background:var(--border-strong)}
.active .tutor-session-dot{background:var(--accent)}
.tutor-session-title{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}
.active .tutor-session-title{font-weight:650}
.icon-button-small{width:26px;height:28px;padding:0;flex:none}
.tutor-session-actions .icon-button,.tutor-session-rename .icon-button{width:26px;height:28px;padding:0;flex:none}
.tutor-session-actions .danger:hover{color:var(--danger);background:var(--danger-soft)}
.tutor-session-rename{width:100%;padding-block:4px}
.tutor-session-rename input{min-width:0;flex:1;font-size:13px}
.tutor-conversation-empty{display:grid;justify-items:start;gap:6px;color:var(--text-muted);font-size:12px}
.tutor-conversation-empty strong{color:var(--text);font-size:13px}
@media(max-width:760px){.tutor-conversation-items{max-height:170px}}
</style>
