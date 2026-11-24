<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, apiError, type Course, type QuestionBankImportResult, type QuestionBankVersion } from '../api/client'
import FeedbackState from '../components/FeedbackState.vue'
import { useFormBaseline, useUnsavedChanges } from '../composables/useUnsavedChanges'

const courses = ref<Course[]>([])
const selectedCourseId = ref<number>()
const versions = ref<QuestionBankVersion[]>([])
const selectedVersionId = ref<number>()
const versionName = ref('')
const sourceFilename = ref('')
const questionsJson = ref('')
const importResult = ref<QuestionBankImportResult>()
const notice = ref('')
const error = ref('')
const loading = ref(true)
const working = ref(false)
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const formDraft = computed(() => ({
  versionName: versionName.value,
  sourceFilename: sourceFilename.value,
  questionsJson: questionsJson.value,
}))
const { isDirty, resetBaseline } = useFormBaseline(formDraft)
useUnsavedChanges(isDirty)

async function loadCourses() {
  try {
    courses.value = (await api.get<Course[]>('/admin/courses')).data
    if (!selectedCourseId.value && courses.value.length) selectedCourseId.value = courses.value[0].id
  } catch (cause) { error.value = apiError(cause, '课程列表暂时无法加载。') }
}

async function loadVersions() {
  if (!selectedCourseId.value) { versions.value = []; return }
  try {
    versions.value = (await api.get<QuestionBankVersion[]>('/admin/question-bank/versions', { params: { courseId: selectedCourseId.value } })).data
    if (selectedVersionId.value && !versions.value.some(version => version.id === selectedVersionId.value)) selectedVersionId.value = undefined
    selectedVersionId.value ||= versions.value.find(version => version.status === 'DRAFT')?.id
  } catch (cause) { error.value = apiError(cause, '题库版本暂时无法加载。') }
}

async function createVersion() {
  if (!selectedCourseId.value || !versionName.value.trim()) { error.value = '请选择课程并填写版本名称。'; return }
  working.value = true; error.value = ''; notice.value = ''
  try {
    const version = (await api.post<QuestionBankVersion>('/admin/question-bank/versions', { courseId: selectedCourseId.value, versionName: versionName.value.trim(), sourceFilename: sourceFilename.value.trim() })).data
    selectedVersionId.value = version.id
    versionName.value = ''
    notice.value = '题库草稿已创建。'
    resetBaseline()
    await loadVersions()
  } catch (cause) { error.value = apiError(cause, '题库草稿创建失败。') }
  finally { working.value = false }
}

async function importQuestions() {
  if (!selectedVersionId.value) { error.value = '请先选择一个草稿版本。'; return }
  let parsed: unknown
  try { parsed = JSON.parse(questionsJson.value) } catch { error.value = 'JSON 格式不正确，请修正后再导入。'; return }
  const questions = Array.isArray(parsed) ? parsed : (parsed as { questions?: unknown })?.questions
  if (!Array.isArray(questions) || !questions.length) { error.value = '导入内容必须是非空题目数组。'; return }
  working.value = true; error.value = ''; notice.value = ''
  try {
    importResult.value = (await api.post<QuestionBankImportResult>('/admin/question-bank/versions/' + selectedVersionId.value + '/import', { questions })).data
    notice.value = '题目批次已导入，合法题目仍需发布后才对学生可见。'
    resetBaseline()
    await loadVersions()
  } catch (cause) { error.value = apiError(cause, '题库导入失败。') }
  finally { working.value = false }
}

async function publishVersion(id: number) {
  if (!window.confirm('发布后将归档当前课程的旧版本，并对学生开放新版本，确认继续吗？')) return
  working.value = true; error.value = ''; notice.value = ''
  try { await api.put('/admin/question-bank/versions/' + id + '/publish'); notice.value = '题库版本已发布，旧版本已归档。'; await loadVersions() }
  catch (cause) { error.value = apiError(cause, '题库版本发布失败。') }
  finally { working.value = false }
}

async function readFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.json') || file.size > 2 * 1024 * 1024) {
    error.value = '请选择不超过 2MB 的 JSON 文件。'
    input.value = ''
    return
  }
  sourceFilename.value = file.name
  questionsJson.value = await file.text()
}

watch(selectedCourseId, () => { selectedVersionId.value = undefined; importResult.value = undefined; void loadVersions() })
onMounted(async () => { loading.value = true; await loadCourses(); await loadVersions(); loading.value = false })
</script>

<template>
  <div class="page-stack question-bank-page">
    <header class="page-heading"><div><p class="eyebrow">QUESTION BANK CONTROL</p><component :is="props.embedded ? 'h2' : 'h1'">题库导入与版本管理</component><p>先导入草稿并检查逐行结果，发布后才会将合法题目开放给学生。</p></div></header>
    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>
    <div v-if="notice" class="inline-notice success" role="status">{{ notice }}</div>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在加载题库配置" loading-text="正在读取课程版本和导入状态，请稍候。" />
    <template v-else>
      <section class="question-bank-toolbar">
        <label>课程<select v-model.number="selectedCourseId" aria-label="题库课程"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label>
        <label>草稿版本<select v-model.number="selectedVersionId" aria-label="题库版本"><option :value="undefined">请选择版本</option><option v-for="version in versions" :key="version.id" :value="version.id">{{ version.versionName }} · {{ version.status }}</option></select></label>
      </section>
      <section class="question-bank-columns">
        <article class="question-bank-panel">
          <div class="panel-heading"><div><p class="eyebrow">NEW VERSION</p><h2>创建导入草稿</h2></div></div>
          <label>版本名称<input data-testid="version-name" v-model="versionName" maxlength="120" placeholder="例如：2026 秋季第一次题库" /></label>
          <label>来源文件名<input data-testid="source-filename" v-model="sourceFilename" maxlength="255" placeholder="questions.json" /></label>
          <button class="primary-button" aria-label="创建题库草稿" :disabled="working" @click="createVersion">创建草稿</button>
        </article>
        <article class="question-bank-panel">
          <div class="panel-heading"><div><p class="eyebrow">BATCH IMPORT</p><h2>导入题目 JSON</h2></div><label class="file-button">选择文件<input type="file" accept=".json,application/json" @change="readFile" /></label></div>
          <textarea data-testid="questions-json" v-model="questionsJson" rows="12" placeholder="粘贴题目数组，或上传 JSON 文件"></textarea>
          <button class="primary-button" aria-label="导入题目" :disabled="working || !selectedVersionId" @click="importQuestions">导入到当前草稿</button>
          <p class="field-hint">每行题目需要包含 courseId、knowledgePointId、type、stem、options、answer、explanation 和 difficulty。错误行不会阻断同批合法题目。</p>
        </article>
      </section>
      <section v-if="importResult" class="import-result" aria-live="polite"><strong>本批次：共 {{ importResult.totalRows }} 条，成功 {{ importResult.successCount }} 条，错误 {{ importResult.errorCount }} 条</strong><ul><li v-for="item in importResult.errors" :key="item.rowNumber">第 {{ item.rowNumber }} 行：{{ item.message }}</li></ul></section>
      <section class="version-list"><div class="panel-heading"><div><p class="eyebrow">VERSION HISTORY</p><h2>版本记录</h2></div></div><div v-if="!versions.length" class="empty-state">暂无题库版本，请先创建导入草稿。</div><div v-else class="version-table"><div v-for="version in versions" :key="version.id" class="version-row"><div><strong>{{ version.versionName }}</strong><small>{{ version.sourceFilename || '未填写来源文件' }} · {{ version.questionCount }} 条题目</small></div><span :class="['status-tag', version.status.toLowerCase()]">{{ version.status === 'DRAFT' ? '草稿' : version.status === 'PUBLISHED' ? '已发布' : '已归档' }}</span><button v-if="version.status === 'DRAFT'" class="text-button" :aria-label="'发布题库版本 ' + version.id" :disabled="working || version.questionCount === 0" @click="publishVersion(version.id)">发布版本</button></div></div></section>
    </template>
  </div>
</template>

<style scoped>
.question-bank-toolbar,.question-bank-panel,.version-list,.import-result{background:var(--surface);border:1px solid var(--line);border-radius:var(--radius-md);box-shadow:var(--shadow-sm)}
.question-bank-toolbar{display:flex;gap:18px;flex-wrap:wrap;padding:15px 18px}.question-bank-toolbar label,.question-bank-panel label:not(.file-button){display:grid;gap:6px;color:var(--muted);font-size:11px}.question-bank-toolbar select,.question-bank-panel input:not([type=file]),.question-bank-panel textarea{border:1px solid var(--line);border-radius:var(--radius-sm);background:var(--surface);color:var(--ink);font:inherit}.question-bank-toolbar select,.question-bank-panel input:not([type=file]){min-height:36px;padding:0 10px}.question-bank-columns{display:grid;grid-template-columns:minmax(260px,.7fr) minmax(420px,1.3fr);gap:16px}.question-bank-panel,.version-list{display:grid;gap:15px;padding:20px}.panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.panel-heading h2,.version-list h2{margin:0;color:var(--ink);font-size:15px}.question-bank-panel textarea{width:100%;max-height:240px;overflow-y:auto;resize:vertical;padding:10px;line-height:1.55}.primary-button{min-height:36px;border:0;border-radius:var(--radius-sm);padding:0 15px;background:var(--accent);color:var(--surface);cursor:pointer}.primary-button:disabled,.text-button:disabled{opacity:.5;cursor:not-allowed}.field-hint{margin:0;color:var(--muted);font-size:10px;line-height:1.6}.file-button{display:inline-flex;align-items:center;gap:6px;padding:6px 9px;border:1px solid var(--line);border-radius:var(--radius-sm);color:var(--ink);cursor:pointer}.file-button:focus-within{outline:2px solid var(--accent);outline-offset:2px}.file-button input{position:absolute;width:1px;height:1px;opacity:0}.import-result{padding:16px;color:var(--ink);font-size:12px}.import-result ul{margin:10px 0 0;padding-left:20px;color:var(--danger)}.version-list{margin-top:16px}.version-table{display:grid}.version-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:15px;padding:13px 0;border-top:1px solid var(--border-soft)}.version-row strong,.version-row small{display:block}.version-row small{margin-top:4px;color:var(--muted);font-size:10px}.status-tag{padding:4px 7px;border-radius:var(--radius-sm);font-size:10px}.status-tag.draft{background:var(--warning-soft);color:var(--warning)}.status-tag.published{background:var(--accent-soft);color:var(--accent)}.status-tag.archived{background:var(--surface-soft);color:var(--text-muted)}.text-button{border:0;background:transparent;color:var(--accent);cursor:pointer;font:inherit;font-size:11px}.success{border-color:var(--accent-soft);color:var(--accent)}@media(max-width:760px){.question-bank-columns{grid-template-columns:1fr}.version-row{grid-template-columns:1fr auto}.version-row .text-button{grid-column:1/-1;justify-self:start}}
</style>
