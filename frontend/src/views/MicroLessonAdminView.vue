<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, apiError, type Course, type MicroLessonDraft } from '../api/client'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import FeedbackState from '../components/FeedbackState.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import SectionHeading from '../components/SectionHeading.vue'

const props = withDefaults(defineProps<{
  embedded?: boolean
}>(), {
  embedded: false
})

interface StudySection { id: number; chapterName?: string; title: string; knowledgePointName?: string }
const courses = ref<Course[]>([])
const sections = ref<StudySection[]>([])
const drafts = ref<MicroLessonDraft[]>([])
const courseId = ref<number>()
const sectionId = ref<number>()
const loading = ref(true)
const working = ref(false)
const confirmPublishLessonId = ref<number | null>(null)
const editingDraftId = ref<number | null>(null)
const editTitle = ref('')
const editGeneratedText = ref('')
const error = ref('')
const notice = ref('')

async function loadCourses() {
  try {
    const list = (await api.get<Course[]>('/admin/courses')).data
    courses.value = Array.isArray(list) ? list : []
    if (!courseId.value) courseId.value = courses.value[0]?.id
  } catch (cause) { error.value = apiError(cause, '课程列表暂时无法加载。') }
}
async function loadSections() {
  if (!courseId.value) { sections.value = []; sectionId.value = undefined; return }
  try {
    const list = (await api.get<StudySection[]>('/admin/micro-lessons/courses/' + courseId.value + '/sections')).data
    sections.value = Array.isArray(list) ? list : []
    if (!sectionId.value || !sections.value.some(section => section.id === sectionId.value)) sectionId.value = sections.value[0]?.id
  } catch (cause) { error.value = apiError(cause, '课程小节暂时无法加载。') }
}
async function loadDrafts() {
  if (!courseId.value) { drafts.value = []; return }
  try {
    const list = (await api.get<MicroLessonDraft[]>('/admin/micro-lessons', { params: { courseId: courseId.value } })).data
    drafts.value = Array.isArray(list) ? list : []
  } catch (cause) { error.value = apiError(cause, '微课草稿暂时无法加载。') }
}
async function generate() {
  if (!courseId.value || !sectionId.value) { error.value = '请选择课程小节。'; return }
  working.value = true; error.value = ''; notice.value = ''
  try { await api.post('/admin/micro-lessons/generate', { courseId: courseId.value, sectionId: sectionId.value }); notice.value = '证据微课草稿已生成，发布前不会影响正式课程正文。'; await loadDrafts() }
  catch (cause) { error.value = apiError(cause, '证据微课生成失败。') }
  finally { working.value = false }
}
function startEditDraft(draft: MicroLessonDraft) {
  editingDraftId.value = draft.id
  editTitle.value = draft.title
  editGeneratedText.value = draft.generatedText
  error.value = ''
  notice.value = ''
}
function cancelEditDraft() {
  editingDraftId.value = null
  editTitle.value = ''
  editGeneratedText.value = ''
}
async function saveEditDraft() {
  if (!editingDraftId.value || working.value) return
  if (!editTitle.value.trim() || !editGeneratedText.value.trim()) {
    error.value = '微课标题和正文内容不能为空。'
    return
  }
  working.value = true; error.value = ''; notice.value = ''
  try {
    await api.put('/admin/micro-lessons/' + editingDraftId.value, {
      title: editTitle.value.trim(),
      generatedText: editGeneratedText.value.trim(),
    })
    editingDraftId.value = null
    notice.value = '微课草稿内容已更新。'
    await loadDrafts()
  } catch (cause) { error.value = apiError(cause, '微课草稿保存失败。') }
  finally { working.value = false }
}
function publish(id: number) {
  confirmPublishLessonId.value = id
}
async function executePublish() {
  const id = confirmPublishLessonId.value
  if (!id || working.value) return
  working.value = true; error.value = ''; notice.value = ''
  try {
    await api.put('/admin/micro-lessons/' + id + '/publish')
    confirmPublishLessonId.value = null
    notice.value = '微课已发布，学生现在可以查看。'
    await loadDrafts()
  }
  catch (cause) { error.value = apiError(cause, '微课发布失败。') }
  finally { working.value = false }
}
watch(courseId, async () => { sectionId.value = undefined; await loadSections(); await loadDrafts() })
onMounted(async () => { await loadCourses(); await loadSections(); await loadDrafts(); loading.value = false })
</script>

<template>
  <div class="page-stack micro-lesson-admin-page">
    <SectionHeading
      :embedded="props.embedded"
      eyebrow="EVIDENCE-BOUND MICRO LESSON"
      title="证据微课草稿"
      description="基于真实课程小节和知识图谱证据生成，审核发布后才对学生可见。"
    />
    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>
    <div v-if="notice" class="inline-notice success" role="status">{{ notice }}</div>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在加载课程内容" loading-text="正在读取课程小节和微课草稿，请稍候。" />
    <template v-else>
      <section class="micro-toolbar">
        <label>课程
          <select v-model.number="courseId" aria-label="微课课程">
            <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option>
          </select>
        </label>
        <label>小节
          <select v-model.number="sectionId" aria-label="微课小节">
            <option v-for="section in sections" :key="section.id" :value="section.id">{{ section.title }}</option>
          </select>
        </label>
        <button class="primary-button" aria-label="生成证据微课草稿" :disabled="working || !sectionId" @click="generate">生成草稿</button>
      </section>
      <section class="micro-source" v-if="sections.find(section => section.id === sectionId)">
        <strong>{{ sections.find(section => section.id === sectionId)?.title }}</strong>
        <span>{{ sections.find(section => section.id === sectionId)?.knowledgePointName || '课程知识点' }}</span>
        <small>生成结果独立保存，不覆盖课程正文。</small>
      </section>
      <section class="draft-list">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">DRAFT HISTORY</p>
            <h2>草稿记录</h2>
          </div>
        </div>
        <div v-if="!drafts.length" class="empty-state">当前课程还没有微课草稿。</div>
        <article v-for="draft in drafts" :key="draft.id" class="draft-card">
          <div class="draft-heading">
            <div>
              <strong>{{ draft.title }}</strong>
              <small>图谱证据 {{ draft.evidence?.tripleCount || 0 }} 条 · {{ draft.evidence?.graphEntity || '未返回实体' }}</small>
            </div>
            <span :class="['status-tag', draft.status.toLowerCase()]">{{ draft.status === 'DRAFT' ? '草稿' : draft.status === 'PUBLISHED' ? '已发布' : '已归档' }}</span>
          </div>
          <form v-if="editingDraftId === draft.id" class="draft-editor" @submit.prevent="saveEditDraft">
            <label>微课标题<input v-model="editTitle" :aria-label="'编辑微课标题 ' + draft.id" maxlength="160" :disabled="working" /></label>
            <label>微课正文（Markdown）<textarea v-model="editGeneratedText" :aria-label="'编辑微课正文 ' + draft.id" rows="6" maxlength="12000" :disabled="working"></textarea></label>
            <div class="draft-editor-actions">
              <button type="button" class="text-button" :disabled="working" @click="cancelEditDraft">取消</button>
              <button type="submit" class="primary-button" :disabled="working || !editTitle.trim() || !editGeneratedText.trim()">保存修改</button>
            </div>
          </form>
          <div v-else class="draft-markdown">
            <MarkdownContent :content="draft.generatedText" />
          </div>
          <div class="draft-actions">
            <button v-if="draft.status === 'DRAFT' && editingDraftId !== draft.id" type="button" class="text-button" :aria-label="'编辑微课 ' + draft.id" :disabled="working" @click="startEditDraft(draft)">编辑草稿</button>
            <button v-if="draft.status === 'DRAFT'" class="text-button" :aria-label="'发布微课 ' + draft.id" :disabled="working" @click="publish(draft.id)">发布微课</button>
            <span v-else class="published-note">学生可见</span>
          </div>
        </article>
      </section>
    </template>
    <ConfirmDialog
      :open="confirmPublishLessonId !== null"
      title="发布证据微课"
      message="发布后学生将可以看到这份证据微课，确认继续吗？"
      confirm-label="确认发布"
      tone="default"
      :busy="working"
      @cancel="confirmPublishLessonId = null"
      @confirm="executePublish"
    />
  </div>
</template>

<style scoped>
.micro-toolbar,
.micro-source,
.draft-list {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.micro-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 20px;
}

.micro-toolbar label {
  display: grid;
  gap: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.micro-toolbar select {
  min-height: 36px;
  min-width: 220px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 0 10px;
  background: var(--surface);
  color: var(--text);
}

.primary-button {
  min-height: 36px;
  border: 0;
  border-radius: var(--radius-sm);
  padding: 0 16px;
  background: var(--accent);
  color: var(--surface);
  cursor: pointer;
  font-weight: 500;
}

.primary-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.micro-source {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  margin-top: 16px;
}

.micro-source strong {
  color: var(--ink);
}

.micro-source span {
  color: var(--accent);
  font-size: 12px;
}

.micro-source small {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 12px;
}

.draft-list {
  display: grid;
  gap: 0;
  padding: 20px 24px;
  margin-top: 24px;
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-heading h2 {
  margin: 0;
  font-size: 15px;
  color: var(--ink);
}

.draft-card {
  padding: 16px 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.draft-card + .draft-card {
  border-top: 1px solid var(--line);
}

.draft-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.draft-heading strong,
.draft-heading small {
  display: block;
}

.draft-heading small {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.draft-card p {
  margin: 12px 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.draft-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
}

.draft-markdown {
  margin: 12px 0;
}

.draft-editor {
  display: grid;
  gap: 10px;
  margin: 12px 0;
  padding: 12px;
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.draft-editor label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  color: var(--text-muted);
}

.draft-editor-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}

.text-button {
  border: 0;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.published-note {
  color: var(--accent);
  font-size: 12px;
}

.status-tag {
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  align-self: flex-start;
}

.status-tag.draft {
  background: var(--warning-soft);
  color: var(--warning);
}

.status-tag.published {
  background: var(--accent-soft);
  color: var(--accent);
}

.status-tag.archived {
  background: var(--surface-soft);
  color: var(--text-muted);
}

.success {
  border-color: var(--accent-soft);
  color: var(--accent);
}

@media (max-width: 700px) {
  .micro-source {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .micro-source small {
    margin-left: 0;
  }
}
</style>
