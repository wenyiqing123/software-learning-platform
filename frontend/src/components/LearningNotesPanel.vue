<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Bookmark, ChevronDown, FilePlus2, Pencil, Save, Trash2, X } from '@lucide/vue'
import { api, apiError, type LearningBookmark, type LearningNote } from '../api/client'
import ConfirmDialog from './ConfirmDialog.vue'

interface SectionDraft { editingId: number | null; title: string; content: string; tags: string }
const draftCache = new Map<number, SectionDraft>()

const props = defineProps<{ sectionId: number; knowledgePointId?: number; startCollapsed?: boolean }>()
const emit = defineEmits<{ 'bookmark-change': [bookmarked: boolean] }>()
const notes = ref<LearningNote[]>([])
const bookmarked = ref(false)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const editingId = ref<number | null>(null)
const confirmDeleteNote = ref<LearningNote | null>(null)
const collapsed = ref(Boolean(props.startCollapsed))
const draft = reactive({ title: '', content: '', tags: '' })

function prefillExcerpt(payload: { text: string; title?: string }) {
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
}

function resetDraft(clearCache = true) {
  editingId.value = null
  draft.title = ''
  draft.content = ''
  draft.tags = ''
  if (clearCache) draftCache.delete(props.sectionId)
}
function failure(cause: unknown) { return apiError(cause, '学习记录暂时无法保存，请稍后重试。') }
function tags() { return [...new Set(draft.tags.split(/[,，、]/).map(tag => tag.trim()).filter(Boolean))].slice(0, 10) }
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
  } catch (cause) { error.value = failure(cause) }
  finally { loading.value = false }
}
function edit(note: LearningNote) { editingId.value = note.id; draft.title = note.title; draft.content = note.content; draft.tags = note.tags.join(', '); notice.value = '' }
async function save() {
  if (!draft.content.trim() || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  const payload = { sectionId: props.sectionId, title: draft.title.trim(), content: draft.content.trim(), tags: tags() }
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

defineExpose({ prefillExcerpt, toggleBookmark })
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
</script>

<template>
  <section class="learning-notes-panel" aria-label="我的学习记录">
    <header class="notes-panel-heading">
      <div><h3>我的学习记录（{{ notes.length }}）</h3><span>{{ bookmarked ? '已收藏本节 · ' : '' }}笔记和收藏只对当前账户可见</span></div>
      <button class="notes-collapse icon-button" type="button" :aria-expanded="!collapsed" :aria-label="collapsed ? '展开笔记' : '收起笔记'" :title="collapsed ? '展开笔记' : '收起笔记'" @click="collapsed = !collapsed"><ChevronDown :size="16" :class="{ rotated: collapsed }" /></button>
      <button class="notes-bookmark icon-button" :class="{ active: bookmarked }" :aria-label="bookmarked ? '取消收藏本节' : '收藏本节'" :title="bookmarked ? '取消收藏本节' : '收藏本节'" :aria-pressed="bookmarked" :disabled="saving || loading" @click="toggleBookmark"><Bookmark :size="16" :fill="bookmarked ? 'currentColor' : 'none'" /></button>
    </header>
    <div v-if="!collapsed">
    <div v-if="error" class="notes-message error" role="alert">{{ error }}</div><div v-if="notice" class="notes-message success" role="status">{{ notice }}</div>
    <form class="notes-editor" @submit.prevent="save"><div class="notes-editor-top"><strong>{{ editingId ? '编辑笔记' : '添加一条笔记' }}</strong><button v-if="editingId" type="button" class="icon-button" aria-label="取消编辑笔记" @click="resetDraft()"><X :size="15" /></button></div><input v-model="draft.title" aria-label="笔记标题" maxlength="160" placeholder="标题（可选）" /><textarea v-model="draft.content" aria-label="笔记内容" rows="3" maxlength="10000" placeholder="记录你的理解、疑问或易错点"></textarea><div class="notes-editor-bottom"><input v-model="draft.tags" aria-label="笔记标签" placeholder="标签，用逗号分隔" /><button class="button button-dark button-small" type="submit" aria-label="保存笔记" :disabled="saving || !draft.content.trim()"><Save :size="14" />保存</button></div></form>
    <div v-if="loading" class="notes-empty">正在读取学习记录…</div><div v-else-if="!notes.length" class="notes-empty"><FilePlus2 :size="20" /><span>还没有笔记，写下本节最重要的一个理解。</span></div><div v-else class="notes-list"><article v-for="note in notes" :key="note.id" class="note-item"><div class="note-item-heading"><strong>{{ note.title || '未命名笔记' }}</strong><div><button class="icon-button" :aria-label="'编辑笔记 ' + note.id" @click="edit(note)"><Pencil :size="14" /></button><button class="icon-button" :aria-label="'删除笔记 ' + note.id" @click="remove(note)"><Trash2 :size="14" /></button></div></div><p>{{ note.content }}</p><div class="note-tags"><span v-for="tag in note.tags" :key="tag">#{{ tag }}</span></div></article></div>
    </div>
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
.notes-panel-heading,.note-item-heading,.notes-editor-top{display:flex;align-items:flex-start;justify-content:space-between;gap:4px}
.notes-panel-heading>div,.note-item-heading>strong{min-width:0;flex:1}
.notes-panel-heading h3{margin:0;font-size:14px}
.notes-panel-heading span,.notes-empty,.note-item p{color:var(--text-muted);font-size:12px}
.notes-panel-heading .icon-button,.note-item-heading .icon-button{flex:0 0 30px;width:30px;height:30px}
.notes-collapse svg{transition:transform var(--motion)}
.notes-collapse .rotated{transform:rotate(-90deg)}
.notes-bookmark.active{color:var(--accent-strong);background:var(--accent-soft)}
.notes-editor,.notes-editor-bottom{display:grid;gap:8px}
.notes-editor-top,.note-item-heading strong{font-size:13px}
.notes-editor :is(input,textarea){width:100%;font-size:13px}
.notes-editor .button{justify-self:end}
.notes-empty{display:flex;align-items:center;gap:8px;margin-top:12px}
.notes-empty svg{flex:0 0 auto;color:var(--accent)}
.notes-list{display:grid;gap:0}
.note-item{padding-top:12px;margin-top:12px;border-top:1px solid var(--border-default);overflow-wrap:anywhere}
.note-item-heading>div,.note-tags{display:flex;gap:4px}
.note-item p{margin:6px 0 8px;white-space:pre-wrap;line-height:1.7}
.note-tags{flex-wrap:wrap}
.note-tags span,.notes-message{padding:3px 8px;color:var(--accent-strong);background:var(--accent-soft);border-radius:var(--radius-pill);font-size:12px}
.notes-message{margin-bottom:8px;border-radius:var(--radius-sm)}
.notes-message.error{color:var(--danger);background:var(--danger-soft)}
</style>
