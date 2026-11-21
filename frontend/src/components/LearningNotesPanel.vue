<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Bookmark, ChevronDown, FilePlus2, Pencil, Save, Trash2, X } from '@lucide/vue'
import { api, apiError, type LearningBookmark, type LearningNote } from '../api/client'

const props = defineProps<{ sectionId: number; knowledgePointId?: number }>()
const notes = ref<LearningNote[]>([])
const bookmarked = ref(false)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const editingId = ref<number | null>(null)
const collapsed = ref(false)
const draft = reactive({ title: '', content: '', tags: '' })

function resetDraft() { editingId.value = null; draft.title = ''; draft.content = ''; draft.tags = '' }
function failure(cause: unknown) { return apiError(cause, '学习记录暂时无法保存，请稍后重试。') }
function tags() { return [...new Set(draft.tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean))].slice(0, 10) }
async function load() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const [noteResponse, bookmarkResponse] = await Promise.all([
      api.get<LearningNote[]>('/learning/notes', { params: { sectionId: props.sectionId } }),
      api.get<LearningBookmark>('/learning/bookmarks', { params: { sectionId: props.sectionId } }),
    ])
    notes.value = noteResponse.data
    bookmarked.value = Boolean(bookmarkResponse.data.bookmarked)
  } catch (cause) { error.value = failure(cause) }
  finally { loading.value = false }
}
function edit(note: LearningNote) { editingId.value = note.id; draft.title = note.title; draft.content = note.content; draft.tags = note.tags.join('、'); notice.value = '' }
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
  if (saving.value || !window.confirm('删除这条笔记？删除后无法恢复。')) return
  saving.value = true; error.value = ''; notice.value = ''
  try { await api.delete('/learning/notes/' + note.id); if (editingId.value === note.id) resetDraft(); await load(); notice.value = '笔记已删除' }
  catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
async function toggleBookmark() {
  if (saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try { const result = (await api.put<LearningBookmark>('/learning/bookmarks', { sectionId: props.sectionId, bookmarked: !bookmarked.value })).data; bookmarked.value = result.bookmarked; notice.value = result.bookmarked ? '已收藏本节' : '已取消收藏' }
  catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
watch(() => props.sectionId, () => { resetDraft(); void load() })
onMounted(() => void load())
</script>

<template>
  <section class="learning-notes-panel" aria-label="我的学习记录">
    <header class="notes-panel-heading"><div><p class="eyebrow">PERSONAL STUDY LOG</p><h3>我的学习记录</h3><span>笔记和收藏只对当前账户可见</span></div><button class="notes-collapse" type="button" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><ChevronDown :size="15" :class="{ rotated: collapsed }" />{{ collapsed ? '展开' : '收起' }}</button><button class="notes-bookmark" :class="{ active: bookmarked }" :aria-label="bookmarked ? '取消收藏本节' : '收藏本节'" :disabled="saving || loading" @click="toggleBookmark"><Bookmark :size="16" :fill="bookmarked ? 'currentColor' : 'none'" />{{ bookmarked ? '已收藏' : '收藏本节' }}</button></header>
    <div v-if="!collapsed">
    <div v-if="error" class="notes-message error" role="alert">{{ error }}</div><div v-if="notice" class="notes-message success" role="status">{{ notice }}</div>
    <form class="notes-editor" @submit.prevent="save"><div class="notes-editor-top"><strong>{{ editingId ? '编辑笔记' : '添加一条笔记' }}</strong><button v-if="editingId" type="button" class="icon-button" aria-label="取消编辑笔记" @click="resetDraft"><X :size="15" /></button></div><input v-model="draft.title" aria-label="笔记标题" maxlength="160" placeholder="标题（可选）" /><textarea v-model="draft.content" aria-label="笔记内容" rows="3" maxlength="10000" placeholder="记录你的理解、疑问或易错点"></textarea><div class="notes-editor-bottom"><input v-model="draft.tags" aria-label="笔记标签" placeholder="标签，用逗号分隔" /><button class="button button-dark button-small" type="submit" aria-label="保存笔记" :disabled="saving || !draft.content.trim()"><Save :size="14" />保存</button></div></form>
    <div v-if="loading" class="notes-empty">正在读取学习记录…</div><div v-else-if="!notes.length" class="notes-empty"><FilePlus2 :size="20" /><span>还没有笔记，写下本节最重要的一个理解。</span></div><div v-else class="notes-list"><article v-for="note in notes" :key="note.id" class="note-item"><div class="note-item-heading"><strong>{{ note.title || '未命名笔记' }}</strong><div><button class="icon-button" :aria-label="'编辑笔记 ' + note.id" @click="edit(note)"><Pencil :size="14" /></button><button class="icon-button" :aria-label="'删除笔记 ' + note.id" @click="remove(note)"><Trash2 :size="14" /></button></div></div><p>{{ note.content }}</p><div class="note-tags"><span v-for="tag in note.tags" :key="tag">#{{ tag }}</span></div></article></div>
    </div>
  </section>
</template>

<style scoped>
.learning-notes-panel { display:grid; gap:14px; margin-top:28px; padding:18px; background:#f8fbf8; border:1px solid #dfeae1; border-radius:10px; }.notes-panel-heading,.notes-editor-top,.notes-editor-bottom,.note-item-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }.notes-panel-heading h3 { margin:0 0 4px; color:var(--ink); font-size:15px; }.notes-panel-heading span { color:var(--muted); font-size:10px; }.notes-panel-heading .eyebrow { margin:0 0 4px; color:var(--green); }.notes-collapse{display:inline-flex;align-items:center;gap:4px;margin-left:auto;padding:0 7px;min-height:28px;color:var(--text-muted);background:transparent;border:1px solid transparent;border-radius:var(--radius-sm);font-size:10px;cursor:pointer}.notes-collapse:hover{color:var(--accent);background:var(--accent-soft)}.notes-collapse .rotated{transform:rotate(-90deg)}.notes-bookmark { display:inline-flex; align-items:center; gap:6px; min-height:34px; padding:0 10px; color:#6d7b72; background:white; border:1px solid var(--line); border-radius:7px; font-size:11px; cursor:pointer; }.notes-panel-heading>div{min-width:0}.notes-panel-heading{align-items:flex-start}.notes-bookmark.active { color:var(--green); background:var(--green-pale); border-color:#b8d7c3; }.notes-editor { display:grid; gap:8px; padding:13px; background:#fff; border:1px solid var(--line); border-radius:8px; }.notes-editor-top { color:#4a5d50; font-size:11px; }.notes-editor input,.notes-editor textarea { width:100%; padding:9px 10px; color:var(--ink); background:#fff; border:1px solid #dbe5dd; border-radius:6px; font:inherit; font-size:11px; outline:none; }.notes-editor input:focus,.notes-editor textarea:focus { border-color:#95c5a4; box-shadow:0 0 0 2px #e2f1e6; }.notes-editor textarea { resize:vertical; line-height:1.65; }.notes-editor-bottom { align-items:center; }.notes-editor-bottom input { flex:1; }.notes-empty { display:flex; align-items:center; justify-content:center; gap:8px; min-height:65px; color:#829087; font-size:11px; text-align:center; }.notes-list { display:grid; gap:9px; }.note-item { padding:12px 13px; background:#fff; border:1px solid #e5eee6; border-radius:8px; }.note-item-heading strong { color:#405548; font-size:11px; }.note-item-heading .icon-button { width:27px; height:27px; }.note-item p { margin:8px 0; color:#607168; font-size:11px; line-height:1.7; white-space:pre-wrap; }.note-tags { display:flex; flex-wrap:wrap; gap:5px; }.note-tags span { padding:3px 6px; color:#61816b; background:#eef7ef; border-radius:4px; font-size:9px; }.notes-message { padding:8px 10px; border-radius:6px; font-size:10px; }.notes-message.error { color:#9d4d3a; background:#fff0ec; }.notes-message.success { color:#3e7750; background:#eaf6ed; }.notes-editor .button { justify-self:end; }
@media(max-width:600px){.learning-notes-panel{padding:13px}.notes-panel-heading{align-items:flex-start;flex-direction:column}.notes-bookmark{align-self:flex-start}.notes-editor-bottom{align-items:stretch;flex-direction:column}.notes-editor-bottom .button{width:100%}}
</style>
