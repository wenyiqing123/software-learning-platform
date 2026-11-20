<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PrerequisiteQualityReport } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'

interface Course { id: number; code: string; name: string; term: string; description: string; graphSpaceId: number; enabled: boolean }
interface Point { id: number; courseId: number; name: string; graphEntity: string; enabled: boolean }
interface Option { key: string; text: string }
type QuestionType = 'SINGLE' | 'MULTIPLE' | 'TRUE_FALSE'
interface Question { id: number; courseId: number; knowledgePointId: number; type: QuestionType; stem: string; options: Option[]; answer: string[]; explanation: string; difficulty: number; enabled: boolean; source: string }
const loading = ref(true)
const courseLoading = ref(false)
const saving = ref(false)
const authorized = ref(false)
const forbidden = ref(false)
const error = ref('')
const notice = ref('')
const courses = ref<Course[]>([])
const points = ref<Point[]>([])
const questions = ref<Question[]>([])
const prerequisiteQuality = ref<PrerequisiteQualityReport | null>(null)
const qualityLoading = ref(false)
const qualityError = ref('')
const courseId = ref<number | null>(null)
const editCourseId = ref<number | null>(null)
const editQuestionId = ref<number | null>(null)
const search = ref('')
const courseDraft = reactive({ code: '', name: '', term: '', description: '', graphSpaceId: 1 })
const questionDraft = reactive({ knowledgePointId: null as number | null, type: 'SINGLE' as QuestionType, stem: '', options: [{ key: 'A', text: '' }, { key: 'B', text: '' }, { key: 'C', text: '' }, { key: 'D', text: '' }] as Option[], answer: [] as string[], explanation: '', difficulty: 2 })
const selectedCourse = computed(() => courses.value.find(c => c.id === courseId.value))
const filteredQuestions = computed(() => questions.value.filter(q => q.stem.toLowerCase().includes(search.value.trim().toLowerCase())))
const pointNames = computed(() => new Map(points.value.map(p => [p.id, p.name])))
const types: Record<QuestionType, string> = { SINGLE: '单选题', MULTIPLE: '多选题', TRUE_FALSE: '判断题' }
function failure(cause: unknown) { const e = cause as { response?: { status?: number; data?: { message?: string; detail?: string } } }; return e.response?.data?.message || e.response?.data?.detail || (e.response?.status === 403 ? '当前账号没有管理权限。' : '操作未完成，请检查输入并重试。') }
function newCourse() { editCourseId.value = null; Object.assign(courseDraft, { code: '', name: '', term: '', description: '', graphSpaceId: 1 }) }
function editCourse(course: Course) { editCourseId.value = course.id; Object.assign(courseDraft, { code: course.code, name: course.name, term: course.term, description: course.description, graphSpaceId: 1 }); notice.value = '' }
function resetQuestion() { editQuestionId.value = null; Object.assign(questionDraft, { knowledgePointId: null, type: 'SINGLE', stem: '', options: ['A', 'B', 'C', 'D'].map(key => ({ key, text: '' })), answer: [], explanation: '', difficulty: 2 }) }
function changeType() { questionDraft.answer = []; questionDraft.options = questionDraft.type === 'TRUE_FALSE' ? [{ key: 'TRUE', text: '正确' }, { key: 'FALSE', text: '错误' }] : ['A', 'B', 'C', 'D'].map(key => ({ key, text: '' })) }
function editQuestion(question: Question) { editQuestionId.value = question.id; Object.assign(questionDraft, { knowledgePointId: question.knowledgePointId, type: question.type, stem: question.stem, options: question.options.map(o => ({ ...o })), answer: [...question.answer], explanation: question.explanation, difficulty: question.difficulty }); notice.value = '' }
function toggleAnswer(key: string) { questionDraft.answer = questionDraft.type === 'MULTIPLE' ? questionDraft.answer.includes(key) ? questionDraft.answer.filter(k => k !== key) : [...questionDraft.answer, key] : [key] }
function removeOption(key: string) { questionDraft.options = questionDraft.options.filter(o => o.key !== key); questionDraft.answer = questionDraft.answer.filter(k => k !== key) }
function addOption() { const key = 'ABCDEFGH'.split('').find(k => !questionDraft.options.some(o => o.key === k)); if (key) questionDraft.options.push({ key, text: '' }) }
let listVersion = 0
async function loadCourse() {
  const version = ++listVersion
  points.value = []; questions.value = []; prerequisiteQuality.value = null; qualityError.value = ''; resetQuestion(); error.value = ''
  if (!courseId.value) return
  courseLoading.value = true
  try {
    qualityLoading.value = true
    const [pointResponse, questionResponse] = await Promise.all([
      api.get<Point[]>('/admin/knowledge-points', { params: { courseId: courseId.value } }),
      api.get<Question[]>('/admin/questions', { params: { courseId: courseId.value } }),
    ])
    if (version !== listVersion) return
    points.value = pointResponse.data; questions.value = questionResponse.data
    try {
      prerequisiteQuality.value = (await api.get<PrerequisiteQualityReport>('/admin/courses/' + courseId.value + '/prerequisite-quality')).data
      qualityError.value = ''
    } catch {
      prerequisiteQuality.value = null
      qualityError.value = '先修关系检查暂时不可用，请重试。'
    }
  } catch (cause) { if (version === listVersion) error.value = failure(cause) }
  finally { if (version === listVersion) { courseLoading.value = false; qualityLoading.value = false } }
}
async function initialize() {
  loading.value = true; error.value = ''; forbidden.value = false
  try {
    const me = (await api.get<{ role: string }>('/auth/me')).data
    if (me.role !== 'ADMIN') { forbidden.value = true; authorized.value = false; return }
    authorized.value = true
    courses.value = (await api.get<Course[]>('/admin/courses')).data
    if (!courses.value.some(c => c.id === courseId.value)) courseId.value = courses.value[0]?.id ?? null
    await loadCourse()
  } catch (cause) { error.value = failure(cause) }
  finally { loading.value = false }
}
async function saveCourse() {
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const payload = { ...courseDraft, graphSpaceId: 1 }
    const response = editCourseId.value ? await api.put<Course>('/admin/courses/' + editCourseId.value, payload) : await api.post<Course>('/admin/courses', payload)
    courses.value = (await api.get<Course[]>('/admin/courses')).data
    courseId.value = response.data.id; editCourse(response.data); await loadCourse(); notice.value = '课程已保存。'
  } catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
async function toggleCourse(course: Course) {
  saving.value = true; error.value = ''; notice.value = ''
  try { const response = await api.put<Course>('/admin/courses/' + course.id + '/enabled', { enabled: !course.enabled }); Object.assign(course, response.data); notice.value = course.enabled ? '课程已启用。' : '课程已停用，学生将无法打开该课程。' }
  catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
function validateQuestion() {
  if (!questionDraft.knowledgePointId || !points.value.some(p => p.id === questionDraft.knowledgePointId && p.enabled)) return '请选择本课程的已启用知识点。'
  if (!questionDraft.answer.length || (questionDraft.type === 'MULTIPLE' ? questionDraft.answer.length < 2 : questionDraft.answer.length !== 1)) return questionDraft.type === 'MULTIPLE' ? '多选题至少选择两个正确答案。' : '请选择一个正确答案。'
  if (questionDraft.options.some(o => !o.text.trim())) return '每个选项都需要填写文本。'
  return ''
}
async function saveQuestion() {
  error.value = validateQuestion(); notice.value = ''
  if (error.value || !courseId.value) return
  saving.value = true
  try {
    const payload = { ...questionDraft, courseId: courseId.value }
    if (editQuestionId.value) await api.put('/admin/questions/' + editQuestionId.value, payload)
    else await api.post('/admin/questions', payload)
    await loadCourse(); notice.value = '题目已保存。正确答案与解析仅在管理和答题反馈中提供。'
  } catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
async function toggleQuestion(question: Question) {
  saving.value = true; error.value = ''; notice.value = ''
  try { const response = await api.put<Question>('/admin/questions/' + question.id + '/enabled', { enabled: !question.enabled }); Object.assign(question, response.data); notice.value = question.enabled ? '题目已启用。' : '题目已停用，历史答题记录仍保留。' }
  catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
onMounted(initialize)
</script>

<template>
  <div class="page-stack admin-page">
    <SectionHeading eyebrow="教学管理" title="课程与题库管理" description="维护课程信息、知识点映射与客观题。所有更改均由管理员权限校验。"><span class="admin-badge">ADMIN</span></SectionHeading>
    <div v-if="loading" class="admin-empty" role="status">正在校验权限并加载管理数据…</div>
    <div v-else-if="forbidden" class="admin-empty" role="alert"><strong>需要管理员权限</strong><p>请使用本地环境配置的管理员账号登录。</p><RouterLink to="/courses">返回课程学习</RouterLink></div>
    <div v-if="error" class="admin-message error" role="alert">{{ error }} <button v-if="!authorized" class="text-button" @click="initialize">重试</button></div>
    <div v-if="notice" class="admin-message success" role="status">{{ notice }}</div>
    <template v-if="authorized && !loading">
      <section class="admin-panel"><div class="panel-heading"><div><p class="eyebrow">01 / 课程</p><h2>课程信息</h2></div><button class="button button-outline" :disabled="saving" @click="newCourse">新增课程</button></div>
        <div class="admin-columns"><div class="course-list"><div v-if="!courses.length" class="admin-empty">暂无课程，请添加第一门课程。</div><article v-for="course in courses" :key="course.id" class="course-row"><div><strong>{{ course.name }}</strong><small>{{ course.code }} · {{ course.term }}</small><span class="status-label" :class="{ disabled: !course.enabled }">{{ course.enabled ? '已启用' : '已停用' }}</span></div><div class="row-actions"><button class="text-button" :disabled="saving" @click="editCourse(course)">编辑</button><button class="text-button" :disabled="saving" @click="toggleCourse(course)">{{ course.enabled ? '停用' : '启用' }}</button></div></article></div>
          <form class="admin-form" @submit.prevent="saveCourse"><h3>{{ editCourseId ? '编辑课程' : '新增课程' }}</h3><label>课程代码<input v-model.trim="courseDraft.code" aria-label="课程代码" required maxlength="40" placeholder="例如 SE-2026-FALL" :disabled="saving" /></label><div class="form-pair"><label>课程名称<input v-model.trim="courseDraft.name" aria-label="课程名称" required maxlength="120" :disabled="saving" /></label><label>学期<input v-model.trim="courseDraft.term" aria-label="学期" required maxlength="40" placeholder="2026 秋季" :disabled="saving" /></label></div><label>课程简介<textarea v-model.trim="courseDraft.description" aria-label="课程简介" required maxlength="500" rows="3" :disabled="saving"></textarea></label><p class="form-hint">使用现有课程图空间 1。新课程的知识点映射与阅读内容需由教学数据配置后提供。</p><button class="button button-dark" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存课程' }}</button></form></div>
      </section>
       <section class="admin-panel prerequisite-quality-panel"><div class="panel-heading"><div><p class="eyebrow">01A / 关系质量</p><h2>先修关系质量检查</h2></div><span v-if="prerequisiteQuality" class="status-label" :class="{ disabled: !prerequisiteQuality.valid || prerequisiteQuality.warningCount > 0 }">{{ !prerequisiteQuality.valid ? '需要检查' : prerequisiteQuality.warningCount ? '有提醒' : '关系完整' }}</span></div>
         <div v-if="qualityLoading" class="admin-empty" role="status">正在检查当前课程的先修关系…</div>
         <div v-else-if="qualityError" class="admin-message error" role="alert">{{ qualityError }} <button class="text-button" @click="loadCourse">重试</button></div>
         <template v-else-if="prerequisiteQuality"><p class="form-hint">已检查 {{ prerequisiteQuality.pointCount }} 个知识点，发现 {{ prerequisiteQuality.issues.length }} 项关系问题，{{ prerequisiteQuality.validEdgeCount }} 条有效先修关系。</p><div v-if="!prerequisiteQuality.issues.length" class="quality-ok"><span>当前课程的先修关系没有发现问题。</span></div><div v-else class="quality-issues"><article v-for="issue in prerequisiteQuality.issues" :key="issue.code + '-' + issue.pointId + '-' + issue.relatedPointId" class="quality-issue" :class="issue.severity.toLowerCase()"><div class="question-meta"><strong>{{ issue.code }}</strong><span>{{ issue.severity === 'ERROR' ? '错误' : '提醒' }}</span></div><p>{{ issue.message }}</p><small>{{ issue.pointName || '未知知识点' }}<span v-if="issue.relatedPointName"> ↔ {{ issue.relatedPointName }}</span></small></article></div></template><div v-else class="admin-empty">选择课程后显示关系检查结果。</div>
       </section>
      <section class="admin-panel"><div class="panel-heading"><div><p class="eyebrow">02 / 客观题</p><h2>题库维护</h2></div><label class="course-filter">所属课程<select v-model="courseId" aria-label="题库课程" :disabled="saving || courseLoading" @change="loadCourse"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}{{ course.enabled ? '' : '（已停用）' }}</option></select></label></div>
        <div v-if="!selectedCourse" class="admin-empty">请先创建课程。</div><template v-else><p v-if="!selectedCourse.enabled" class="admin-message error">该课程已停用，请先启用课程再新增、编辑或启用题目。</p><div v-if="courseLoading" class="admin-empty" role="status">正在加载课程题库…</div><div v-else class="admin-columns question-columns"><div><label class="search-label">搜索题干<input v-model="search" aria-label="搜索题干" placeholder="输入题干关键词" /></label><p class="form-hint">{{ questions.length }} 道题目 · {{ points.filter(p => p.enabled).length }} 个可用知识点</p><div v-if="!filteredQuestions.length" class="admin-empty">{{ search ? '未找到匹配题目。' : '当前课程暂无题目。' }}</div><article v-for="question in filteredQuestions" :key="question.id" class="question-row"><div class="question-meta"><span>{{ types[question.type] || question.type }}</span><span>难度 {{ question.difficulty }}</span><span>{{ pointNames.get(question.knowledgePointId) || '未映射知识点' }}</span><span class="status-label" :class="{ disabled: !question.enabled }">{{ question.enabled ? '启用' : '停用' }}</span></div><h3>{{ question.stem }}</h3><p>答案：{{ question.answer.join('、') }}</p><div class="row-actions"><button class="text-button" :disabled="saving || !selectedCourse.enabled" @click="editQuestion(question)">编辑题目</button><button class="text-button" :disabled="saving || (!question.enabled && !selectedCourse.enabled)" @click="toggleQuestion(question)">{{ question.enabled ? '停用' : '启用' }}</button></div></article></div>
          <form class="admin-form question-editor" @submit.prevent="saveQuestion"><div class="panel-heading"><h3>{{ editQuestionId ? '编辑题目 #' + editQuestionId : '新增题目' }}</h3><button type="button" class="text-button" :disabled="saving" @click="resetQuestion">清空 / 新增</button></div><fieldset :disabled="saving || !selectedCourse.enabled"><label>知识点<select v-model="questionDraft.knowledgePointId" aria-label="题目知识点" required><option :value="null" disabled>选择本课程知识点</option><option v-for="point in points" :key="point.id" :value="point.id" :disabled="!point.enabled">{{ point.name }}{{ point.enabled ? '' : '（已停用）' }}</option></select></label><p v-if="!points.some(p => p.enabled)" class="form-hint">此课程尚无可用知识点，暂不能发布题目。</p><div class="form-pair"><label>题型<select v-model="questionDraft.type" aria-label="题型" @change="changeType"><option value="SINGLE">单选题</option><option value="MULTIPLE">多选题</option><option value="TRUE_FALSE">判断题</option></select></label><label>难度<select v-model.number="questionDraft.difficulty" aria-label="难度"><option v-for="level in 5" :key="level" :value="level">{{ level }}</option></select></label></div><label>题干<textarea v-model.trim="questionDraft.stem" aria-label="题干" required maxlength="10000" rows="3" placeholder="清晰、独立且可判定的题目"></textarea></label><div class="option-heading"><strong>选项与正确答案</strong><span>{{ questionDraft.type === 'MULTIPLE' ? '至少选择两个正确项' : '选择一个正确项' }}</span></div><div v-for="option in questionDraft.options" :key="option.key" class="option-row"><input :type="questionDraft.type === 'MULTIPLE' ? 'checkbox' : 'radio'" :name="'correct-answer'" :checked="questionDraft.answer.includes(option.key)" :aria-label="'正确答案 ' + option.key" @change="toggleAnswer(option.key)" /><label :for="'option-' + option.key">{{ option.key }}</label><input :id="'option-' + option.key" v-model="option.text" :aria-label="'选项 ' + option.key" required maxlength="2000" :readonly="questionDraft.type === 'TRUE_FALSE'" /><button v-if="questionDraft.type !== 'TRUE_FALSE'" type="button" class="remove-option" :aria-label="'删除选项 ' + option.key" :disabled="questionDraft.options.length <= 2" @click="removeOption(option.key)">×</button></div><button v-if="questionDraft.type !== 'TRUE_FALSE'" type="button" class="text-button" :disabled="questionDraft.options.length >= 8" @click="addOption">＋ 添加选项</button><label>答案解析<textarea v-model.trim="questionDraft.explanation" aria-label="答案解析" required maxlength="10000" rows="4" placeholder="说明正确依据与易错点"></textarea></label><button type="submit" class="button button-dark" :disabled="!points.some(p => p.enabled)">{{ saving ? '正在保存…' : '保存题目' }}</button></fieldset></form></div></template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin-badge { padding:6px 10px; color:var(--green); background:var(--green-pale); border-radius:6px; font-size:11px; font-weight:700; }.admin-panel { padding:24px; background:white; border:1px solid var(--line); border-radius:10px; }.panel-heading { display:flex; align-items:center; justify-content:space-between; gap:18px; margin-bottom:20px; }.panel-heading h2 { margin:0; font-size:19px; }.panel-heading h3 { margin:0; font-size:15px; }.admin-columns { display:grid; grid-template-columns:minmax(0,1fr) minmax(300px,1fr); gap:28px; align-items:start; }.admin-form { display:grid; gap:15px; padding:22px; background:#f7f9f6; border:1px solid var(--line); border-radius:8px; }.admin-form h3 { margin:0; font-size:15px; }.admin-form label,.course-filter,.search-label { display:grid; gap:7px; color:#526159; font-size:12px; }.admin-form input,.admin-form textarea,.admin-form select,.course-filter select,.search-label input { width:100%; min-width:0; padding:10px 11px; color:var(--ink); background:white; border:1px solid #dbe3dc; border-radius:6px; font:inherit; line-height:1.6; }.admin-form textarea { resize:vertical; }.form-pair { display:grid; grid-template-columns:1fr 1fr; gap:12px; }.course-row { display:flex; justify-content:space-between; gap:15px; padding:17px 0; border-bottom:1px solid var(--line); }.course-row strong { display:block; font-size:14px; }.course-row small { display:block; margin:6px 0; color:var(--muted); font-size:11px; }.row-actions { display:flex; gap:15px; align-items:center; }.status-label { display:inline-block; padding:3px 7px; color:var(--green); background:var(--green-pale); border-radius:4px; font-size:10px; }.status-label.disabled { color:#7a6b60; background:#f1ebe6; }.form-hint { margin:0; color:var(--muted); font-size:11px; line-height:1.8; }.admin-message { padding:14px 18px; border-radius:7px; font-size:13px; line-height:1.7; }.admin-message.error { color:#964632; background:#fff0e8; }.admin-message.success { color:var(--green); background:var(--green-pale); }.admin-empty { padding:32px 15px; color:var(--muted); text-align:center; background:white; border:1px solid var(--line); border-radius:8px; font-size:13px; }.admin-empty a { color:var(--green); }.course-filter { min-width:220px; }.question-columns { grid-template-columns:minmax(0,1fr) minmax(350px,1.1fr); }.search-label { margin-bottom:12px; }.question-row { padding:19px 0; border-bottom:1px solid var(--line); }.question-meta { display:flex; flex-wrap:wrap; align-items:center; gap:10px; color:var(--muted); font-size:10px; }.question-row h3 { font-size:13px; line-height:1.9; }.question-row p { color:var(--muted); font-size:12px; }.question-row .row-actions { margin-top:14px; }.admin-form fieldset { display:grid; gap:15px; min-width:0; padding:0; margin:0; border:0; }.option-heading { display:flex; justify-content:space-between; gap:8px; font-size:11px; }.option-heading span { color:var(--muted); }.option-row { display:grid; grid-template-columns:18px 36px minmax(0,1fr) 20px; gap:7px; align-items:center; }.option-row input[type=radio],.option-row input[type=checkbox] { width:16px; accent-color:var(--green); }.option-row label { font-size:11px; }.remove-option { padding:0; background:none; border:0; color:#a35641; font-size:19px; cursor:pointer; }.admin-form .button { justify-self:start; }.quality-ok { padding:14px; color:var(--green); background:var(--green-pale); border-radius:7px; font-size:12px; }.quality-issues { display:grid; gap:9px; margin-top:13px; }.quality-issue { padding:12px; background:#fafbf9; border:1px solid #e5ebe5; border-left:3px solid #b67a32; border-radius:6px; }.quality-issue.error { border-left-color:#a35641; }.quality-issue p { margin:7px 0 4px; color:var(--ink); font-size:12px; }.quality-issue small { color:var(--muted); font-size:10px; }@media(max-width:1100px) { .admin-columns,.question-columns { grid-template-columns:1fr; }.admin-panel { padding:20px; } }@media(max-width:600px) { .panel-heading { flex-wrap:wrap; }.admin-form { padding:16px; }.form-pair { grid-template-columns:1fr; }.admin-panel { padding:16px; }.course-row { flex-wrap:wrap; } }
</style>
