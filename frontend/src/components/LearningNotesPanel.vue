<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Bookmark, ChevronDown, NotebookPen, X } from '@lucide/vue'
import { api, apiError, type LearningBookmark, type LearningNote } from '../api/client'
import { buildQuickNoteTags, parseNoteTags } from '../domain/noteTags'
import ConfirmDialog from './ConfirmDialog.vue'
import LearningNotesBody from './LearningNotesBody.vue'

interface SectionDraft { editingId: number | null; title: string; content: string; tags: string }
const draftCache = new Map<number, SectionDraft>()

const props = defineProps<{ sectionId: number; knowledgePointId?: number; knowledgePointName?: string; sectionTitle?: string; startCollapsed?: boolean }>()
const emit = defineEmits<{
  'bookmark-change': [bookmarked: boolean]
  'notes-count-change': [count: number]
}>()

const notes = ref<LearningNote[]>([])
const bookmarked = ref(false)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const editingId = ref<number | null>(null)
const confirmDeleteNote = ref<LearningNote | null>(null)
const collapsed = ref(Boolean(props.startCollapsed))
const composerModalOpen = ref(false)
const modalBodyRef = ref<{ focusContent: () => void } | null>(null)
const draft = reactive({ title: '', content: '', tags: '' })

let prevBodyOverflow: string | null = null
let prevFocusedEl: HTMLElement | null = null

const modalTitle = computed(() => (props.sectionTitle ? `课程笔记 · ${props.sectionTitle}` : '课程笔记'))
const quickTags = computed(() => buildQuickNoteTags(props.knowledgePointName))

async function openComposer() {
  collapsed.value = false
  composerModalOpen.value = true
  await nextTick()
  modalBodyRef.value?.focusContent()
}

function closeComposer() {
  composerModalOpen.value = false
}

async function prefillExcerpt(payload: { text: string; title?: string }) {
  const snippet = payload.text.trim()
  if (!snippet) return
  collapsed.value = false
  if (!draft.title.trim() && payload.title?.trim()) {
    draft.title = payload.title.trim().slice(0, 160)
  }
  const quoted = snippet
    .split(/\r?\n/)
    .map((line) => `> ${line}`)
    .join('\n')
  draft.content = draft.content.trim() ? `${draft.content.trimEnd()}\n\n${quoted}` : quoted
  notice.value = '已摘录选文至草稿，可补充理解后点击保存'
  composerModalOpen.value = true
  await nextTick()
  modalBodyRef.value?.focusContent()
}

function resetDraft(clearCache = true) {
  editingId.value = null
  draft.title = ''
  draft.content = ''
  draft.tags = ''
  if (clearCache) draftCache.delete(props.sectionId)
}
function failure(cause: unknown) { return apiError(cause, '学习记录暂时无法保存，请稍后重试。') }
async function load() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const [noteResponse, bookmarkResponse] = await Promise.all([
      api.get<LearningNote[]>('/learning/notes', { params: { sectionId: props.sectionId } }),
      api.get<LearningBookmark>('/learning/bookmarks', { params: { sectionId: props.sectionId } }),
    ])
    notes.value = noteResponse.data
    bookmarked.value = Boolean(bookmarkResponse.data.bookmarked)
    emit('bookmark-change', bookmarked.value)
    emit('notes-count-change', notes.value.length)
  } catch (cause) { error.value = failure(cause) }
  finally { loading.value = false }
}
function edit(note: LearningNote) {
  editingId.value = note.id
  draft.title = note.title
  draft.content = note.content
  draft.tags = note.tags.join(', ')
  notice.value = ''
}
async function save() {
  if (!draft.content.trim() || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  const payload = { sectionId: props.sectionId, title: draft.title.trim(), content: draft.content.trim(), tags: parseNoteTags(draft.tags) }
  try {
    if (editingId.value) await api.put('/learning/notes/' + editingId.value, payload)
    else await api.post('/learning/notes', payload)
    resetDraft(); await load(); notice.value = '笔记已保存'
  } catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
async function remove(note: LearningNote) {
  if (saving.value) return
  confirmDeleteNote.value = note
}
async function executeRemove() {
  const note = confirmDeleteNote.value
  if (!note || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    await api.delete('/learning/notes/' + note.id)
    if (editingId.value === note.id) resetDraft()
    confirmDeleteNote.value = null
    await load()
    notice.value = '笔记已删除'
  }
  catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
async function toggleBookmark() {
  if (saving.value || loading.value) return false
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const result = (await api.put<LearningBookmark>('/learning/bookmarks', { sectionId: props.sectionId, bookmarked: !bookmarked.value })).data
    bookmarked.value = result.bookmarked
    emit('bookmark-change', bookmarked.value)
    notice.value = result.bookmarked ? '已收藏本节' : '已取消收藏'
    return true
  } catch (cause) {
    error.value = failure(cause)
    return false
  } finally {
    saving.value = false
  }
}

function onModalKeydown(event: KeyboardEvent) {
  if (!composerModalOpen.value || confirmDeleteNote.value || event.key !== 'Escape') return
  event.preventDefault()
  event.stopPropagation()
  closeComposer()
}

function restoreModalEnvironment() {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onModalKeydown, true)
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

watch(composerModalOpen, (isOpen) => {
  if (isOpen) {
    if (typeof document !== 'undefined') {
      prevFocusedEl = document.activeElement instanceof HTMLElement ? document.activeElement : null
      if (prevBodyOverflow === null) {
        prevBodyOverflow = document.body.style.overflow
      }
      document.body.style.overflow = 'hidden'
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('keydown', onModalKeydown, true)
    }
  } else {
    restoreModalEnvironment()
  }
})

defineExpose({ prefillExcerpt, toggleBookmark, openComposer, closeComposer })
watch(() => props.sectionId, (nextId, prevId) => {
  if (prevId && (draft.title.trim() || draft.content.trim() || draft.tags.trim() || editingId.value !== null)) {
    draftCache.set(prevId, { editingId: editingId.value, title: draft.title, content: draft.content, tags: draft.tags })
  } else if (prevId) {
    draftCache.delete(prevId)
  }
  const restored = draftCache.get(nextId)
  if (restored) {
    editingId.value = restored.editingId
    draft.title = restored.title
    draft.content = restored.content
    draft.tags = restored.tags
    notice.value = '已恢复此小节未保存的笔记草稿'
  } else {
    resetDraft(false)
  }
  void load()
})
onMounted(() => void load())
onBeforeUnmount(restoreModalEnvironment)
</script>

<template>
  <section class="learning-notes-panel" aria-label="我的学习记录">
    <header class="notes-panel-heading">
      <div><h3>我的学习记录（{{ notes.length }}）</h3><span>{{ bookmarked ? '已收藏本节 · ' : '' }}笔记和收藏只对当前账户可见</span></div>
      <button class="notes-collapse icon-button" type="button" :aria-expanded="!collapsed" :aria-label="collapsed ? '展开笔记' : '收起笔记'" :title="collapsed ? '展开写笔记' : '收起笔记'" @click="collapsed = !collapsed"><ChevronDown :size="16" :class="{ rotated: collapsed }" /></button>
      <button class="notes-bookmark icon-button" :class="{ active: bookmarked }" :aria-label="bookmarked ? '取消收藏本节' : '收藏本节'" :title="bookmarked ? '取消收藏本节' : '收藏本节'" :aria-pressed="bookmarked" :disabled="saving || loading" @click="toggleBookmark"><Bookmark :size="16" :fill="bookmarked ? 'currentColor' : 'none'" /></button>
    </header>
    <div v-if="!collapsed && !composerModalOpen">
      <LearningNotesBody
        v-model:title="draft.title"
        v-model:content="draft.content"
        v-model:tags="draft.tags"
        :quick-tags="quickTags"
        :notes="notes"
        :loading="loading"
        :saving="saving"
        :editing-id="editingId"
        :error="error"
        :notice="notice"
        @submit="save"
        @cancel-edit="resetDraft()"
        @edit="edit"
        @remove="remove"
      />
    </div>
    <Teleport to="body">
      <div
        v-if="composerModalOpen"
        class="notes-dialog-backdrop"
        data-testid="note-composer-backdrop"
        @click.self="closeComposer"
      >
        <section
          class="notes-dialog-card"
          role="dialog"
          aria-modal="true"
          :aria-label="modalTitle"
          data-testid="note-composer-dialog"
        >
          <header class="notes-dialog-head">
            <div class="notes-dialog-title">
              <span class="notes-dialog-icon" aria-hidden="true">
                <NotebookPen :size="17" />
              </span>
              <div>
                <h3>{{ modalTitle }}</h3>
                <span>已有 {{ notes.length }} 条私有笔记 · 按 Esc 关闭</span>
              </div>
            </div>
            <button
              type="button"
              class="icon-button"
              aria-label="关闭记笔记弹窗"
              data-testid="note-composer-close"
              @click="closeComposer"
            >
              <X :size="16" />
            </button>
          </header>
          <div class="notes-dialog-body">
            <LearningNotesBody
              ref="modalBodyRef"
              v-model:title="draft.title"
              v-model:content="draft.content"
              v-model:tags="draft.tags"
              :quick-tags="quickTags"
              :notes="notes"
              :loading="loading"
              :saving="saving"
              :editing-id="editingId"
              :error="error"
              :notice="notice"
              @submit="save"
              @cancel-edit="resetDraft()"
              @edit="edit"
              @remove="remove"
            />
          </div>
        </section>
      </div>
    </Teleport>
    <ConfirmDialog
      :open="Boolean(confirmDeleteNote)"
      title="删除笔记"
      :message="`删除「${confirmDeleteNote?.title || '未命名笔记'}」？删除后无法恢复。`"
      confirm-label="确认删除"
      tone="danger"
      :busy="saving"
      @cancel="confirmDeleteNote = null"
      @confirm="executeRemove"
    />
  </section>
</template>

<style scoped>
.learning-notes-panel{display:grid;gap:12px;min-width:0;padding:14px;background:var(--surface-tint);border-radius:var(--radius-md)}
.notes-panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:4px}
.notes-panel-heading>div{min-width:0;flex:1}
.notes-panel-heading h3{margin:0;font-size:14px}
.notes-panel-heading span{color:var(--text-muted);font-size:12px}
.notes-panel-heading .icon-button{flex:0 0 30px;width:30px;height:30px}
.notes-collapse svg{transition:transform var(--motion)}
.notes-collapse .rotated{transform:rotate(-90deg)}
.notes-bookmark.active{color:var(--accent-strong);background:var(--accent-soft)}
.notes-dialog-backdrop{position:fixed;inset:0;z-index:1150;display:grid;place-items:center;padding:16px;background:var(--surface-overlay);backdrop-filter:blur(2px)}
.notes-dialog-card{width:min(560px,100%);max-height:min(84dvh,720px);display:flex;flex-direction:column;background:var(--surface);color:var(--text);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-lg);overflow:hidden}
.notes-dialog-head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 18px;border-bottom:1px solid var(--border-soft);background:var(--surface-soft)}
.notes-dialog-head .icon-button{cursor:pointer}
.notes-dialog-title{display:flex;align-items:center;gap:10px;min-width:0}
.notes-dialog-title>div{min-width:0}
.notes-dialog-title h3{margin:0;font-size:16px;line-height:1.35;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.notes-dialog-title span{display:block;color:var(--text-muted);font-size:12px}
.notes-dialog-icon{display:grid;place-items:center;flex:none;width:34px;height:34px;border-radius:var(--radius-sm);color:var(--accent-strong);background:var(--accent-soft)}
.notes-dialog-body{overflow-y:auto;padding:16px 18px 18px}
</style>
