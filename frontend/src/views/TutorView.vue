<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ArrowRight, Bot, CircleAlert, Database, Gauge, Lightbulb, Sparkles } from '@lucide/vue'
import { useRoute } from 'vue-router'
import { apiError, learningApi } from '../api/client'
import type { TutorSession } from '../api/tutor'
import type { TutorAnswerResult, TutorChatMessage, TutorContextChip } from '../domain/tutorChat'
import SectionHeading from '../components/SectionHeading.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import TutorConversationList from '../components/TutorConversationList.vue'
import TutorMessageList from '../components/TutorMessageList.vue'
import TutorComposer from '../components/TutorComposer.vue'
import type { TutorModeOption } from '../components/TutorModeSelector.vue'

type Mode = 'DIRECT' | 'TRIPLES' | 'RANKED'
interface TutorHintResult { question: string; level: number; content: string; hasEvidence: boolean; evidence: { subject: string; predicate: string; object: string; score: number }[]; nextLevelAvailable: boolean; source: string }
const route = useRoute()
const mode = ref<Mode>('RANKED')
const queryValue = (value: unknown) => typeof value === 'string' ? value : Array.isArray(value) ? String(value[0] || '') : ''
const initialEntity = queryValue(route.query.entity)
const question = ref(initialEntity ? '请解释「' + initialEntity + '」的核心概念及相关知识。' : '')
const loading = ref(false)
const streamingAnswer = ref('')
const streamingEvidence = ref<{ triples?: unknown[] } | null>(null)
const compareAll = ref(false)
const error = ref('')
const failedQuestion = ref('')
const failedMode = ref<string>('RANKED')
const modes: TutorModeOption[] = [
  { key: 'DIRECT', title: '大模型直答', note: '不检索图谱知识', icon: Bot },
  { key: 'TRIPLES', title: '三元组增强', note: '提供图谱事实', icon: Database },
  { key: 'RANKED', title: '相关性排序', note: '评分与提示词增强', icon: Gauge },
]

const sessions = ref<TutorSession[]>([])
const sessionId = ref<number | null>(null)
const messages = ref<TutorChatMessage[]>([])
const historyError = ref('')
const hintLevel = ref(1)
const hintLoading = ref(false)
const hintError = ref('')
const hintResult = ref<TutorHintResult | null>(null)
const context = computed<TutorContextChip[]>(() => ([
  ['课程', route.query.courseId],
  ['小节', route.query.sectionId],
  ['知识点', route.query.knowledgePointId],
  ['实体', route.query.entity],
  ] as Array<[string, unknown]>).flatMap(([label, value]) => {
  const text = queryValue(value)
  return text ? [{ label, value: text }] : []
}))
const quickPrompts = computed(() => {
  const target = context.value.find(item => item.label === '实体')?.value || context.value.find(item => item.label === '知识点')?.value || '这个知识点'
  return [`解释「${target}」`, `举一个「${target}」的例子`, '给我一道练习题']
})
let abortController: AbortController | null = null
onMounted(async () => { try { sessions.value = await learningApi.sessions() } catch (e) { historyError.value = apiError(e, '会话历史暂不可用') } })
function selectMode(selected: string) {
  if (selected === 'DIRECT' || selected === 'TRIPLES' || selected === 'RANKED') mode.value = selected
  compareAll.value = false
}
function stopGenerating() { abortController?.abort() }
onBeforeUnmount(stopGenerating)
async function selectSession() {
  error.value = ''; failedQuestion.value = ''; streamingAnswer.value = ''; streamingEvidence.value = null; messages.value = []
  if (!sessionId.value) return
  try { messages.value = await learningApi.sessionMessages(Number(sessionId.value)) as TutorChatMessage[] } catch (e) { historyError.value = apiError(e) }
}
function selectSessionById(id: number) { sessionId.value = id; void selectSession() }
function newSession() { abortController?.abort(); sessionId.value = null; messages.value = []; failedQuestion.value = ''; error.value = ''; streamingAnswer.value = ''; streamingEvidence.value = null; hintResult.value = null; hintError.value = ''; historyError.value = '' }
async function deleteSession() {
  if (!sessionId.value || loading.value) return
  try { await learningApi.deleteSession(sessionId.value); sessions.value = sessions.value.filter(s => s.id !== sessionId.value); newSession() } catch (e) { historyError.value = apiError(e) }
}
function removeSessionById(id: number) { sessionId.value = id; void deleteSession() }
async function renameSession(id: number, title: string) {
  try {
    const updated = await learningApi.renameSession(id, title)
    sessions.value = sessions.value.map(item => item.id === id ? updated : item)
  } catch (e) { historyError.value = apiError(e, '会话标题保存失败') }
}

async function copyAnswer(value?: string) {
  if (value) await window.navigator.clipboard?.writeText(value)
}

async function requestHint(level = hintLevel.value) {
  const prompt = question.value.trim()
  if (!prompt || hintLoading.value || loading.value) return
  hintLoading.value = true; hintError.value = ''; hintLevel.value = level
  try {
    const data = await learningApi.tutorHint(prompt, level,
      messages.value.slice(-10).map(message => message.content.slice(0, 2000)),
      messages.value.filter(message => message.role === 'ASSISTANT').flatMap(message => message.metadata?.results?.flatMap(result => result.entities?.map(entity => entity.canonicalName) || []) || []).slice(-20))
    hintResult.value = data
    if (!sessionId.value) {
      const created = await learningApi.createSession(prompt.slice(0, 100)) as TutorSession
      sessions.value = [created, ...sessions.value]; sessionId.value = created.id
    }
    try {
      const saved = await learningApi.saveMessage(sessionId.value!, {
        role: 'ASSISTANT', content: '第' + data.level + '级学习提示：' + data.content,
        mode: 'TUTOR_HINT_' + data.level, metadata: { hint: data },
      })
      messages.value.push(saved as TutorChatMessage)
    } catch (cause) { historyError.value = apiError(cause, '提示已生成，但历史记录保存失败') }
  } catch (cause) { hintError.value = apiError(cause, '提示暂时不可用，请检查图谱与模型服务') }
  finally { hintLoading.value = false }
}

async function submit(options: { retry?: boolean; prompt?: string; mode?: string } = {}) {
  const prompt = (options.prompt ?? question.value).trim()
  if (!prompt || loading.value) return
  const retrying = Boolean(options.retry)
  const requestMode = options.mode || (compareAll.value ? 'COMPARE' : mode.value)
  if (requestMode === 'COMPARE') compareAll.value = true
  else if (requestMode === 'DIRECT' || requestMode === 'TRIPLES' || requestMode === 'RANKED') { compareAll.value = false; mode.value = requestMode }
  loading.value = true; error.value = ''; failedQuestion.value = ''; streamingAnswer.value = ''; streamingEvidence.value = null
  abortController = new AbortController()
  try {
    if (!sessionId.value) {
      const created = await learningApi.createSession(prompt.slice(0, 100)) as TutorSession
      sessions.value = [created, ...sessions.value]; sessionId.value = created.id
    }
    if (!retrying) {
      const userMessage = await learningApi.saveMessage(sessionId.value!, { role: 'USER', content: prompt, mode: requestMode })
      messages.value.push(userMessage as TutorChatMessage)
    }
    const historyMessages = messages.value.slice(0, -1).slice(-10).map(m => m.content.slice(0, 2000))
    const historyEntities = messages.value.filter(m => m.role === 'ASSISTANT').flatMap(m => m.metadata?.results?.flatMap(r => r.entities?.map(e => e.canonicalName) || []) || []).slice(-20)
    let generatedResults: TutorAnswerResult[]
    if (compareAll.value) {
      const data = await learningApi.compare(prompt)
      generatedResults = Array.isArray(data) ? data : data.results || [data]
    } else {
      const data = await learningApi.streamAsk(prompt, mode.value, (name, payload) => {
        if (name === 'knowledge') streamingEvidence.value = payload
        if (name === 'delta') streamingAnswer.value += payload.content || ''
      }, abortController.signal, { historyMessages, historyEntities })
      generatedResults = [data]
    }
    const content = generatedResults.map(r => r.error ? '[' + r.mode + '] ' + r.error : '[' + r.mode + '] ' + (r.answer || r.content || '')).join('\n\n')
    if (content.trim()) {
      try {
        const saved = await learningApi.saveMessage(sessionId.value!, { role: 'ASSISTANT', content: content.slice(0, 20000), mode: requestMode, metadata: { results: generatedResults } })
        messages.value.push(saved as TutorChatMessage)
      } catch (e) {
        historyError.value = apiError(e, '回答已生成，但历史记录保存失败')
        messages.value.push({ role: 'ASSISTANT', content: content.slice(0, 20000), mode: requestMode, metadata: { results: generatedResults } })
      }
    }
  } catch (e) {
    error.value = e instanceof DOMException && e.name === 'AbortError' ? '已停止生成，可修改问题后重试。' : apiError(e, 'AI 服务暂不可用，请检查模型与知识服务配置')
    failedQuestion.value = prompt
    failedMode.value = requestMode
  } finally { loading.value = false; abortController = null }
}

function retryQuestion(prompt: string, retryMode: string) {
  question.value = prompt
  void submit({ retry: true, prompt, mode: retryMode })
}

function selectQuickPrompt(prompt: string) {
  question.value = prompt
}

</script>

<template>
  <div class="page-stack tutor-page">
    <SectionHeading eyebrow="基于课程知识图谱" title="AI 学习助教" description="在连续学习会话中提问，查看回答过程和真实图谱证据。"></SectionHeading>
    <section class="tutor-chat-workspace">
      <aside class="tutor-rail">
        <TutorConversationList :sessions="sessions" :active-id="sessionId" :disabled="loading" :error="historyError" @select="selectSessionById" @create="newSession" @rename="renameSession" @remove="removeSessionById"></TutorConversationList>
        <section class="tutor-aside" aria-label="回答策略"><div class="tutor-aside-icon"><Sparkles :size="18" /></div><p class="eyebrow">回答策略</p><h3>{{ compareAll ? '控制变量，对照观察' : modes.find((item) => item.key === mode)?.title }}</h3><p v-if="mode === 'DIRECT' && !compareAll">直接将问题交给大模型，适合作为不使用课程图谱的对照基线。</p><p v-else-if="mode === 'TRIPLES' && !compareAll">检索相关三元组作为上下文，不提供相关性分数，观察结构化知识的作用。</p><p v-else>使用 Python 返回的相关性排序与原始提示词，帮助模型聚焦关键证据。</p><div class="strategy-steps"><span><i>1</i>识别问题实体</span><span><i>2</i>检索图谱邻居</span><span><i>3</i>生成有依据的回答</span></div><div class="aside-footnote">回答可能有误，请结合课程材料核对。</div></section>
      </aside>
      <div class="tutor-chat-main">
        <TutorMessageList :messages="messages" :streaming-answer="streamingAnswer" :streaming-evidence="streamingEvidence" :loading="loading" :error="error" :retry-question="failedQuestion" :retry-mode="failedMode" @retry="retryQuestion" @copy="copyAnswer"></TutorMessageList>
        <TutorComposer :question="question" :mode="mode" :compare-all="compareAll" :loading="loading" :context="context" :quick-prompts="quickPrompts" :modes="modes" @update:question="question = $event" @update:compare-all="compareAll = $event" @select-mode="selectMode" @quick-prompt="selectQuickPrompt" @submit="submit()" @stop="stopGenerating"></TutorComposer>
        <section class="hint-panel">
          <div class="hint-heading"><div><p class="eyebrow">分层学习提示</p><h2>先思考，再展开证据，最后查看解析</h2></div><Lightbulb :size="20" /></div>
          <div class="hint-levels"><button v-for="level in [1, 2, 3]" :key="level" class="hint-level-button" :class="{ selected: hintLevel === level }" :disabled="hintLoading || loading || !question.trim()" @click="requestHint(level)"><strong>第{{ level }}级</strong><small>{{ level === 1 ? '思考方向' : level === 2 ? '知识证据' : '完整解析' }}</small></button></div>
          <div v-if="hintLoading" class="hint-loading"><span class="spinner"></span>正在准备第{{ hintLevel }}级提示</div>
          <div v-else-if="hintError" class="inline-notice" role="alert"><CircleAlert :size="16" />{{ hintError }}<button class="text-button" @click="requestHint(hintLevel)">重试</button></div>
          <div v-else-if="hintResult" class="hint-result"><MarkdownContent :content="hintResult.content" /><div v-if="hintResult.evidence.length" class="hint-evidence-list"><div v-for="item in hintResult.evidence" :key="item.subject + item.predicate + item.object" class="hint-evidence"><span>{{ item.subject }}</span><i>{{ item.predicate }}</i><span>{{ item.object }}</span></div></div><button v-if="hintResult.nextLevelAvailable" class="button button-outline button-small" @click="requestHint(hintResult.level + 1)">展开第{{ hintResult.level + 1 }}级提示 <ArrowRight :size="14" /></button></div>
          <div v-else class="hint-empty">输入问题后，先点击第1级提示；低层提示不会直接展示完整答案。</div>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tutor-chat-workspace{display:grid;grid-template-columns:minmax(215px,246px) minmax(0,1fr);gap:20px;align-items:start;min-width:0}
.tutor-chat-main,.tutor-rail{display:grid;gap:16px;min-width:0}
.tutor-aside{padding:16px;color:var(--text-muted);background:var(--surface-tint);border-radius:var(--radius-md);font-size:13px}
.tutor-aside-icon{float:right;display:grid;place-items:center;width:30px;height:30px;color:var(--accent);background:var(--accent-soft);border-radius:var(--radius-sm)}
.tutor-aside h3{margin:0;font-size:14px;color:var(--text)}
.tutor-aside>p:not(.eyebrow){margin:8px 0;line-height:1.65}
.strategy-steps{display:grid;gap:6px;margin-block:12px}
.strategy-steps span{display:flex;align-items:center;gap:8px;font-size:12px}
.strategy-steps i{display:grid;place-items:center;width:22px;height:22px;color:var(--accent-strong);background:var(--accent-soft);border-radius:50%;font-size:12px;font-style:normal;font-weight:650}
.aside-footnote{padding-top:10px;border-top:1px solid var(--border-default);font-size:12px}
.hint-panel{padding-top:16px;border-top:1px solid var(--border-default)}
.hint-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;color:var(--accent)}
.hint-heading h2{margin:0;color:var(--text);font-size:16px}
.hint-levels{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:12px}
.hint-level-button{display:grid;justify-items:start;gap:2px;min-height:48px;padding:8px 12px;text-align:left}
.hint-level-button small,.hint-empty,.hint-loading{color:var(--text-muted);font-size:12px}
.hint-level-button.selected,.hint-result{background:var(--accent-soft);border-color:var(--border-focus)}
.hint-loading,.hint-empty,.hint-result{margin-top:12px;padding:12px;border-radius:var(--radius-sm)}
.hint-loading{display:flex;align-items:center;gap:8px}
.hint-evidence-list{margin-top:10px}
.hint-evidence{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:8px;padding:8px 0;border-top:1px solid var(--border-default);font-size:12px;overflow-wrap:anywhere}
.hint-evidence i{color:var(--accent-strong);font-style:normal}
.hint-result .button{margin-top:10px}
@media(max-width:760px){.tutor-chat-workspace{grid-template-columns:1fr}.tutor-rail{display:contents}.tutor-aside{grid-row:3}}
</style>
