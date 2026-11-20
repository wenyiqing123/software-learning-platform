<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, apiError, type Course, type MicroLessonDraft } from '../api/client'

interface StudySection { id: number; title: string; knowledgePointName?: string; objectives?: string[] }
interface StudyResponse { chapters?: { sections?: StudySection[] }[] }
const courses = ref<Course[]>([])
const sections = ref<StudySection[]>([])
const drafts = ref<MicroLessonDraft[]>([])
const courseId = ref<number>()
const sectionId = ref<number>()
const loading = ref(true)
const working = ref(false)
const error = ref('')
const notice = ref('')

async function loadCourses() {
  try { courses.value = (await api.get<Course[]>('/admin/courses')).data; if (!courseId.value) courseId.value = courses.value[0]?.id }
  catch (cause) { error.value = apiError(cause, '课程列表暂时无法加载。') }
}
async function loadSections() {
  if (!courseId.value) { sections.value = []; sectionId.value = undefined; return }
  try {
    const data = (await api.get<StudyResponse>('/courses/' + courseId.value + '/study')).data
    sections.value = (data.chapters || []).flatMap(chapter => chapter.sections || [])
    if (!sectionId.value || !sections.value.some(section => section.id === sectionId.value)) sectionId.value = sections.value[0]?.id
  } catch (cause) { error.value = apiError(cause, '课程小节暂时无法加载。') }
}
async function loadDrafts() {
  if (!courseId.value) { drafts.value = []; return }
  try { drafts.value = (await api.get<MicroLessonDraft[]>('/admin/micro-lessons', { params: { courseId: courseId.value } })).data }
  catch (cause) { error.value = apiError(cause, '微课草稿暂时无法加载。') }
}
async function generate() {
  if (!courseId.value || !sectionId.value) { error.value = '请选择课程小节。'; return }
  working.value = true; error.value = ''; notice.value = ''
  try { await api.post('/admin/micro-lessons/generate', { courseId: courseId.value, sectionId: sectionId.value }); notice.value = '证据微课草稿已生成，发布前不会影响正式课程正文。'; await loadDrafts() }
  catch (cause) { error.value = apiError(cause, '证据微课生成失败。') }
  finally { working.value = false }
}
async function publish(id: number) {
  working.value = true; error.value = ''; notice.value = ''
  try { await api.put('/admin/micro-lessons/' + id + '/publish'); notice.value = '微课已发布，学生现在可以查看。'; await loadDrafts() }
  catch (cause) { error.value = apiError(cause, '微课发布失败。') }
  finally { working.value = false }
}
watch(courseId, async () => { sectionId.value = undefined; await loadSections(); await loadDrafts() })
onMounted(async () => { await loadCourses(); await loadSections(); await loadDrafts(); loading.value = false })
</script>

<template>
  <div class="page-stack micro-lesson-admin-page">
    <header class="page-heading"><div><p class="eyebrow">EVIDENCE-BOUND MICRO LESSON</p><h1>证据微课草稿</h1><p>基于真实课程小节和知识图谱证据生成，审核发布后才对学生可见。</p></div></header>
    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div><div v-if="notice" class="inline-notice success" role="status">{{ notice }}</div>
    <section v-if="loading" class="empty-state">正在加载课程内容…</section>
    <template v-else>
      <section class="micro-toolbar"><label>课程<select v-model.number="courseId" aria-label="微课课程"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><label>小节<select v-model.number="sectionId" aria-label="微课小节"><option v-for="section in sections" :key="section.id" :value="section.id">{{ section.title }}</option></select></label><button class="primary-button" aria-label="生成证据微课草稿" :disabled="working || !sectionId" @click="generate">生成草稿</button></section>
      <section class="micro-source" v-if="sections.find(section => section.id === sectionId)"><strong>{{ sections.find(section => section.id === sectionId)?.title }}</strong><span>{{ sections.find(section => section.id === sectionId)?.knowledgePointName || '课程知识点' }}</span><small>生成结果独立保存，不覆盖课程正文。</small></section>
      <section class="draft-list"><div class="panel-heading"><div><p class="eyebrow">DRAFT HISTORY</p><h2>草稿记录</h2></div></div><div v-if="!drafts.length" class="empty-state">当前课程还没有微课草稿。</div><article v-for="draft in drafts" :key="draft.id" class="draft-card"><div class="draft-heading"><div><strong>{{ draft.title }}</strong><small>图谱证据 {{ draft.evidence?.tripleCount || 0 }} 条 · {{ draft.evidence?.graphEntity || '未返回实体' }}</small></div><span :class="['status-tag', draft.status.toLowerCase()]">{{ draft.status === 'DRAFT' ? '草稿' : draft.status === 'PUBLISHED' ? '已发布' : '已归档' }}</span></div><p>{{ draft.generatedText }}</p><div class="draft-actions"><button v-if="draft.status === 'DRAFT'" class="text-button" :aria-label="'发布微课 ' + draft.id" :disabled="working" @click="publish(draft.id)">发布微课</button><span v-else class="published-note">学生可见</span></div></article></section>
    </template>
  </div>
</template>

<style scoped>
.micro-toolbar,.micro-source,.draft-list,.draft-card{background:#fff;border:1px solid var(--line);border-radius:10px;box-shadow:0 8px 24px rgba(31,66,48,.045)}.micro-toolbar{display:flex;align-items:flex-end;gap:16px;flex-wrap:wrap;padding:16px 18px}.micro-toolbar label{display:grid;gap:6px;color:var(--muted);font-size:11px}.micro-toolbar select{min-height:36px;min-width:220px;border:1px solid var(--line);border-radius:6px;padding:0 10px;background:#fff;color:var(--ink)}.primary-button{min-height:36px;border:0;border-radius:6px;padding:0 15px;background:var(--accent);color:#fff;cursor:pointer}.primary-button:disabled{opacity:.5;cursor:not-allowed}.micro-source{display:flex;align-items:center;gap:14px;padding:14px 18px}.micro-source strong{color:var(--ink)}.micro-source span{color:#4d7761;font-size:11px}.micro-source small{margin-left:auto;color:var(--muted);font-size:10px}.draft-list{display:grid;gap:14px;padding:20px}.panel-heading{display:flex;justify-content:space-between}.panel-heading h2{margin:0;font-size:15px;color:var(--ink)}.draft-card{padding:16px;box-shadow:none}.draft-heading{display:flex;justify-content:space-between;gap:12px}.draft-heading strong,.draft-heading small{display:block}.draft-heading small{margin-top:5px;color:var(--muted);font-size:10px}.draft-card p{margin:14px 0;color:#56665c;font-size:12px;line-height:1.7;white-space:pre-wrap}.draft-actions{display:flex;justify-content:flex-end}.text-button{border:0;background:transparent;color:var(--accent);cursor:pointer;font:inherit;font-size:11px}.published-note{color:#2b7548;font-size:11px}.status-tag{padding:4px 7px;border-radius:5px;font-size:10px}.status-tag.draft{background:#fff3df;color:#9b681d}.status-tag.published{background:#e4f4e8;color:#2b7548}.status-tag.archived{background:#eef0ef;color:#6d7770}.success{border-color:#b8ddc3;color:#2b7548}@media(max-width:700px){.micro-source{align-items:flex-start;flex-direction:column}.micro-source small{margin-left:0}}
</style>
