<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight } from '@lucide/vue'
import { api, apiError, type Course, type MicroLessonDraft } from '../api/client'
import FeedbackState from '../components/FeedbackState.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import SectionHeading from '../components/SectionHeading.vue'

interface StudySection { id: number; title: string }
interface StudyResponse { chapters?: { sections?: StudySection[] }[] }
const route = useRoute()
const router = useRouter()
const courses = ref<Course[]>([])
const sections = ref<StudySection[]>([])
const queryCourseId = Number(route?.query?.courseId)
const querySectionId = Number(route?.query?.sectionId)
const courseId = ref<number | undefined>(Number.isInteger(queryCourseId) && queryCourseId > 0 ? queryCourseId : undefined)
const sectionId = ref<number | undefined>(Number.isInteger(querySectionId) && querySectionId > 0 ? querySectionId : undefined)
const lesson = ref<MicroLessonDraft>()
const loading = ref(true)
const error = ref('')

async function loadCourses() { try { courses.value = (await api.get<Course[]>('/courses')).data; if (!courseId.value || !courses.value.some(c => c.id === courseId.value)) courseId.value = courses.value[0]?.id } catch (cause) { error.value = apiError(cause, '课程列表暂时无法加载。') } }
async function loadSections() {
  if (!courseId.value) return
  try { const data = (await api.get<StudyResponse>('/courses/' + courseId.value + '/study')).data; sections.value = (data.chapters || []).flatMap(chapter => chapter.sections || []); if (!sectionId.value || !sections.value.some(s => s.id === sectionId.value)) sectionId.value = sections.value[0]?.id }
  catch (cause) { error.value = apiError(cause, '课程小节暂时无法加载。') }
}
async function loadLesson() {
  if (!courseId.value || !sectionId.value) { lesson.value = undefined; return }
  try { lesson.value = (await api.get<MicroLessonDraft>('/courses/' + courseId.value + '/study/sections/' + sectionId.value + '/micro-lesson')).data; error.value = '' }
  catch (cause) { lesson.value = undefined; error.value = apiError(cause, '本小节暂未发布证据微课。') }
}
function openSelfTest(questionId: number) {
  const kpId = lesson.value?.evidence?.knowledgePointId
  void router?.push({
    path: '/practice',
    query: {
      ...(kpId ? { knowledgePointId: String(kpId) } : {}),
      questionId: String(questionId),
    },
  })
}
watch(courseId, async (next, prev) => { if (prev !== undefined && next !== prev) sectionId.value = undefined; await loadSections(); await loadLesson() })
watch(sectionId, () => { void loadLesson() })
onMounted(async () => { await loadCourses(); await loadSections(); await loadLesson(); loading.value = false })
</script>

<template>
  <div class="page-stack micro-lesson-page">
    <SectionHeading eyebrow="课程学习" title="证据微课" description="只展示已审核发布的课程微课，内容来源和图谱证据会一并保留。" />
    <section class="micro-selector"><label>课程<select v-model.number="courseId" aria-label="微课课程"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><label>小节<select v-model.number="sectionId" aria-label="微课小节"><option v-for="section in sections" :key="section.id" :value="section.id">{{ section.title }}</option></select></label></section>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在加载证据微课" loading-text="正在读取已发布的课程微课和图谱证据，请稍候。" />
    <article v-else-if="lesson" class="content-section lesson-content">
      <header class="lesson-heading">
        <p class="eyebrow">{{ lesson.status === 'PUBLISHED' ? '已发布' : lesson.status }}</p>
        <h2>{{ lesson.title }}</h2>
      </header>
      <section class="lesson-grid">
        <div><h3>学习目标</h3><ul><li v-for="item in lesson.objectives" :key="item">{{ item }}</li></ul></div>
        <div><h3>先修知识</h3><ul v-if="lesson.prerequisites.length"><li v-for="item in lesson.prerequisites" :key="item.id">{{ item.name }}</li></ul><p v-else>暂无先修知识记录。</p></div>
      </section>
      <MarkdownContent :content="lesson.generatedText" />
      <section class="evidence-panel">
        <div class="lesson-evidence-heading"><h3>关键三元组</h3><span>来源图谱实体：{{ lesson.evidence?.graphEntity }} · {{ lesson.evidence?.tripleCount || 0 }} 条</span></div>
        <ul><li v-for="item in lesson.triples" :key="item.subject + item.predicate + item.object">{{ item.subject }} — {{ item.predicate }} — {{ item.object }}</li></ul>
      </section>
      <section v-if="lesson.commonErrors.length || lesson.selfTests.length" class="lesson-grid lesson-callout-grid">
        <div v-if="lesson.commonErrors.length"><h3>常见错误</h3><ul><li v-for="item in lesson.commonErrors" :key="item.stem">{{ item.stem }}<small>{{ item.explanation }}</small></li></ul></div>
        <div v-if="lesson.selfTests.length"><h3>自测题</h3><ul class="self-test-list"><li v-for="item in lesson.selfTests" :key="item.questionId"><button type="button" class="text-button self-test-btn" @click="openSelfTest(item.questionId)"><span>{{ item.stem }}</span><ArrowRight :size="13" aria-hidden="true" /></button></li></ul></div>
      </section>
    </article>
    <FeedbackState v-else :error="error" :empty="!error" empty-title="本小节暂未发布证据微课" empty-text="请选择其他课程小节，或等待教师审核发布。" @retry="loadLesson" />
  </div>
</template>

<style scoped>
.micro-selector{display:flex;align-items:center;flex-wrap:wrap;gap:12px;padding:12px 16px;background:var(--surface-tint);border-radius:var(--radius-md)}
.micro-selector label{display:flex;align-items:center;flex-wrap:wrap;gap:8px;color:var(--text-muted);font-size:13px}
.lesson-content{display:grid;align-content:start;gap:20px;padding:24px clamp(18px,3vw,38px)}
.lesson-heading h2{margin:0;font-size:22px;line-height:1.35}
.lesson-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 20px;padding-block:14px;border-block:1px solid var(--border-soft)}
.lesson-grid h3,.evidence-panel h3{margin:0;font-size:16px}
.lesson-grid ul,.evidence-panel ul{margin:6px 0 0;padding-left:20px;overflow-wrap:anywhere}
.lesson-grid p,.lesson-grid small,.lesson-evidence-heading span{color:var(--text-muted);font-size:13px}
.lesson-grid p{margin:6px 0 0}
.lesson-grid small{display:block;margin-top:3px}
.evidence-panel,.lesson-callout-grid{padding:14px 16px;background:var(--surface-soft);border:1px solid var(--border-soft);border-radius:var(--radius-sm)}
.lesson-evidence-heading{display:flex;align-items:baseline;flex-wrap:wrap;justify-content:space-between;gap:6px 12px}
.self-test-list{list-style:none;padding-left:0}
.self-test-btn{display:inline-flex;align-items:center;gap:6px;padding:4px 0;text-align:left;color:var(--accent-strong);font-size:13px}
.self-test-btn:hover{text-decoration:underline}
@media(max-width:650px){.lesson-grid{grid-template-columns:1fr}}
</style>
