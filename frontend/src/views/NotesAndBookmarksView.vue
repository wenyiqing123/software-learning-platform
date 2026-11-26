<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowUpRight, Bookmark, BookmarkX, BookOpen, CheckSquare, Download, FileText, Network, Pencil, Printer, RotateCcw, Save, Search, Trash2, X } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { apiError, type LearningBookmarkItem, type LearningNote } from '../api/client'
import * as notesApi from '../api/notes'
import { buildNotesMarkdown, triggerMarkdownDownload } from '../domain/studyExport'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import SectionHeading from '../components/SectionHeading.vue'

const notes = ref<LearningNote[]>([])
const bookmarks = ref<LearningBookmarkItem[]>([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const keyword = ref('')
const activeTag = ref('')
const activeTab = ref<'notes' | 'bookmarks'>('notes')
const editingNoteId = ref<number | null>(null)
const confirmDeleteNote = ref<LearningNote | null>(null)
const editDraft = reactive({ title: '', content: '', tags: '' })

const allTags = computed(() => {
  const counts = new Map<string, number>()
  for (const item of notes.value) {
    for (const tag of item.tags || []) {
      const normalized = tag.trim()
      if (!normalized) continue
      counts.set(normalized, (counts.get(normalized) || 0) + 1)
    }
  }
  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([tag, count]) => ({ tag, count }))
})

const filteredNotes = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const selectedTag = activeTag.value
  return notes.value.filter((item) => {
    if (selectedTag && !(item.tags || []).includes(selectedTag)) return false
    if (!q) return true
    return (
      (item.title || '').toLowerCase().includes(q)
      || item.content.toLowerCase().includes(q)
      || (item.tags || []).some((tag) => tag.toLowerCase().includes(q))
    )
  })
})

const filteredBookmarks = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return bookmarks.value
  return bookmarks.value.filter((item) =>
    item.title.toLowerCase().includes(q)
    || (item.courseName || '').toLowerCase().includes(q)
    || (item.knowledgePointName || '').toLowerCase().includes(q),
  )
})

function formatTimestamp(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

function parseTags(raw: string) {
  return [...new Set(raw.split(/[,，、]/).map((tag) => tag.trim()).filter(Boolean))].slice(0, 10)
}

function startEdit(note: LearningNote) {
  editingNoteId.value = note.id
  editDraft.title = note.title || ''
  editDraft.content = note.content
  editDraft.tags = (note.tags || []).join(', ')
  notice.value = ''
  error.value = ''
}

function cancelEdit() {
  editingNoteId.value = null
  editDraft.title = ''
  editDraft.content = ''
  editDraft.tags = ''
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [noteRows, bookmarkRows] = await Promise.all([
      notesApi.notes(),
      notesApi.bookmarksList(),
    ])
    notes.value = Array.isArray(noteRows) ? noteRows : []
    bookmarks.value = Array.isArray(bookmarkRows) ? bookmarkRows : []
    if (activeTag.value && !allTags.value.some((item) => item.tag === activeTag.value)) {
      activeTag.value = ''
    }
  } catch (cause) {
    error.value = apiError(cause, '暂时无法读取个人笔记与收藏，请稍后重试。')
  } finally {
    loading.value = false
  }
}

async function saveEdit(note: LearningNote) {
  if (busy.value || !editDraft.content.trim()) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await notesApi.updateNote(note.id, {
      sectionId: note.sectionId,
      knowledgePointId: note.knowledgePointId,
      title: editDraft.title.trim(),
      content: editDraft.content.trim(),
      tags: parseTags(editDraft.tags),
    })
    cancelEdit()
    await loadAll()
    notice.value = '笔记修改已保存'
  } catch (cause) {
    error.value = apiError(cause, '保存笔记失败，请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function confirmDelete() {
  const target = confirmDeleteNote.value
  if (!target || busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await notesApi.deleteNote(target.id)
    if (editingNoteId.value === target.id) cancelEdit()
    confirmDeleteNote.value = null
    await loadAll()
    notice.value = '笔记已删除'
  } catch (cause) {
    error.value = apiError(cause, '删除笔记失败，请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function unbookmark(item: LearningBookmarkItem) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await notesApi.setBookmark(
      item.targetType === 'SECTION'
        ? { sectionId: item.targetId, bookmarked: false }
        : { knowledgePointId: item.targetId, bookmarked: false },
    )
    await loadAll()
    notice.value = `已取消收藏「${item.title}」`
  } catch (cause) {
    error.value = apiError(cause, '取消收藏失败，请稍后重试。')
  } finally {
    busy.value = false
  }
}

onMounted(() => void loadAll())

function exportHandout() {
  const scopeParts: string[] = []
  if (activeTag.value) scopeParts.push(`标签 #${activeTag.value}`)
  if (keyword.value.trim()) scopeParts.push(`关键词 “${keyword.value.trim()}”`)
  const scopeLabel = scopeParts.length ? scopeParts.join(' · ') : '全部笔记与收藏'
  const markdown = buildNotesMarkdown(filteredNotes.value, filteredBookmarks.value, scopeLabel)
  const stamp = new Date().toISOString().slice(0, 10)
  triggerMarkdownDownload(`study-notes-${stamp}.md`, markdown)
  notice.value = `已导出 Markdown 讲义（${filteredNotes.value.length} 条笔记，${filteredBookmarks.value.length} 项收藏）`
}

function printHandout() {
  if (typeof window !== 'undefined' && typeof window.print === 'function') {
    window.print()
  }
}
</script>

<template>
  <div class="page-stack notes-hub-page">
    <SectionHeading
      eyebrow="个人知识沉淀"
      title="笔记与收藏"
      description="集中检索各课程小节记下的理解与标签，随时回到收藏小节或重点知识点继续巩固。"
    >
      <div class="notes-hub-heading-actions no-print">
        <button
          type="button"
          class="button button-outline"
          :disabled="loading || (!filteredNotes.length && !filteredBookmarks.length)"
          data-testid="notes-export-md"
          @click="exportHandout"
        >
          <Download :size="15" />导出讲义
        </button>
        <button
          type="button"
          class="button button-outline"
          :disabled="loading || (!filteredNotes.length && !filteredBookmarks.length)"
          data-testid="notes-print"
          @click="printHandout"
        >
          <Printer :size="15" />打印讲义
        </button>
        <button class="button button-outline" :disabled="loading" @click="loadAll">
          <RotateCcw :size="15" />刷新
        </button>
      </div>
    </SectionHeading>

    <div v-if="error" class="inline-notice error-notice" role="alert">
      {{ error }}
      <button class="text-button" :disabled="loading" @click="loadAll">重试</button>
    </div>
    <div v-if="notice" class="inline-notice success" role="status">{{ notice }}</div>

    <div class="notes-hub-toolbar">
      <div class="notes-hub-tabs" role="tablist" aria-label="笔记与收藏分类">
        <button
          type="button"
          role="tab"
          class="notes-hub-tab"
          :class="{ active: activeTab === 'notes' }"
          :aria-selected="activeTab === 'notes'"
          data-testid="notes-hub-tab-notes"
          @click="activeTab = 'notes'"
        >
          <FileText :size="15" />
          <span>学习笔记（{{ notes.length }}）</span>
        </button>
        <button
          type="button"
          role="tab"
          class="notes-hub-tab"
          :class="{ active: activeTab === 'bookmarks' }"
          :aria-selected="activeTab === 'bookmarks'"
          data-testid="notes-hub-tab-bookmarks"
          @click="activeTab = 'bookmarks'"
        >
          <Bookmark :size="15" />
          <span>收藏内容（{{ bookmarks.length }}）</span>
        </button>
      </div>

      <label class="notes-hub-search">
        <Search :size="15" />
        <input
          v-model="keyword"
          aria-label="搜索笔记或收藏"
          placeholder="搜索标题、正文、课程或 #标签…"
        />
        <button
          v-if="keyword"
          type="button"
          class="icon-button"
          aria-label="清空搜索"
          @click="keyword = ''"
        >
          <X :size="14" />
        </button>
      </label>
    </div>

    <div v-if="loading" class="loading-row"><span class="spinner"></span>正在整理个人笔记与收藏</div>

    <template v-else-if="activeTab === 'notes'">
      <div v-if="allTags.length" class="notes-tag-bar" aria-label="按标签筛选">
        <button
          type="button"
          class="notes-tag-pill"
          :class="{ active: activeTag === '' }"
          @click="activeTag = ''"
        >
          全部（{{ notes.length }}）
        </button>
        <button
          v-for="item in allTags"
          :key="item.tag"
          type="button"
          class="notes-tag-pill"
          :class="{ active: activeTag === item.tag }"
          @click="activeTag = activeTag === item.tag ? '' : item.tag"
        >
          #{{ item.tag }}（{{ item.count }}）
        </button>
      </div>

      <div v-if="!filteredNotes.length" class="feedback-state feedback-empty">
        <FileText :size="24" class="feedback-icon" />
        <strong>{{ notes.length ? '没有匹配的笔记' : '还没有保存任何学习笔记' }}</strong>
        <span>
          {{ notes.length
            ? '试试更换搜索关键词或清除当前标签筛选。'
            : '在「课程阅读」页右侧打开「我的学习记录」，记下关键概念、疑问或易错点。' }}
        </span>
        <RouterLink v-if="!notes.length" class="button button-dark" to="/courses">去课程阅读记笔记</RouterLink>
      </div>

      <div v-else class="notes-hub-grid">
        <article v-for="note in filteredNotes" :key="note.id" class="notes-hub-card" data-testid="notes-hub-card">
          <template v-if="editingNoteId === note.id">
            <form class="notes-hub-edit-form" @submit.prevent="saveEdit(note)">
              <input v-model="editDraft.title" aria-label="编辑笔记标题" maxlength="160" placeholder="标题（可选）" />
              <textarea v-model="editDraft.content" aria-label="编辑笔记正文" rows="4" maxlength="10000" />
              <input v-model="editDraft.tags" aria-label="编辑笔记标签" placeholder="标签，用逗号分隔" />
              <div class="notes-hub-edit-actions">
                <button type="button" class="button button-outline button-small" :disabled="busy" @click="cancelEdit">
                  取消
                </button>
                <button
                  type="submit"
                  class="button button-dark button-small"
                  :disabled="busy || !editDraft.content.trim()"
                >
                  <Save :size="14" />保存修改
                </button>
              </div>
            </form>
          </template>
          <template v-else>
            <header class="notes-hub-card-head">
              <div>
                <h2>{{ note.title || '未命名笔记' }}</h2>
                <small v-if="note.updatedAt || note.createdAt">
                  更新于 {{ formatTimestamp(note.updatedAt || note.createdAt) }}
                </small>
              </div>
              <div class="notes-hub-card-tools">
                <button
                  type="button"
                  class="icon-button"
                  :aria-label="'编辑笔记 ' + (note.title || note.id)"
                  @click="startEdit(note)"
                >
                  <Pencil :size="15" />
                </button>
                <button
                  type="button"
                  class="icon-button"
                  :aria-label="'删除笔记 ' + (note.title || note.id)"
                  @click="confirmDeleteNote = note"
                >
                  <Trash2 :size="15" />
                </button>
              </div>
            </header>
            <p class="notes-hub-body">{{ note.content }}</p>
            <div v-if="note.tags?.length" class="notes-hub-tags">
              <button
                v-for="tag in note.tags"
                :key="tag"
                type="button"
                class="notes-tag-chip"
                @click="activeTag = tag"
              >
                #{{ tag }}
              </button>
            </div>
            <footer class="notes-hub-links">
              <RouterLink
                v-if="note.sectionId"
                class="text-button"
                :to="{ path: '/courses', query: { sectionId: String(note.sectionId) } }"
              >
                <BookOpen :size="14" />回到对应小节<ArrowUpRight :size="13" />
              </RouterLink>
              <RouterLink
                v-if="note.knowledgePointId"
                class="text-button"
                :to="{ path: '/practice', query: { knowledgePointId: String(note.knowledgePointId) } }"
              >
                <CheckSquare :size="14" />专项练习<ArrowUpRight :size="13" />
              </RouterLink>
            </footer>
          </template>
        </article>
      </div>
    </template>

    <template v-else>
      <div v-if="!filteredBookmarks.length" class="feedback-state feedback-empty">
        <Bookmark :size="24" class="feedback-icon" />
        <strong>{{ bookmarks.length ? '没有匹配的收藏内容' : '还没有收藏小节或知识点' }}</strong>
        <span>
          {{ bookmarks.length
            ? '试试更换搜索关键词。'
            : '在课程阅读页或图谱页点击收藏按钮，重点内容会集中显示在这里。' }}
        </span>
        <RouterLink v-if="!bookmarks.length" class="button button-dark" to="/courses">浏览课程内容</RouterLink>
      </div>

      <div v-else class="bookmarks-hub-grid">
        <article
          v-for="item in filteredBookmarks"
          :key="item.targetType + '-' + item.targetId"
          class="notes-hub-card bookmark-hub-card"
          data-testid="bookmark-hub-card"
        >
          <header class="notes-hub-card-head">
            <div>
              <span class="bookmark-kind-badge">
                {{ item.targetType === 'SECTION' ? '课程小节' : '知识点' }}
              </span>
              <h2>{{ item.title }}</h2>
              <small v-if="item.courseName">
                {{ item.courseName }}{{ item.knowledgePointName && item.targetType === 'SECTION' ? ' · ' + item.knowledgePointName : '' }}
              </small>
            </div>
            <button
              type="button"
              class="icon-button"
              :aria-label="'取消收藏 ' + item.title"
              :disabled="busy"
              @click="unbookmark(item)"
            >
              <BookmarkX :size="16" />
            </button>
          </header>
          <footer class="notes-hub-links">
            <RouterLink
              v-if="item.targetType === 'SECTION'"
              class="text-button"
              :to="{ path: '/courses', query: { ...(item.courseId ? { courseId: String(item.courseId) } : {}), sectionId: String(item.targetId) } }"
            >
              <BookOpen :size="14" />打开课程阅读<ArrowUpRight :size="13" />
            </RouterLink>
            <RouterLink
              v-if="item.knowledgePointId"
              class="text-button"
              :to="{ path: '/practice', query: { knowledgePointId: String(item.knowledgePointId) } }"
            >
              <CheckSquare :size="14" />对应练习<ArrowUpRight :size="13" />
            </RouterLink>
            <RouterLink
              v-if="item.graphEntity"
              class="text-button"
              :to="{ path: '/knowledge', query: { entity: item.graphEntity } }"
            >
              <Network :size="14" />查看图谱<ArrowUpRight :size="13" />
            </RouterLink>
          </footer>
        </article>
      </div>
    </template>

    <ConfirmDialog
      :open="Boolean(confirmDeleteNote)"
      title="删除笔记"
      :message="`删除「${confirmDeleteNote?.title || '未命名笔记'}」？删除后无法恢复。`"
      confirm-label="确认删除"
      tone="danger"
      :busy="busy"
      @cancel="confirmDeleteNote = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.notes-hub-page {
  min-width: 0;
}

.notes-hub-heading-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.notes-hub-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notes-hub-tabs {
  display: inline-flex;
  gap: 6px;
  padding: 4px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.notes-hub-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 5px 12px;
  color: var(--text-muted);
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.notes-hub-tab.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.notes-hub-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 260px;
  max-width: 380px;
  min-height: 40px;
  padding: 0 10px 0 12px;
  color: var(--text-muted);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.notes-hub-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 13px;
  outline: none;
}

.notes-tag-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notes-tag-pill {
  min-height: 30px;
  padding: 4px 11px;
  color: var(--text-muted);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  font-size: 12px;
  cursor: pointer;
}

.notes-tag-pill.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--border-focus);
  font-weight: 600;
}

.notes-hub-grid,
.bookmarks-hub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  gap: 14px;
}

.notes-hub-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.bookmark-hub-card {
  border-color: var(--border-default);
}

.notes-hub-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.notes-hub-card-head h2 {
  margin: 0;
  color: var(--text);
  font-size: 15.5px;
  line-height: 1.4;
}

.notes-hub-card-head small {
  display: block;
  margin-top: 3px;
  color: var(--text-subtle);
  font-size: 12px;
}

.notes-hub-card-tools {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.notes-hub-body {
  margin: 0;
  color: var(--text-muted);
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.notes-hub-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.notes-tag-chip,
.bookmark-kind-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 0;
  border-radius: var(--radius-pill);
  font-size: 12px;
}

.notes-tag-chip {
  cursor: pointer;
}

.bookmark-kind-badge {
  margin-bottom: 6px;
  font-weight: 600;
}

.notes-hub-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--border-soft);
}

.notes-hub-edit-form {
  display: grid;
  gap: 8px;
}

.notes-hub-edit-form :is(input, textarea) {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  font-size: 13px;
}

.notes-hub-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
