<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, apiError, type Course, type QuestionBankImportResult, type QuestionBankPreviewQuestion, type QuestionBankVersion } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
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
const confirmPublishVersionId = ref<number | null>(null)
const workspaceView = ref<'all' | 'import' | 'versions'>('all')
const previewVersionId = ref<number | null>(null)
const previewQuestions = ref<QuestionBankPreviewQuestion[]>([])
const previewLoading = ref(false)
const previewError = ref('')
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const jsonPrecheck = computed(() => {
  const raw = questionsJson.value.trim()
  if (!raw) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return { valid: false, total: 0, errors: ['JSON 格式有误，请检查括号与引号。'] }
  }
  const rows = Array.isArray(parsed) ? parsed : (parsed as { questions?: unknown })?.questions
  if (!Array.isArray(rows) || !rows.length) {
    return { valid: false, total: 0, errors: ['导入内容必须是非空题目数组。'] }
  }
  const rowErrors: string[] = []
  rows.forEach((row, idx) => {
    const item = (row || {}) as Record<string, unknown>
    const rowNo = idx + 1
    if (!Number.isInteger(Number(item.courseId)) || Number(item.courseId) <= 0) rowErrors.push(`第 ${rowNo} 行缺少有效 courseId`)
    if (!Number.isInteger(Number(item.knowledgePointId)) || Number(item.knowledgePointId) <= 0) rowErrors.push(`第 ${rowNo} 行缺少有效 knowledgePointId`)
    if (!['SINGLE', 'MULTIPLE', 'TRUE_FALSE'].includes(String(item.type || ''))) rowErrors.push(`第 ${rowNo} 行 type 需为 SINGLE/MULTIPLE/TRUE_FALSE`)
    if (typeof item.stem !== 'string' || !item.stem.trim()) rowErrors.push(`第 ${rowNo} 行缺少题干 stem`)
    if (!Array.isArray(item.options) || item.options.length < 2) rowErrors.push(`第 ${rowNo} 行 options 至少需要 2 项`)
    if (!Array.isArray(item.answer) || !item.answer.length) rowErrors.push(`第 ${rowNo} 行 answer 不能为空`)
  })
  return { valid: rowErrors.length === 0, total: rows.length, errors: rowErrors.slice(0, 5), extraErrorCount: Math.max(0, rowErrors.length - 5) }
})

function downloadTemplate() {
  const sample = [
    {
      courseId: selectedCourseId.value || 1,
      knowledgePointId: 1,
      type: 'SINGLE',
      stem: '软件需求分析阶段最重要的输出文档是什么？',
      options: [
        { key: 'A', text: '软件需求规格说明书' },
        { key: 'B', text: '系统测试用例' },
      ],
      answer: ['A'],
      explanation: '需求分析的结果主要沉淀在《软件需求规格说明书》中。',
      difficulty: 2,
    },
  ]
  const blob = new Blob([JSON.stringify(sample, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'question-bank-template.json'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

async function togglePreview(versionId: number) {
  if (previewVersionId.value === versionId) {
    previewVersionId.value = null
    return
  }
  previewVersionId.value = versionId
  previewQuestions.value = []
  previewError.value = ''
  previewLoading.value = true
  try {
    previewQuestions.value = (await api.get<QuestionBankPreviewQuestion[]>('/admin/question-bank/versions/' + versionId + '/questions')).data
  } catch (cause) {
    previewError.value = apiError(cause, '无法读取版本题目明细。')
  } finally {
    previewLoading.value = false
  }
}

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

function publishVersion(id: number) {
  confirmPublishVersionId.value = id
}
async function executePublishVersion() {
  const id = confirmPublishVersionId.value
  if (!id || working.value) return
  working.value = true; error.value = ''; notice.value = ''
  try {
    await api.put('/admin/question-bank/versions/' + id + '/publish')
    confirmPublishVersionId.value = null
    notice.value = '题库版本已发布，旧版本已归档。'
    await loadVersions()
  }
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
    <SectionHeading
      :embedded="props.embedded"
      eyebrow="QUESTION BANK CONTROL"
      title="题库导入与版本管理"
      description="先导入草稿并检查逐行结果，发布后才会将合法题目开放给学生。"
    />
    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>
    <div v-if="notice" class="inline-notice success" role="status">{{ notice }}</div>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在加载题库配置" loading-text="正在读取课程版本和导入状态，请稍候。" />
    <template v-else>
      <section class="question-bank-toolbar">
        <label>课程<select v-model.number="selectedCourseId" aria-label="题库课程"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label>
        <label>草稿版本<select v-model.number="selectedVersionId" aria-label="题库版本"><option :value="undefined">请选择版本</option><option v-for="version in versions" :key="version.id" :value="version.id">{{ version.versionName }} · {{ version.status }}</option></select></label>
        <div class="workspace-tabs" role="tablist" aria-label="工作区视图">
          <button type="button" role="tab" class="workspace-tab" :class="{ active: workspaceView === 'all' }" :aria-selected="workspaceView === 'all'" @click="workspaceView = 'all'">全部</button>
          <button type="button" role="tab" class="workspace-tab" :class="{ active: workspaceView === 'import' }" :aria-selected="workspaceView === 'import'" @click="workspaceView = 'import'">导入题目</button>
          <button type="button" role="tab" class="workspace-tab" :class="{ active: workspaceView === 'versions' }" :aria-selected="workspaceView === 'versions'" @click="workspaceView = 'versions'">版本历史</button>
        </div>
      </section>
      <section v-if="workspaceView !== 'versions'" class="question-bank-columns">
        <article class="question-bank-panel">
          <div class="panel-heading"><div><p class="eyebrow">NEW VERSION</p><h2>创建导入草稿</h2></div></div>
          <label>版本名称<input data-testid="version-name" v-model="versionName" maxlength="120" placeholder="例如：2026 秋季第一次题库" /></label>
          <label>来源文件名<input data-testid="source-filename" v-model="sourceFilename" maxlength="255" placeholder="questions.json" /></label>
          <button class="primary-button" aria-label="创建题库草稿" :disabled="working" @click="createVersion">创建草稿</button>
        </article>
        <article class="question-bank-panel">
          <div class="panel-heading"><div><p class="eyebrow">BATCH IMPORT</p><h2>导入题目 JSON</h2></div><div class="import-head-actions"><button type="button" class="template-button" data-testid="download-bank-template" @click="downloadTemplate">下载 JSON 模板</button><label class="file-button">选择文件<input type="file" accept=".json,application/json" @change="readFile" /></label></div></div>
          <textarea data-testid="questions-json" v-model="questionsJson" rows="12" placeholder="粘贴题目数组，或上传 JSON 文件"></textarea>
          <div v-if="jsonPrecheck" class="precheck-banner" :class="{ ok: jsonPrecheck.valid }" data-testid="json-precheck">
            <strong v-if="jsonPrecheck.valid">预检通过：已识别 {{ jsonPrecheck.total }} 道题目结构</strong>
            <template v-else>
              <strong>前端预检提示（共 {{ jsonPrecheck.total }} 行）：</strong>
              <ul><li v-for="(msg, i) in jsonPrecheck.errors" :key="i">{{ msg }}</li></ul>
              <span v-if="jsonPrecheck.extraErrorCount">另有 {{ jsonPrecheck.extraErrorCount }} 处格式提醒</span>
            </template>
          </div>
          <button class="primary-button" aria-label="导入题目" :disabled="working || !selectedVersionId" @click="importQuestions">导入到当前草稿</button>
          <p class="field-hint">每行题目需要包含 courseId、knowledgePointId、type、stem、options、answer、explanation 和 difficulty。错误行不会阻断同批合法题目。</p>
        </article>
      </section>
      <section v-if="importResult" class="import-result" aria-live="polite"><strong>本批次：共 {{ importResult.totalRows }} 条，成功 {{ importResult.successCount }} 条，错误 {{ importResult.errorCount }} 条</strong><ul><li v-for="item in importResult.errors" :key="item.rowNumber">第 {{ item.rowNumber }} 行：{{ item.message }}</li></ul></section>
      <section v-if="workspaceView !== 'import'" class="version-list"><div class="panel-heading"><div><p class="eyebrow">VERSION HISTORY</p><h2>版本记录</h2></div></div><div v-if="!versions.length" class="empty-state">暂无题库版本，请先创建导入草稿。</div><div v-else class="version-table"><div v-for="version in versions" :key="version.id" class="version-row"><div><strong>{{ version.versionName }}</strong><small>{{ version.sourceFilename || '未填写来源文件' }} · {{ version.questionCount }} 条题目</small></div><span :class="['status-tag', version.status.toLowerCase()]">{{ version.status === 'DRAFT' ? '草稿' : version.status === 'PUBLISHED' ? '已发布' : '已归档' }}</span><div class="version-row-actions"><button type="button" class="text-button" :data-testid="'preview-version-' + version.id" @click="togglePreview(version.id)">{{ previewVersionId === version.id ? '收起题目' : '预览题目' }}</button><button v-if="version.status === 'DRAFT'" class="text-button" :aria-label="'发布题库版本 ' + version.id" :disabled="working || version.questionCount === 0" @click="publishVersion(version.id)">发布版本</button></div></div></div>
      <div v-if="previewVersionId !== null" class="version-preview-panel" data-testid="version-preview-panel">
        <h3>版本 #{{ previewVersionId }} 题目明细</h3>
        <p v-if="previewLoading" class="field-hint">正在读取题目列表…</p>
        <p v-else-if="previewError" class="preview-error">{{ previewError }}</p>
        <p v-else-if="!previewQuestions.length" class="field-hint">该版本下暂无已导入题目。</p>
        <ul v-else class="preview-question-list">
          <li v-for="q in previewQuestions" :key="q.id" class="preview-question-item">
            <div class="preview-question-meta"><span>{{ q.type }}</span><span>难度 {{ q.difficulty }}</span><span>答案：{{ q.answer.join('、') }}</span><span>{{ q.enabled ? '已启用' : '未启用' }}</span></div>
            <strong>{{ q.stem }}</strong>
            <small>{{ q.explanation }}</small>
          </li>
        </ul>
      </div>
      </section>
    </template>
    <ConfirmDialog
      :open="confirmPublishVersionId !== null"
      title="发布题库版本"
      message="发布后将归档当前课程的旧版本，并对学生开放新版本，确认继续吗？"
      confirm-label="确认发布"
      tone="default"
      :busy="working"
      @cancel="confirmPublishVersionId = null"
      @confirm="executePublishVersion"
    />
  </div>
</template>

<style scoped>
.question-bank-toolbar,.question-bank-panel,.version-list,.import-result{background:var(--surface);border:1px solid var(--line);border-radius:var(--radius-md);box-shadow:var(--shadow-sm)}
.question-bank-toolbar{display:flex;gap:18px;flex-wrap:wrap;align-items:end;padding:15px 18px}.workspace-tabs{display:inline-flex;gap:4px;margin-left:auto;padding:3px;background:var(--surface-soft);border:1px solid var(--border-default);border-radius:var(--radius-sm)}.workspace-tab{min-height:30px;padding:4px 10px;color:var(--text-muted);background:transparent;border:0;border-radius:var(--radius-xs);font-size:12px;font-weight:600;cursor:pointer}.workspace-tab.active{color:var(--accent-strong);background:var(--surface)}.import-head-actions,.version-row-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.template-button{min-height:32px;padding:4px 10px;color:var(--accent-strong);background:var(--accent-soft);border:1px solid var(--border-focus);border-radius:var(--radius-sm);font-size:12px;cursor:pointer}.precheck-banner{padding:10px 12px;background:var(--warning-soft);border-radius:var(--radius-sm);font-size:12px;color:var(--warning-strong)}.precheck-banner.ok{background:var(--accent-soft);color:var(--accent-strong)}.precheck-banner ul{margin:6px 0 0;padding-left:18px}.version-preview-panel{margin-top:12px;padding:14px;background:var(--surface-soft);border-radius:var(--radius-sm)}.version-preview-panel h3{margin:0 0 10px;font-size:14px}.preview-error{color:var(--danger);font-size:12px}.preview-question-list{display:grid;gap:8px;margin:0;padding:0;list-style:none}.preview-question-item{display:grid;gap:4px;padding:10px;background:var(--surface);border:1px solid var(--border-soft);border-radius:var(--radius-xs);font-size:12.5px}.preview-question-meta{display:flex;flex-wrap:wrap;gap:8px;color:var(--text-muted);font-size:12px}.question-bank-toolbar label,.question-bank-panel label:not(.file-button){display:grid;gap:6px;color:var(--text-muted);font-size:12px}.question-bank-toolbar select,.question-bank-panel input:not([type=file]),.question-bank-panel textarea{border:1px solid var(--border-default);border-radius:var(--radius-sm);background:var(--surface);color:var(--text);font:inherit}.question-bank-toolbar select,.question-bank-panel input:not([type=file]){min-height:36px;padding:0 10px}.question-bank-columns{display:grid;grid-template-columns:minmax(260px,.7fr) minmax(380px,1.3fr);gap:16px}.question-bank-panel,.version-list{display:grid;gap:15px;padding:20px}.panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.panel-heading h2,.version-list h2{margin:0;color:var(--text);font-size:15px}.question-bank-panel textarea{width:100%;max-height:240px;overflow-y:auto;resize:vertical;padding:10px;line-height:1.55}.primary-button{min-height:36px;border:0;border-radius:var(--radius-sm);padding:0 15px;background:var(--accent);color:var(--surface);cursor:pointer}.primary-button:hover:not(:disabled){background:var(--accent-strong)}.primary-button:disabled,.text-button:disabled{opacity:.5;cursor:not-allowed}.field-hint{margin:0;color:var(--text-muted);font-size:12px;line-height:1.6}.file-button{display:inline-flex;align-items:center;gap:6px;padding:6px 9px;border:1px solid var(--border-default);border-radius:var(--radius-sm);color:var(--text);cursor:pointer}.file-button:focus-within{outline:2px solid var(--accent);outline-offset:2px}.file-button input{position:absolute;width:1px;height:1px;opacity:0}.import-result{padding:16px;color:var(--text);font-size:12px}.import-result ul{margin:10px 0 0;padding-left:20px;color:var(--danger)}.version-list{margin-top:16px}.version-table{display:grid}.version-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:15px;padding:13px 0;border-top:1px solid var(--border-soft)}.version-row strong,.version-row small{display:block}.version-row small{margin-top:4px;color:var(--text-muted);font-size:12px}.status-tag{padding:4px 7px;border-radius:var(--radius-sm);font-size:12px}.status-tag.draft{background:var(--warning-soft);color:var(--warning)}.status-tag.published{background:var(--accent-soft);color:var(--accent)}.status-tag.archived{background:var(--surface-soft);color:var(--text-muted)}.text-button{border:0;background:transparent;color:var(--accent);cursor:pointer;font:inherit;font-size:12px}.success{border-color:var(--accent-soft);color:var(--accent)}@media(max-width:1020px){.question-bank-columns{grid-template-columns:1fr}}@media(max-width:760px){.version-row{grid-template-columns:1fr auto}.version-row .text-button{grid-column:1/-1;justify-self:start}}
</style>
