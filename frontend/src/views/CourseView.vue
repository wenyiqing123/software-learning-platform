<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
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
      <section class="study-summary" aria-label="课程阅读进度">
        <div><p class="eyebrow">{{ study.code }} · {{ study.term }}</p><h2>{{ study.name }}</h2><p>{{ study.description }}</p></div>
        <div class="study-progress" v-if="study.totalSections">
          <span>阅读进度 <strong>{{ study.completedSections }} / {{ study.totalSections }} 小节</strong></span>
          <progress :value="study.completedSections" :max="study.totalSections" aria-label="阅读完成进度"></progress>
          <span>{{ progress }}% 已完成</span>
        </div>
      </section>
      <FeedbackState v-if="!sections.length" empty empty-title="本课程的阅读内容正在准备中。" empty-text="可先查看课程知识关系。" empty-action-label="浏览知识图谱" @action="router.push({ path: '/knowledge', query: { courseId } })" />
      <div v-else class="study-layout">
        <div class="study-rail">
        <aside id="course-outline" class="study-outline" aria-label="章节目录"><div class="outline-heading"><h2>章节目录</h2><span>{{ sections.length }} 节</span></div><section v-for="(chapter, index) in study.chapters" :key="chapter.id"><h3>{{ String(index + 1).padStart(2, '0') }} {{ chapter.name }}</h3><p>{{ chapter.description }}</p><button v-for="section in chapter.sections" :key="section.id" class="outline-item" :class="{ active: active?.id === section.id }" :aria-current="active?.id === section.id ? 'page' : undefined" :aria-controls="'section-' + section.id" @click="chooseSection(section)"><span class="outline-status" aria-hidden="true">{{ section.completed ? '✓' : '○' }}</span><span>{{ section.title }}<small>{{ section.completed ? '已完成阅读' : section.knowledgePointName }}</small></span></button></section></aside>
          <LearningNotesPanel v-if="active" :section-id="active.id" :knowledge-point-id="active.knowledgePointId" start-collapsed />
        </div>
        <article v-if="active" :id="'section-' + active.id" class="study-reading" :aria-labelledby="'section-title-' + active.id">
          <p class="eyebrow">小节 {{ position + 1 }} / {{ sections.length }} · {{ active.knowledgePointName }}</p><h2 :id="'section-title-' + active.id">{{ active.title }}</h2>
          <section class="study-objectives"><h3>本节学习目标</h3><ul><li v-for="objective in active.objectives" :key="objective">{{ objective }}</li></ul></section>
          <section v-for="block in active.content" :key="block.heading" class="reading-block"><h3>{{ block.heading }}</h3><p v-for="paragraph in block.paragraphs" :key="paragraph">{{ paragraph }}</p></section>
           <section v-if="active.resources.length" class="study-resources"><h3>延伸阅读</h3><div v-for="resource in active.resources" :key="resource.url"><a v-if="safeUrl(resource.url)" :href="safeUrl(resource.url)" target="_blank" rel="noopener noreferrer">{{ resource.title }} ↗</a><strong v-else>{{ resource.title }}</strong><p>{{ resource.description }}</p></div></section>
          <LearningContextActions :context="{ courseId: courseId ?? undefined, sectionId: active.id, knowledgePointId: active.knowledgePointId, entity: active.graphEntity }" />
           <footer class="study-footer"><div><button class="button button-outline" :disabled="saving" @click="markRead">{{ saving ? '正在保存…' : active.completed ? '✓ 已读 · 撤销标记' : '标记本节已读' }}</button><p>阅读记录与练习掌握度分别统计。</p><span v-if="progressError" role="alert">{{ progressError }}</span></div><div class="section-paging"><button class="text-button" :disabled="position <= 0" @click="sections[position - 1] && chooseSection(sections[position - 1]!)">上一节</button><button class="text-button" :disabled="position >= sections.length - 1" @click="sections[position + 1] && chooseSection(sections[position + 1]!)">下一节 →</button></div></footer>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.course-picker{display:grid;gap:6px;color:var(--text-muted);font-size:12px}
.study-summary{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:16px 24px;border-radius:var(--radius-md);background:var(--surface-tint)}
.study-summary h2,.outline-heading h2{margin:0;font-size:16px}
.study-summary p:not(.eyebrow){margin:4px 0 0;color:var(--text-muted);font-size:13px}
.study-progress{display:grid;gap:6px;min-width:min(100%,180px);color:var(--text-muted);font-size:12px;font-variant-numeric:tabular-nums}
.study-progress strong{color:var(--text);margin-left:8px}
.study-progress progress{width:100%;height:6px;appearance:none;border:0;border-radius:var(--radius-pill);background:var(--border-default)}
.study-progress progress::-webkit-progress-bar{background:var(--border-default);border-radius:inherit}
.study-progress progress::-webkit-progress-value{background:var(--accent);border-radius:inherit}
.study-progress progress::-moz-progress-bar{background:var(--accent);border-radius:inherit}
.study-layout{display:grid;grid-template-columns:minmax(214px,254px) minmax(0,1fr);align-items:start;gap:20px}
.study-rail{display:grid;gap:16px;min-width:0}
.study-outline,.study-reading{min-width:0;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}
.study-outline{max-height:420px;overflow:auto;padding:16px}
.outline-heading{display:flex;align-items:baseline;justify-content:space-between;gap:8px;padding-bottom:12px;border-bottom:1px solid var(--border-soft)}
.outline-heading span,.study-outline section>p{color:var(--text-muted);font-size:12px}
.study-outline h3{margin:14px 0 3px;font-size:13px}
.study-outline section>p{margin:0 0 8px}
.outline-item{display:flex;align-items:flex-start;gap:8px;width:100%;padding:8px;text-align:left;border:0;background:transparent;border-radius:var(--radius-sm);font-size:13px}
.outline-item>span:last-child{min-width:0;overflow-wrap:anywhere}
.outline-item.active{color:var(--accent-strong);background:var(--accent-soft);box-shadow:inset 2px 0 var(--accent)}
.outline-item small,.outline-status{font-size:12px;color:var(--text-muted)}
.outline-item small{display:block;margin-top:2px}
.outline-status{flex:0 0 16px;color:var(--accent);text-align:center}
.study-reading{padding:24px clamp(20px,3vw,40px)}
.study-reading>h2{margin:0 0 20px;font-size:24px;line-height:1.35}
.study-reading h3{margin:0 0 8px;font-size:16px;line-height:1.55}
.study-objectives,.reading-block,.study-resources{max-width:var(--reading-width);font-size:15px;line-height:1.7}
.study-objectives{padding:14px 16px;background:var(--surface-soft);border-left:2px solid var(--border-focus);border-radius:0 var(--radius-sm) var(--radius-sm) 0}
.study-objectives ul{margin:0;padding-left:20px}
.reading-block,.study-resources{margin-top:24px}
.reading-block p,.study-resources p{margin:0 0 12px;overflow-wrap:anywhere}
.study-resources{padding-top:16px;border-top:1px solid var(--border-soft)}
.study-resources a{color:var(--accent-strong);font-weight:600}
.study-resources p{color:var(--text-muted);font-size:13px}
.study-footer,.section-paging{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:16px}
.study-footer{margin-top:20px;padding-top:16px;border-top:1px solid var(--border-soft)}
.study-footer p{margin:8px 0 0;color:var(--text-muted);font-size:12px}
.study-footer [role=alert]{color:var(--danger);font-size:13px}
.study-notice,.study-empty{padding:20px;border-radius:var(--radius-md)}
.study-notice{color:var(--danger);background:var(--danger-soft)}
.study-empty{color:var(--text-muted);text-align:center;background:var(--surface);border:1px solid var(--border-default)}
@media(max-width:760px){.study-layout{grid-template-columns:1fr}.study-summary{align-items:flex-start;flex-direction:column}.study-outline{max-height:240px}.study-reading{padding:20px 16px}}
</style>
