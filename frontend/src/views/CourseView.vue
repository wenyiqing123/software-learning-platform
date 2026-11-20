<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import LearningNotesPanel from '../components/LearningNotesPanel.vue'
import LearningContextActions from '../components/LearningContextActions.vue'
import { rememberLearningContext } from '../domain/learningContext'

interface Course { id: number; name: string; term: string; description: string }
interface Resource { title: string; url: string; description: string }
interface StudySection { id: number; knowledgePointId: number; knowledgePointName: string; graphEntity: string; title: string; objectives: string[]; content: { heading: string; paragraphs: string[] }[]; resources: Resource[]; completed: boolean }
interface Chapter { id: number; name: string; description: string; sections: StudySection[] }
interface Study { courseId: number; name: string; code: string; term: string; description: string; totalSections: number; completedSections: number; chapters: Chapter[] }
const route = useRoute()
const router = useRouter()
const courses = ref<Course[]>([])
const courseId = ref<number | null>(null)
const study = ref<Study | null>(null)
const activeId = ref<number | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const progressError = ref('')
const sections = computed(() => study.value?.chapters.flatMap(ch => ch.sections) ?? [])
const active = computed(() => sections.value.find(s => s.id === activeId.value) ?? sections.value[0])
const position = computed(() => sections.value.findIndex(s => s.id === active.value?.id))
const progress = computed(() => study.value?.totalSections ? Math.round(study.value.completedSections / study.value.totalSections * 100) : 0)
let requestVersion = 0
function failure(cause: unknown) { const e = cause as { response?: { status?: number; data?: { message?: string; detail?: string } } }; return e.response?.data?.message || e.response?.data?.detail || (e.response?.status === 404 ? '课程不存在或已停用，请选择其他课程。' : '课程内容暂时无法加载，请重试。') }
function safeUrl(value: string) { try { const url = new URL(value); return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : undefined } catch { return undefined } }
async function loadCourse(id: number) {
  const version = ++requestVersion
  loading.value = true; error.value = ''; progressError.value = ''; study.value = null
  try {
    const { data } = await api.get<Study>('/courses/' + id + '/study')
    if (version !== requestVersion) return
    study.value = data
    const availableSections = data.chapters.flatMap(ch => ch.sections)
    const requested = Number(route.query.sectionId)
    const requestedPoint = Number(route.query.knowledgePointId)
    activeId.value = availableSections.find(s => s.id === requested)?.id
      ?? availableSections.find(s => s.knowledgePointId === requestedPoint)?.id
      ?? availableSections.find(s => !s.completed)?.id
      ?? data.chapters[0]?.sections[0]?.id
      ?? null
  } catch (cause) { if (version === requestVersion) error.value = failure(cause) }
  finally { if (version === requestVersion) loading.value = false }
}
async function loadCourses() {
  loading.value = true; error.value = ''
  try {
    courses.value = (await api.get<Course[]>('/courses')).data
    const requested = Number(route.query.courseId)
    courseId.value = courses.value.find(c => c.id === requested)?.id ?? courses.value[0]?.id ?? null
    if (courseId.value) await loadCourse(courseId.value)
    else loading.value = false
  } catch (cause) { error.value = failure(cause); loading.value = false }
}
function chooseSection(section: StudySection) {
  activeId.value = section.id; progressError.value = ''
  rememberLearningContext({ courseId: courseId.value ?? undefined, sectionId: section.id, knowledgePointId: section.knowledgePointId, entity: section.graphEntity })
  void router.replace({ path: '/courses', query: { courseId: courseId.value, sectionId: section.id } })
}
function chooseCourse() { if (courseId.value) { void router.replace({ path: '/courses', query: { courseId: courseId.value } }); void loadCourse(courseId.value) } }
async function markRead() {
  const section = active.value
  const current = study.value
  if (!section || !current || saving.value) return
  saving.value = true; progressError.value = ''
  const completed = !section.completed
  try {
    await api.put('/courses/' + current.courseId + '/study/sections/' + section.id + '/progress', { completed })
    section.completed = completed
    current.completedSections = current.chapters.flatMap(ch => ch.sections).filter(s => s.completed).length
  } catch (cause) { progressError.value = failure(cause) }
  finally { saving.value = false }
}
watch(() => route.query.courseId, value => { const next = Number(value); if (next > 0 && next !== courseId.value) { courseId.value = next; void loadCourse(next) } })
watch(() => route.query.sectionId, value => { const next = Number(value); if (sections.value.some(s => s.id === next)) activeId.value = next })
onMounted(loadCourses)
</script>

<template>
  <div class="page-stack course-study">
    <SectionHeading eyebrow="理解概念 · 建立联系 · 检验所学" title="课程学习" description="按章节阅读课程内容，再前往知识图谱与配套练习。">
      <label class="course-picker">当前课程 <select v-model="courseId" aria-label="当前课程" :disabled="loading || saving" @change="chooseCourse"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label>
    </SectionHeading>
    <div v-if="error" class="study-notice" role="alert">{{ error }} <button class="text-button" @click="loadCourses">重新加载</button></div>
    <div v-if="loading" class="study-empty" role="status">正在加载课程与阅读进度…</div>
    <div v-else-if="!courses.length && !error" class="study-empty">暂无可学习的课程。课程启用后会显示在这里。</div>
    <template v-else-if="study">
      <section class="study-summary"><div><p class="eyebrow">{{ study.code }} / {{ study.term }}</p><h2>{{ study.name }}</h2><p>{{ study.description }}</p><div class="study-summary-actions"><RouterLink class="button button-outline button-small" :to="{ path: '/knowledge', query: { courseId: study.courseId, entity: active?.graphEntity, knowledgePointId: active?.knowledgePointId } }">浏览课程图谱</RouterLink><RouterLink class="button button-dark button-small" :to="{ path: '/practice', query: { courseId: study.courseId, knowledgePointId: active?.knowledgePointId } }">开始课程练习</RouterLink></div></div><div class="study-progress"><strong>{{ study.completedSections }} / {{ study.totalSections }}</strong><span>小节已读 · {{ progress }}%</span><progress :value="study.completedSections" :max="Math.max(1, study.totalSections)" aria-label="阅读完成进度"></progress><div class="progress-caption"><span>学习进度</span><strong>{{ progress }}%</strong></div></div></section>
      <div v-if="!sections.length" class="study-empty">本课程的阅读内容正在准备中。<RouterLink :to="{ path: '/knowledge', query: { courseId } }">浏览知识图谱</RouterLink></div>
      <div v-else class="study-layout">
        <aside id="course-outline" class="study-outline" aria-label="章节目录"><div class="outline-heading"><div><p class="eyebrow">章节目录</p><strong>{{ study.completedSections }} / {{ study.totalSections }} 小节</strong></div><span>{{ progress }}%</span></div><section v-for="(chapter, index) in study.chapters" :key="chapter.id"><h3>{{ String(index + 1).padStart(2, '0') }} {{ chapter.name }}</h3><p>{{ chapter.description }}</p><button v-for="section in chapter.sections" :key="section.id" class="outline-item" :class="{ active: active?.id === section.id }" :aria-current="active?.id === section.id ? 'page' : undefined" :aria-controls="'section-' + section.id" @click="chooseSection(section)"><span class="outline-status" aria-hidden="true">{{ section.completed ? '✓' : '○' }}</span><span>{{ section.title }}<small>{{ section.completed ? '已完成阅读' : section.knowledgePointName }}</small></span></button></section></aside>
        <article v-if="active" :id="'section-' + active.id" class="study-reading" :aria-labelledby="'section-title-' + active.id">
          <p class="eyebrow">小节 {{ position + 1 }} / {{ sections.length }} · {{ active.knowledgePointName }}</p><h2 :id="'section-title-' + active.id">{{ active.title }}</h2>
          <section class="study-objectives"><h3>本节学习目标</h3><ul><li v-for="objective in active.objectives" :key="objective">{{ objective }}</li></ul></section>
          <section v-for="block in active.content" :key="block.heading" class="reading-block"><h3>{{ block.heading }}</h3><p v-for="paragraph in block.paragraphs" :key="paragraph">{{ paragraph }}</p></section>
           <section v-if="active.resources.length" class="study-resources"><h3>延伸阅读</h3><div v-for="resource in active.resources" :key="resource.url"><a v-if="safeUrl(resource.url)" :href="safeUrl(resource.url)" target="_blank" rel="noopener noreferrer">{{ resource.title }} ↗</a><strong v-else>{{ resource.title }}</strong><p>{{ resource.description }}</p></div></section>
           <LearningNotesPanel :section-id="active.id" :knowledge-point-id="active.knowledgePointId" />
           <LearningContextActions :context="{ courseId: courseId ?? undefined, sectionId: active.id, knowledgePointId: active.knowledgePointId, entity: active.graphEntity }" />
           <div class="study-actions"><RouterLink class="button button-outline" :to="{ path: '/knowledge', query: { entity: active.graphEntity, courseId, knowledgePointId: active.knowledgePointId, sectionId: active.id } }">在图谱中建立联系 ↗</RouterLink><RouterLink class="button button-dark" :to="{ path: '/practice', query: { knowledgePointId: active.knowledgePointId, courseId, sectionId: active.id } }">练习本节知识 →</RouterLink></div>
           <footer class="study-footer"><div><button class="button button-outline" :disabled="saving" @click="markRead">{{ saving ? '正在保存…' : active.completed ? '✓ 已读 · 撤销标记' : '标记本节已读' }}</button><p>阅读记录与练习掌握度分别统计。</p><span v-if="progressError" role="alert">{{ progressError }}</span></div><div class="section-paging"><button class="text-button" :disabled="position <= 0" @click="sections[position - 1] && chooseSection(sections[position - 1]!)">上一节</button><button class="text-button" :disabled="position >= sections.length - 1" @click="sections[position + 1] && chooseSection(sections[position + 1]!)">下一节 →</button></div></footer>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.course-picker { display:grid; gap:7px; color:var(--muted); font-size:12px; }
select { max-width:100%; padding:10px 12px; color:var(--ink); background:white; border:1px solid var(--line); border-radius:7px; font:inherit; }
.study-summary { display:flex; justify-content:space-between; gap:24px; padding:24px; background:white; border:1px solid var(--line); border-radius:10px; }
.study-summary h2 { margin:0 0 10px; font-size:22px; }.study-summary p { margin-bottom:0; color:var(--muted); font-size:13px; line-height:1.8; }
.study-progress { display:grid; align-content:center; gap:7px; min-width:150px; }.study-progress strong { font-size:22px; }.study-progress span { color:var(--muted); font-size:12px; }progress { width:100%; height:8px; accent-color:var(--green); }
.study-layout { display:grid; grid-template-columns:250px minmax(0,1fr); align-items:start; gap:24px; }.study-outline { padding:20px 16px; background:white; border:1px solid var(--line); border-radius:10px; }.study-outline section + section { margin-top:25px; }.study-outline h3 { font-size:13px; line-height:1.6; }.study-outline section > p { color:var(--muted); font-size:11px; line-height:1.7; }
.outline-item { display:flex; align-items:start; gap:9px; width:100%; padding:11px 8px; text-align:left; line-height:1.6; font-size:12px; background:transparent; border:0; border-radius:7px; cursor:pointer; }.outline-item.active { color:var(--green); background:var(--green-pale); }.outline-item small { display:block; margin-top:3px; color:var(--muted); font-size:10px; }
.study-reading { padding:32px clamp(20px,4vw,50px); background:white; border:1px solid var(--line); border-radius:10px; }.study-reading > h2 { margin:0 0 24px; font-size:clamp(20px,2vw,26px); line-height:1.6; }.study-objectives { padding:17px 22px; background:var(--green-pale); border-radius:8px; }.study-reading h3 { margin:0 0 12px; font-size:16px; line-height:1.8; }.study-objectives ul { margin:0; padding-left:20px; font-size:13px; line-height:2; }.reading-block { margin-top:30px; }.reading-block p { color:#46554c; font-size:14px; line-height:2.05; overflow-wrap:anywhere; }.study-resources { margin-top:30px; padding:20px; border:1px solid var(--line); border-radius:8px; }.study-resources a { color:var(--green); font-size:13px; }.study-resources p { color:var(--muted); font-size:12px; line-height:1.8; }.study-actions { display:flex; flex-wrap:wrap; gap:12px; margin-top:25px; }.study-actions a { text-decoration:none; }.study-footer { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-top:28px; padding-top:24px; border-top:1px solid var(--line); }.study-footer p { color:var(--muted); font-size:11px; }.study-footer [role=alert] { color:#9b4130; font-size:12px; }.section-paging { display:flex; gap:18px; }.study-notice { padding:16px; color:#9b4130; background:#fff4ec; border-radius:8px; }.study-empty { display:grid; gap:15px; padding:40px; color:var(--muted); text-align:center; background:white; border:1px solid var(--line); border-radius:10px; }
@media(max-width:1050px) { .study-layout { grid-template-columns:210px minmax(0,1fr); gap:14px; }.study-reading { padding:24px; } }@media(max-width:760px) { .study-layout { grid-template-columns:1fr; }.study-summary,.study-footer { flex-direction:column; align-items:stretch; }.study-outline { max-height:320px; overflow:auto; }.study-reading { padding:22px 18px; }.study-summary { padding:20px; } }
</style>
