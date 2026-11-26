<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookmarkPlus, GraduationCap, MessageSquarePlus } from '@lucide/vue'
import { api } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import LearningNotesPanel from '../components/LearningNotesPanel.vue'
import LearningContextActions from '../components/LearningContextActions.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
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
const autoReadNotice = ref('')
const readingProgressPct = ref(0)
const activeAnchorId = ref('anchor-objectives')
const readingArticleRef = ref<HTMLElement | null>(null)
const notesPanelRef = ref<{ prefillExcerpt: (payload: { text: string; title?: string }) => void } | null>(null)
const selectionToolbar = ref({ visible: false, text: '', top: 0, left: 0 })
const sections = computed(() => study.value?.chapters.flatMap(ch => ch.sections) ?? [])
const active = computed(() => sections.value.find(s => s.id === activeId.value) ?? sections.value[0])
const sectionAnchors = computed(() => {
  const sec = active.value
  if (!sec) return [] as Array<{ id: string; label: string }>
  const list: Array<{ id: string; label: string }> = [{ id: 'anchor-objectives', label: '学习目标' }]
  sec.content.forEach((block, idx) => {
    if (block.heading) list.push({ id: `anchor-block-${idx}`, label: block.heading })
  })
  if (sec.resources.length) list.push({ id: 'anchor-resources', label: '延伸阅读' })
  return list
})
const position = computed(() => sections.value.findIndex(s => s.id === active.value?.id))
const progress = computed(() => study.value?.totalSections ? Math.round(study.value.completedSections / study.value.totalSections * 100) : 0)
let requestVersion = 0
let requireDownwardScroll = false
let lastScrollTop = 0
let scrollRafId: number | null = null
const suppressedAutoReadIds = new Set<number>()
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
  activeId.value = section.id; progressError.value = ''; autoReadNotice.value = ''
  rememberLearningContext({ courseId: courseId.value ?? undefined, sectionId: section.id, knowledgePointId: section.knowledgePointId, entity: section.graphEntity })
  void router.replace({ path: '/courses', query: { courseId: courseId.value, sectionId: section.id }, hash: '#section-' + section.id })
}
function chooseCourse() {
  if (courseId.value) {
    suppressedAutoReadIds.clear(); autoReadNotice.value = ''; requireDownwardScroll = false
    void router.replace({ path: '/courses', query: { courseId: courseId.value } })
    void loadCourse(courseId.value)
  }
}
async function persistCompleted(section: StudySection, completed: boolean, source: 'scroll' | 'manual') {
  const current = study.value
  if (!current || saving.value) return
  saving.value = true; progressError.value = ''
  if (source === 'manual') autoReadNotice.value = ''
  try {
    await api.put('/courses/' + current.courseId + '/study/sections/' + section.id + '/progress', { completed })
    section.completed = completed
    current.completedSections = current.chapters.flatMap(ch => ch.sections).filter(s => s.completed).length
    if (completed) {
      suppressedAutoReadIds.delete(section.id)
      autoReadNotice.value = source === 'scroll' ? '已滚动至页面底部，本节已自动标记为已读。' : ''
    } else {
      suppressedAutoReadIds.add(section.id)
      autoReadNotice.value = ''
    }
  } catch (cause) {
    if (source === 'scroll') suppressedAutoReadIds.add(section.id)
    progressError.value = failure(cause)
  } finally { saving.value = false }
}
async function markRead() {
  const section = active.value
  if (!section) return
  await persistCompleted(section, !section.completed, 'manual')
}
function getWindowScrollMetrics() {
  const doc = document.documentElement
  if (!doc) return null
  const viewportHeight = window.innerHeight || doc.clientHeight || 0
  const scrollHeight = doc.scrollHeight || 0
  const scrollTop = window.scrollY ?? window.pageYOffset ?? doc.scrollTop ?? 0
  return { viewportHeight, scrollHeight, scrollTop, maxScroll: Math.max(0, scrollHeight - viewportHeight) }
}
function updateReadingScrollIndicators(metrics: { viewportHeight: number; scrollHeight: number; scrollTop: number; maxScroll: number } | null) {
  if (!metrics || metrics.maxScroll <= 2) {
    readingProgressPct.value = active.value?.completed ? 100 : 0
  } else {
    readingProgressPct.value = Math.min(100, Math.max(0, Math.round((metrics.scrollTop / metrics.maxScroll) * 100)))
  }
  const anchors = sectionAnchors.value
  if (!anchors.length) return
  let current = anchors[0].id
  for (const anchor of anchors) {
    const el = typeof document !== 'undefined' ? document.getElementById(anchor.id) : null
    if (!el || typeof el.getBoundingClientRect !== 'function') continue
    if (el.getBoundingClientRect().top <= 160) {
      current = anchor.id
    }
  }
  activeAnchorId.value = current
}
function jumpToAnchor(id: string) {
  activeAnchorId.value = id
  const el = typeof document !== 'undefined' ? document.getElementById(id) : null
  el?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}
function hideSelectionToolbar() {
  selectionToolbar.value.visible = false
}
function inspectTextSelection() {
  if (typeof window === 'undefined' || typeof window.getSelection !== 'function') return
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !readingArticleRef.value) {
    hideSelectionToolbar()
    return
  }
  const text = sel.toString().trim()
  if (text.length < 2) {
    hideSelectionToolbar()
    return
  }
  const anchorInside = sel.anchorNode ? readingArticleRef.value.contains(sel.anchorNode) : false
  const focusInside = sel.focusNode ? readingArticleRef.value.contains(sel.focusNode) : false
  if (!anchorInside && !focusInside) {
    hideSelectionToolbar()
    return
  }
  if (!sel.rangeCount) return
  const rect = sel.getRangeAt(0).getBoundingClientRect()
  const vw = window.innerWidth || 1024
  selectionToolbar.value = {
    visible: true,
    text,
    top: Math.max(12, Math.round(rect.top - 48)),
    left: Math.min(Math.max(12, Math.round(rect.left + rect.width / 2 - 148)), Math.max(12, vw - 310)),
  }
}
function clearNativeSelection() {
  hideSelectionToolbar()
  try {
    window.getSelection()?.removeAllRanges()
  } catch {
    // 忽略测试或受限环境选区清理失败
  }
}
function excerptToNote() {
  const snippet = selectionToolbar.value.text
  if (!snippet) return
  notesPanelRef.value?.prefillExcerpt({ text: snippet, title: active.value?.title })
  clearNativeSelection()
}
function askSelectedInTutor() {
  const snippet = selectionToolbar.value.text
  const sec = active.value
  if (!snippet || !sec) return
  clearNativeSelection()
  void router.push({
    path: '/tutor',
    query: {
      ...(courseId.value ? { courseId: String(courseId.value) } : {}),
      sectionId: String(sec.id),
      knowledgePointId: String(sec.knowledgePointId),
      ...(sec.graphEntity ? { entity: sec.graphEntity } : {}),
      prompt: `请结合课程小节《${sec.title}》讲解以下内容：\n“${snippet}”`,
    },
  })
}
function feynmanSelected() {
  const snippet = selectionToolbar.value.text
  const sec = active.value
  if (!snippet || !sec) return
  clearNativeSelection()
  void router.push({
    path: '/tutor',
    query: {
      activity: 'feynman',
      ...(courseId.value ? { courseId: String(courseId.value) } : {}),
      sectionId: String(sec.id),
      knowledgePointId: String(sec.knowledgePointId),
      entity: sec.graphEntity || sec.knowledgePointName,
      prompt: snippet,
    },
  })
}
function evaluateWindowScrollRead() {
  const metrics = getWindowScrollMetrics()
  updateReadingScrollIndicators(metrics)
  const section = active.value
  if (!metrics || metrics.maxScroll <= 2 || loading.value || error.value || !section || saving.value) {
    if (metrics) lastScrollTop = metrics.scrollTop
    return
  }
  const isScrollingDown = metrics.scrollTop > lastScrollTop
  lastScrollTop = metrics.scrollTop
  if (requireDownwardScroll) {
    if (!isScrollingDown) return
    requireDownwardScroll = false
  }
  const atBottom = metrics.scrollTop + metrics.viewportHeight >= metrics.scrollHeight - 24
  if (!atBottom) {
    if (suppressedAutoReadIds.has(section.id) && metrics.scrollTop + metrics.viewportHeight < metrics.scrollHeight - 120) {
      suppressedAutoReadIds.delete(section.id)
    }
    return
  }
  if (!section.completed && !suppressedAutoReadIds.has(section.id)) {
    void persistCompleted(section, true, 'scroll')
  }
}
function onWindowWheel(event: WheelEvent) {
  if (event.deltaY <= 0) return
  const metrics = getWindowScrollMetrics()
  const section = active.value
  if (!metrics || metrics.maxScroll > 2 || metrics.scrollHeight <= 0 || loading.value || error.value || !section || saving.value) return
  if (!section.completed && !suppressedAutoReadIds.has(section.id)) {
    void persistCompleted(section, true, 'scroll')
  }
}
function onWindowScroll() {
  if (typeof window.requestAnimationFrame === 'function') {
    if (scrollRafId !== null) return
    let rafCompleted = false
    const handle = window.requestAnimationFrame(() => {
      rafCompleted = true
      scrollRafId = null
      evaluateWindowScrollRead()
    })
    if (!rafCompleted) scrollRafId = handle
    return
  }
  evaluateWindowScrollRead()
}
const outlineOpen = ref(typeof window === 'undefined' ? true : window.innerWidth > 760)
watch(activeId, (next, prev) => {
  autoReadNotice.value = ''
  hideSelectionToolbar()
  activeAnchorId.value = 'anchor-objectives'
  const metrics = getWindowScrollMetrics()
  lastScrollTop = metrics?.scrollTop ?? 0
  readingProgressPct.value = active.value?.completed ? 100 : 0
  requireDownwardScroll = prev !== null && next !== prev && (metrics?.scrollTop ?? 0) > 0
  if (typeof window !== 'undefined' && window.innerWidth <= 760) outlineOpen.value = false
})
watch(() => route.query.courseId, value => { const next = Number(value); if (next > 0 && next !== courseId.value) { courseId.value = next; void loadCourse(next) } })
watch(() => route.query.sectionId, value => { const next = Number(value); if (sections.value.some(s => s.id === next)) activeId.value = next })
watch(() => route.query.knowledgePointId, value => {
  const nextKp = Number(value)
  if (nextKp > 0) {
    const match = sections.value.find(s => s.knowledgePointId === nextKp)
    if (match) activeId.value = match.id
  }
})
onMounted(() => {
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  window.addEventListener('wheel', onWindowWheel, { passive: true })
  document.addEventListener('selectionchange', inspectTextSelection)
  void loadCourses()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onWindowScroll)
  window.removeEventListener('wheel', onWindowWheel)
  document.removeEventListener('selectionchange', inspectTextSelection)
  if (scrollRafId !== null && typeof window.cancelAnimationFrame === 'function') {
    window.cancelAnimationFrame(scrollRafId)
    scrollRafId = null
  }
})
</script>

<template>
  <div class="page-stack">
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
          <details id="course-outline" class="study-outline" :open="outlineOpen" @toggle="outlineOpen = ($event.target as HTMLDetailsElement).open" aria-label="章节目录"><summary class="outline-heading"><h2>章节目录</h2><span>{{ sections.length }} 节</span></summary><div><section v-for="(chapter, index) in study.chapters" :key="chapter.id"><h3>{{ String(index + 1).padStart(2, '0') }} {{ chapter.name }}</h3><p>{{ chapter.description }}</p><button v-for="section in chapter.sections" :key="section.id" class="outline-item" :class="{ active: active?.id === section.id }" :aria-current="active?.id === section.id ? 'page' : undefined" :aria-controls="'section-' + section.id" @click="chooseSection(section)"><span class="outline-status" aria-hidden="true">{{ section.completed ? '✓' : '○' }}</span><span>{{ section.title }}<small>{{ section.completed ? '已完成阅读' : section.knowledgePointName }}</small></span></button></section></div></details>
          <LearningNotesPanel v-if="active" ref="notesPanelRef" :section-id="active.id" :knowledge-point-id="active.knowledgePointId" start-collapsed />
        </div>
        <article v-if="active" ref="readingArticleRef" :id="'section-' + active.id" class="study-reading reading-flow" :aria-labelledby="'section-title-' + active.id" @mouseup="inspectTextSelection">
          <div class="reading-progress-bar no-print" role="progressbar" aria-label="当前小节阅读滚动进度" :aria-valuenow="readingProgressPct" aria-valuemin="0" aria-valuemax="100" data-testid="reading-progress-bar">
            <span class="reading-progress-track"><i class="reading-progress-fill" :style="{ width: readingProgressPct + '%' }"></i></span>
            <small class="reading-progress-text">本节阅读 {{ readingProgressPct }}%</small>
          </div>
          <p class="eyebrow">小节 {{ position + 1 }} / {{ sections.length }} · {{ active.knowledgePointName }}</p><h2 :id="'section-title-' + active.id">{{ active.title }}</h2>
          <nav v-if="sectionAnchors.length > 1" class="section-toc no-print" aria-label="本节段落目录" data-testid="section-toc">
            <button
              v-for="anchor in sectionAnchors"
              :key="anchor.id"
              type="button"
              class="section-toc-item"
              :class="{ active: activeAnchorId === anchor.id }"
              @click="jumpToAnchor(anchor.id)"
            >
              {{ anchor.label }}
            </button>
          </nav>
          <section id="anchor-objectives" class="study-objectives"><h3>本节学习目标</h3><ul><li v-for="objective in active.objectives" :key="objective">{{ objective }}</li></ul></section>
          <section v-for="(block, blockIdx) in active.content" :id="'anchor-block-' + blockIdx" :key="block.heading" class="reading-block"><h3>{{ block.heading }}</h3><MarkdownContent v-for="paragraph in block.paragraphs" :key="paragraph" :content="paragraph" /></section>
           <section v-if="active.resources.length" id="anchor-resources" class="study-resources"><h3>延伸阅读</h3><div v-for="resource in active.resources" :key="resource.url"><a v-if="safeUrl(resource.url)" :href="safeUrl(resource.url)" target="_blank" rel="noopener noreferrer">{{ resource.title }} ↗</a><strong v-else>{{ resource.title }}</strong><p>{{ resource.description }}</p></div></section>
          <LearningContextActions :context="{ courseId: courseId ?? undefined, sectionId: active.id, knowledgePointId: active.knowledgePointId, entity: active.graphEntity }" />
           <footer class="study-footer"><div><button class="button button-outline" :disabled="saving" @click="markRead">{{ saving ? '正在保存…' : active.completed ? '✓ 已读 · 撤销标记' : '标记本节已读' }}</button><p>滚动至页面底部自动标记本节已读；阅读记录与练习掌握度分别统计。</p><span v-if="autoReadNotice" class="auto-read-status" role="status">{{ autoReadNotice }}</span><span v-if="progressError" role="alert">{{ progressError }}</span></div><div class="section-paging"><button class="text-button" :disabled="position <= 0" @click="sections[position - 1] && chooseSection(sections[position - 1]!)">上一节</button><button class="text-button" :disabled="position >= sections.length - 1" @click="sections[position + 1] && chooseSection(sections[position + 1]!)">下一节 →</button></div></footer>
        </article>
      </div>
    </template>
    <Teleport to="body">
      <div
        v-if="selectionToolbar.visible"
        class="floating-selection-toolbar no-print"
        data-testid="course-selection-toolbar"
        role="toolbar"
        aria-label="选中文本快捷学习操作"
        :style="{ top: selectionToolbar.top + 'px', left: selectionToolbar.left + 'px' }"
        @mousedown.prevent
      >
        <button type="button" class="selection-tool-btn" data-testid="selection-to-note" @click="excerptToNote"><BookmarkPlus :size="14" /><span>摘录到笔记</span></button>
        <button type="button" class="selection-tool-btn" data-testid="selection-to-tutor" @click="askSelectedInTutor"><MessageSquarePlus :size="14" /><span>问 AI 助教</span></button>
        <button type="button" class="selection-tool-btn" data-testid="selection-to-feynman" @click="feynmanSelected"><GraduationCap :size="14" /><span>费曼试讲</span></button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.reading-progress-bar{position:sticky;top:calc(var(--topbar-height) + 4px);z-index:6;display:flex;align-items:center;gap:10px;padding:6px 10px;margin:-8px 0 14px;background:color-mix(in srgb,var(--surface) 92%,transparent);border:1px solid var(--border-soft);border-radius:var(--radius-sm);backdrop-filter:blur(6px)}
.reading-progress-track{flex:1;height:5px;overflow:hidden;border-radius:var(--radius-pill);background:var(--surface-tint)}
.reading-progress-fill{display:block;height:100%;background:var(--accent);border-radius:inherit;transition:width 140ms ease}
.reading-progress-text{color:var(--text-subtle);font-size:12px;font-variant-numeric:tabular-nums;white-space:nowrap}
.section-toc{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 16px}
.section-toc-item{padding:4px 10px;border:1px solid var(--border-soft);border-radius:var(--radius-pill);background:var(--surface-soft);color:var(--text-muted);font-size:12px;cursor:pointer;transition:color var(--motion),border-color var(--motion),background var(--motion)}
.section-toc-item:hover,.section-toc-item.active{color:var(--accent-strong);background:var(--accent-soft);border-color:var(--border-focus);font-weight:600}
.floating-selection-toolbar{position:fixed;z-index:1600;display:inline-flex;align-items:center;gap:4px;padding:4px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-sm);box-shadow:var(--shadow-md)}
.selection-tool-btn{display:inline-flex;align-items:center;gap:5px;padding:6px 10px;border:0;border-radius:var(--radius-xs);background:transparent;color:var(--text);font-size:12px;font-weight:600;cursor:pointer;white-space:nowrap}
.selection-tool-btn:hover{color:var(--accent-strong);background:var(--accent-soft)}
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
.study-outline{padding:16px}
.study-outline summary{display:flex;list-style:none;cursor:pointer}
.study-outline summary::-webkit-details-marker{display:none}
.outline-heading{width:100%;display:flex;align-items:baseline;justify-content:space-between;gap:8px;padding-bottom:12px;border-bottom:1px solid var(--border-soft)}
.outline-heading span,.study-outline section>p{color:var(--text-muted);font-size:12px}
.study-outline h3{margin:14px 0 3px;font-size:13px}
.study-outline section>p{margin:0 0 8px}
.outline-item{display:flex;align-items:flex-start;gap:8px;width:100%;padding:8px;text-align:left;border:0;background:transparent;border-radius:var(--radius-sm);font-size:13px}
.outline-item>span:last-child{min-width:0;overflow-wrap:anywhere}
.outline-item.active{color:var(--accent-strong);background:var(--accent-soft);box-shadow:inset 2px 0 var(--accent)}
.outline-item small,.outline-status{font-size:12px;color:var(--text-muted)}
.outline-item small{display:block;margin-top:2px}
.outline-status{flex:0 0 16px;color:var(--accent);text-align:center}
.study-reading{padding:24px clamp(20px,3vw,40px);font-size:15px;line-height:1.7}
.study-reading.reading-flow{max-width:var(--reading-width)}
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
.auto-read-status{display:block;margin-top:6px;color:var(--accent-strong);font-size:12px;font-weight:600}
.study-footer [role=alert]{display:block;margin-top:6px;color:var(--danger);font-size:13px}
.study-notice,.study-empty{padding:20px;border-radius:var(--radius-md)}
.study-notice{color:var(--danger);background:var(--danger-soft)}
.study-empty{color:var(--text-muted);text-align:center;background:var(--surface);border:1px solid var(--border-default)}
@media(min-width:761px){
  .study-outline{position:sticky;top:calc(var(--topbar-height) + 16px);max-height:calc(100dvh - var(--topbar-height) - 40px);overflow-y:auto}
  .study-outline summary{cursor:default;pointer-events:none}
}
@media(max-width:760px){
  .study-layout{grid-template-columns:1fr}
  .study-summary{align-items:flex-start;flex-direction:column}
  .study-outline{max-height:none}
  .study-reading{padding:20px 16px}
}
</style>
