<script setup lang="ts">
import { ref } from 'vue'
import { Check, MessageSquare, MessageSquarePlus, PanelLeftClose, Pencil, Plus, Trash2, X } from '@lucide/vue'
import type { TutorSession } from '../api/tutor'

withDefaults(defineProps<{
  sessions: TutorSession[]
  activeId: number | null
  disabled?: boolean
  error?: string
  collapsible?: boolean
}>(), {
  collapsible: true,
})

const emit = defineEmits<{
  select: [id: number]
  create: []
  rename: [id: number, title: string]
  remove: [id: number]
  collapse: []
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
    <div class="tutor-sidebar-header">
      <button
        class="tutor-new-chat-btn"
        type="button"
        title="新建学习会话"
        aria-label="新建学习会话"
        data-testid="tutor-new-session"
        :disabled="disabled"
        @click="emit('create')"
      >
        <Plus :size="15" />
        <span>新建对话</span>
      </button>
      <button
        v-if="collapsible"
        class="icon-button tutor-collapse-btn"
        type="button"
        title="收起会话侧栏"
        aria-label="收起会话侧栏"
        data-testid="tutor-collapse-sidebar"
        @click="emit('collapse')"
      >
        <PanelLeftClose :size="16" />
      </button>
    </div>

    <p v-if="error" class="tutor-conversation-error" role="alert">{{ error }}</p>

    <div v-if="!sessions.length" class="tutor-conversation-empty">
      <MessageSquarePlus :size="20" />
      <strong>还没有学习会话</strong>
      <span>在右侧提出你的第一个问题，对话记录会自动保存在这里。</span>
    </div>

    <div v-else class="tutor-conversation-body">
      <p class="tutor-sidebar-caption">近期会话</p>
      <nav class="tutor-conversation-items" aria-label="历史会话列表">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="tutor-conversation-item"
          :class="{ active: activeId === session.id }"
        >
          <form v-if="editingId === session.id" class="tutor-session-rename" @submit.prevent="submitRename(session.id)">
            <input v-model="draftTitle" data-testid="tutor-rename-input" maxlength="120" aria-label="会话标题" />
            <button
              class="icon-button tutor-action-icon"
              type="submit"
              title="保存会话标题"
              aria-label="保存会话标题"
              data-testid="tutor-rename-submit"
              :disabled="disabled || !draftTitle.trim()"
            >
              <Check :size="14" />
            </button>
            <button
              class="icon-button tutor-action-icon"
              type="button"
              title="取消重命名"
              aria-label="取消重命名"
              @click="cancelRename"
            >
              <X :size="14" />
            </button>
          </form>
          <template v-else>
            <button
              class="tutor-session-select"
              type="button"
              :title="session.title"
              :aria-current="activeId === session.id ? 'true' : undefined"
              :disabled="disabled"
              @click="emit('select', session.id)"
            >
              <MessageSquare :size="14" class="tutor-session-icon" aria-hidden="true" />
              <span class="tutor-session-title">{{ session.title }}</span>
            </button>
            <div class="tutor-session-actions">
              <button
                class="icon-button tutor-action-icon"
                type="button"
                title="重命名会话"
                :aria-label="`重命名${session.title}`"
                :data-testid="`tutor-rename-${session.id}`"
                :disabled="disabled"
                @click.stop="beginRename(session)"
              >
                <Pencil :size="13" />
              </button>
              <button
                class="icon-button tutor-action-icon tutor-action-danger"
                type="button"
                title="删除会话"
                :aria-label="`删除${session.title}`"
                :data-testid="`tutor-delete-${session.id}`"
                :disabled="disabled"
                @click.stop="emit('remove', session.id)"
              >
                <Trash2 :size="13" />
              </button>
            </div>
          </template>
        </div>
      </nav>
    </div>
  </aside>
</template>

<style scoped>
.tutor-conversation-list {
  display: flex;
  flex-direction: column;
  width: 256px;
  height: 100%;
  min-width: 256px;
  min-height: 0;
  padding: 12px;
  background: var(--surface-soft);
}

@media (max-width: 820px) {
  .tutor-conversation-list {
    width: 100%;
    min-width: 0;
  }
}

.tutor-sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.tutor-new-chat-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex: 1;
  min-width: 0;
  min-height: 36px;
  padding: 0 12px;
  color: var(--accent-strong);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  box-shadow: var(--shadow-xs);
  cursor: pointer;
}

.tutor-new-chat-btn:hover:not(:disabled) {
  background: var(--accent-soft);
  border-color: var(--border-focus);
}

.tutor-collapse-btn {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
}

.tutor-collapse-btn:hover {
  color: var(--accent-strong);
  background: var(--surface-tint);
}

.tutor-conversation-error {
  margin: 10px 0 0;
  padding: 8px 10px;
  color: var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius-sm);
  font-size: 12px;
  flex-shrink: 0;
}

.tutor-conversation-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin-top: 14px;
}

.tutor-sidebar-caption {
  margin: 0 0 6px;
  padding: 0 8px;
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: 650;
  flex-shrink: 0;
}

.tutor-conversation-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
  scrollbar-width: thin;
  scrollbar-color: var(--border-default) transparent;
}

.tutor-conversation-item {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: 38px;
  border-radius: var(--radius-sm);
  transition: background-color var(--motion);
}

.tutor-conversation-item:hover,
.tutor-conversation-item:focus-within {
  background: var(--surface-tint);
}

.tutor-conversation-item.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.tutor-session-select {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  min-height: 36px;
  padding: 6px 10px;
  color: inherit;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  text-align: left;
  cursor: pointer;
}

.tutor-session-select:hover:not(:disabled) {
  background: transparent;
}

.tutor-conversation-item:hover .tutor-session-select,
.tutor-conversation-item:focus-within .tutor-session-select {
  padding-right: 60px;
}

.tutor-session-icon {
  flex-shrink: 0;
  color: var(--text-subtle);
}

.active .tutor-session-icon {
  color: var(--accent);
}

.tutor-session-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.4;
}

.active .tutor-session-title {
  font-weight: 650;
}

.tutor-session-actions {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 2px;
  padding-left: 6px;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--motion);
}

.tutor-conversation-item:hover .tutor-session-actions,
.tutor-conversation-item:focus-within .tutor-session-actions {
  opacity: 1;
  pointer-events: auto;
}

.tutor-action-icon {
  width: 26px;
  height: 26px;
  min-height: 26px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  flex-shrink: 0;
}

.tutor-action-danger:hover:not(:disabled) {
  color: var(--danger);
  background: var(--danger-soft);
}

.tutor-session-rename {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 4px 6px;
}

.tutor-session-rename input {
  flex: 1;
  min-width: 0;
  min-height: 30px;
  padding: 3px 8px;
  font-size: 13px;
}

.tutor-conversation-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  margin: auto 0;
  padding: 24px 12px;
  color: var(--text-muted);
  text-align: center;
  font-size: 12px;
  line-height: 1.6;
}

.tutor-conversation-empty svg {
  color: var(--accent);
  opacity: 0.75;
}

.tutor-conversation-empty strong {
  color: var(--text);
  font-size: 13px;
}

@media (hover: none) {
  .tutor-session-select {
    padding-right: 60px;
  }

  .tutor-session-actions {
    opacity: 1;
    pointer-events: auto;
  }
}
</style>
