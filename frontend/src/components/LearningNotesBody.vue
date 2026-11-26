<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, FilePlus2, Pencil, Plus, Save, Trash2, X } from '@lucide/vue'
import type { LearningNote } from '../api/client'
import { MAX_NOTE_TAGS, parseNoteTags, SYSTEM_NOTE_TAGS, toggleNoteTag } from '../domain/noteTags'

const props = defineProps<{
  notes: LearningNote[]
  loading: boolean
  saving: boolean
  editingId: number | null
  error: string
  notice: string
  title: string
  content: string
  tags: string
  quickTags?: string[]
}>()

const emit = defineEmits<{
  'update:title': [value: string]
  'update:content': [value: string]
  'update:tags': [value: string]
  submit: []
  'cancel-edit': []
  edit: [note: LearningNote]
  remove: [note: LearningNote]
}>()

const contentInputRef = ref<HTMLTextAreaElement | null>(null)
// 本地同步副本确保同一渲染帧内连续点击多个系统标签不会因父组件 prop 尚未刷新而相互覆盖
const liveTags = ref(props.tags)
watch(() => props.tags, (next) => {
  liveTags.value = next
})

const resolvedQuickTags = computed(() => (props.quickTags?.length ? props.quickTags : [...SYSTEM_NOTE_TAGS]))
const selectedTags = computed(() => parseNoteTags(liveTags.value))
const selectedTagSet = computed(() => new Set(selectedTags.value))

function handleToggleQuickTag(tag: string) {
  const next = toggleNoteTag(liveTags.value, tag)
  liveTags.value = next
  emit('update:tags', next)
}

function handleTagsInput(event: Event) {
  const next = (event.target as HTMLInputElement).value
  liveTags.value = next
  emit('update:tags', next)
}

function focusContent() {
  contentInputRef.value?.focus()
}

defineExpose({ focusContent })
</script>

<template>
  <div class="notes-body">
    <div v-if="error" class="notes-message error" role="alert">{{ error }}</div>
    <div v-if="notice" class="notes-message success" role="status">{{ notice }}</div>
    <form class="notes-editor" @submit.prevent="emit('submit')">
      <div class="notes-editor-top">
        <strong>{{ editingId ? '编辑笔记' : '添加一条笔记' }}</strong>
        <button
          v-if="editingId"
          type="button"
          class="icon-button"
          aria-label="取消编辑笔记"
          @click="emit('cancel-edit')"
        >
          <X :size="15" />
        </button>
      </div>
      <input
        :value="title"
        aria-label="笔记标题"
        maxlength="160"
        placeholder="标题（可选）"
        @input="emit('update:title', ($event.target as HTMLInputElement).value)"
      />
      <textarea
        ref="contentInputRef"
        :value="content"
        aria-label="笔记内容"
        rows="4"
        maxlength="10000"
        placeholder="记录你的理解、疑问或易错点"
        @input="emit('update:content', ($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <div class="notes-editor-bottom">
        <div class="notes-quick-tags" role="group" aria-label="系统预设标签" data-testid="note-quick-tags">
          <span class="notes-quick-tags-label">系统标签</span>
          <div class="notes-quick-tags-list">
            <button
              v-for="tag in resolvedQuickTags"
              :key="tag"
              type="button"
              class="note-preset-tag"
              :class="{ active: selectedTagSet.has(tag) }"
              :aria-pressed="selectedTagSet.has(tag)"
              :disabled="saving || (!selectedTagSet.has(tag) && selectedTags.length >= MAX_NOTE_TAGS)"
              :data-testid="'note-preset-tag-' + tag"
              @click="handleToggleQuickTag(tag)"
            >
              <Check v-if="selectedTagSet.has(tag)" :size="12" aria-hidden="true" />
              <Plus v-else :size="12" aria-hidden="true" />
              <span>{{ tag }}</span>
            </button>
          </div>
        </div>
        <input
          :value="liveTags"
          aria-label="笔记标签"
          placeholder="自定义标签，用逗号分隔，或点击上方系统标签"
          @input="handleTagsInput"
        />
        <button
          class="button button-dark button-small"
          type="submit"
          aria-label="保存笔记"
          :disabled="saving || !content.trim()"
        >
          <Save :size="14" />保存
        </button>
      </div>
    </form>
    <div v-if="loading" class="notes-empty">正在读取学习记录…</div>
    <div v-else-if="!notes.length" class="notes-empty">
      <FilePlus2 :size="20" />
      <span>还没有笔记，写下本节最重要的一个理解。</span>
    </div>
    <div v-else class="notes-list">
      <article v-for="note in notes" :key="note.id" class="note-item">
        <div class="note-item-heading">
          <strong>{{ note.title || '未命名笔记' }}</strong>
          <div>
            <button
              type="button"
              class="icon-button"
              :aria-label="'编辑笔记 ' + note.id"
              @click="emit('edit', note)"
            >
              <Pencil :size="14" />
            </button>
            <button
              type="button"
              class="icon-button"
              :aria-label="'删除笔记 ' + note.id"
              @click="emit('remove', note)"
            >
              <Trash2 :size="14" />
            </button>
          </div>
        </div>
        <p>{{ note.content }}</p>
        <div class="note-tags">
          <span v-for="tag in note.tags" :key="tag">#{{ tag }}</span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.notes-body {
  display: grid;
  gap: 8px;
}

.notes-editor-top,
.note-item-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 4px;
}

.note-item-heading > strong {
  min-width: 0;
  flex: 1;
}

.note-item p,
.notes-empty {
  color: var(--text-muted);
  font-size: 12px;
}

.note-item-heading .icon-button,
.notes-editor-top .icon-button {
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  cursor: pointer;
}

.notes-editor,
.notes-editor-bottom {
  display: grid;
  gap: 8px;
}

.notes-editor-top,
.note-item-heading strong {
  font-size: 13px;
}

.notes-editor :is(input, textarea) {
  width: 100%;
  min-width: 0;
  min-height: var(--control-height);
  padding: 8px 11px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font: inherit;
  font-size: 13px;
  line-height: 1.55;
  transition: border-color var(--motion), box-shadow var(--motion);
}

.notes-editor :is(input, textarea):hover {
  border-color: var(--border-focus);
}

.notes-editor :is(input, textarea):focus {
  border-color: var(--border-focus);
  box-shadow: var(--focus-ring);
}

.notes-editor textarea {
  min-height: 92px;
  resize: vertical;
}

.notes-editor .button {
  justify-self: end;
  cursor: pointer;
}

.notes-quick-tags {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.notes-quick-tags-label {
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: 600;
}

.notes-quick-tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.note-preset-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 26px;
  padding: 3px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.3;
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--motion), border-color var(--motion), background var(--motion);
}

.note-preset-tag:hover:not(:disabled),
.note-preset-tag.active {
  color: var(--accent-strong);
  border-color: var(--border-focus);
  background: var(--accent-soft);
}

.note-preset-tag.active {
  font-weight: 600;
}

.note-preset-tag:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.notes-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.notes-empty svg {
  flex: 0 0 auto;
  color: var(--accent);
}

.notes-list {
  display: grid;
  gap: 0;
}

.note-item {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--border-default);
  overflow-wrap: anywhere;
}

.note-item-heading > div,
.note-tags {
  display: flex;
  gap: 4px;
}

.note-item p {
  margin: 6px 0 8px;
  white-space: pre-wrap;
  line-height: 1.7;
}

.note-tags {
  flex-wrap: wrap;
}

.note-tags span,
.notes-message {
  padding: 3px 8px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
}

.notes-message {
  margin-bottom: 4px;
  border-radius: var(--radius-sm);
}

.notes-message.error {
  color: var(--danger);
  background: var(--danger-soft);
}
</style>
