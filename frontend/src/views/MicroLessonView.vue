<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, apiError, type Course, type MicroLessonDraft } from '../api/client'

interface StudySection { id: number; title: string }
interface StudyResponse { chapters?: { sections?: StudySection[] }[] }
const courses = ref<Course[]>([])
const sections = ref<StudySection[]>([])
const courseId = ref<number>()
const sectionId = ref<number>()
const lesson = ref<MicroLessonDraft>()
const loading = ref(true)
const error = ref('')

async function loadCourses() { try { courses.value = (await api.get<Course[]>('/courses')).data; courseId.value ||= courses.value[0]?.id } catch (cause) { error.value = apiError(cause, '课程列表暂时无法加载。') } }
async function loadSections() {
  if (!courseId.value) return
  try { const data = (await api.get<StudyResponse>('/courses/' + courseId.value + '/study')).data; sections.value = (data.chapters || []).flatMap(chapter => chapter.sections || []); sectionId.value ||= sections.value[0]?.id }
  catch (cause) { error.value = apiError(cause, '课程小节暂时无法加载。') }
}
async function loadLesson() {
  if (!courseId.value || !sectionId.value) { lesson.value = undefined; return }
  try { lesson.value = (await api.get<MicroLessonDraft>('/courses/' + courseId.value + '/study/sections/' + sectionId.value + '/micro-lesson')).data; error.value = '' }
  catch (cause) { lesson.value = undefined; error.value = apiError(cause, '本小节暂未发布证据微课。') }
}
watch(courseId, async () => { sectionId.value = undefined; await loadSections(); await loadLesson() })
watch(sectionId, () => { void loadLesson() })
onMounted(async () => { await loadCourses(); await loadSections(); await loadLesson(); loading.value = false })
</script>

<template>
  <div class="page-stack micro-lesson-page">
    <header class="page-heading"><div><p class="eyebrow">PUBLISHED MICRO LESSON</p><h1>证据微课</h1><p>只展示已审核发布的课程微课，内容来源和图谱证据会一并保留。</p></div></header>
    <section class="micro-selector"><label>课程<select v-model.number="courseId" aria-label="微课课程"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><label>小节<select v-model.number="sectionId" aria-label="微课小节"><option v-for="section in sections" :key="section.id" :value="section.id">{{ section.title }}</option></select></label></section>
    <section v-if="loading" class="empty-state">正在加载证据微课…</section>
    <div v-else-if="lesson" class="lesson-content"><section class="lesson-hero"><p class="eyebrow">{{ lesson.status === 'PUBLISHED' ? 'PUBLISHED' : lesson.status }}</p><h2>{{ lesson.title }}</h2><p>{{ lesson.generatedText }}</p></section><section class="lesson-grid"><article><h3>学习目标</h3><ul><li v-for="item in lesson.objectives" :key="item">{{ item }}</li></ul></article><article><h3>先修知识</h3><ul v-if="lesson.prerequisites.length"><li v-for="item in lesson.prerequisites" :key="item.id">{{ item.name }}</li></ul><p v-else class="muted">暂无先修知识记录。</p></article></section><section class="evidence-panel"><div class="panel-heading"><div><p class="eyebrow">GRAPH EVIDENCE</p><h3>关键三元组</h3></div><span>来源图谱实体：{{ lesson.evidence?.graphEntity }} · {{ lesson.evidence?.tripleCount || 0 }} 条</span></div><ul><li v-for="item in lesson.triples" :key="item.subject + item.predicate + item.object">{{ item.subject }} — {{ item.predicate }} — {{ item.object }}</li></ul></section><section v-if="lesson.commonErrors.length || lesson.selfTests.length" class="lesson-grid"><article v-if="lesson.commonErrors.length"><h3>常见错误</h3><ul><li v-for="item in lesson.commonErrors" :key="item.stem">{{ item.stem }}<small>{{ item.explanation }}</small></li></ul></article><article v-if="lesson.selfTests.length"><h3>自测题</h3><ul><li v-for="item in lesson.selfTests" :key="item.questionId">{{ item.stem }}</li></ul></article></section></div>
    <section v-else class="empty-state" role="status">{{ error || '本小节暂未发布证据微课。' }}</section>
  </div>
</template>

<style scoped>
.micro-selector,.lesson-hero,.lesson-grid article,.evidence-panel{background:#fff;border:1px solid var(--line);border-radius:10px;box-shadow:0 8px 24px rgba(31,66,48,.045)}.micro-selector{display:flex;gap:16px;flex-wrap:wrap;padding:15px 18px}.micro-selector label{display:grid;gap:6px;color:var(--muted);font-size:11px}.micro-selector select{min-height:36px;min-width:220px;border:1px solid var(--line);border-radius:6px;padding:0 10px;background:#fff;color:var(--ink)}.lesson-content{display:grid;gap:16px}.lesson-hero{padding:24px}.lesson-hero h2{margin:0 0 12px;color:var(--ink);font-size:22px}.lesson-hero p:last-child{margin:0;color:#56665c;line-height:1.8;white-space:pre-wrap}.lesson-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.lesson-grid article{padding:20px}.lesson-grid h3,.evidence-panel h3{margin:0;color:var(--ink);font-size:14px}.lesson-grid ul,.evidence-panel ul{margin:13px 0 0;padding-left:20px;color:#56665c;font-size:12px;line-height:1.8}.lesson-grid small{display:block;margin-top:3px;color:var(--muted);line-height:1.6}.evidence-panel{padding:20px}.panel-heading{display:flex;justify-content:space-between;gap:12px}.panel-heading span{color:var(--muted);font-size:10px}.muted{color:var(--muted);font-size:11px}@media(max-width:700px){.lesson-grid{grid-template-columns:1fr}.panel-heading{display:grid}}
</style>
