<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PanelLeftOpen, Plus } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { apiError, learningApi } from '../api/client'
import type { TutorSession } from '../api/tutor'
import type { TutorHintResult } from '../api/types'
import {
  LOCKED_TUTOR_MODE,
  stripLegacyModePrefix,
  type TutorAnswerResult,
  type TutorChatMessage,
  type TutorContextChip,
  type TutorQuickPrompt,
} from '../domain/tutorChat'
import { contextFromQuery, graphHref, type LearningContext } from '../domain/learningContext'
import LearningContextActions from '../components/LearningContextActions.vue'
import TutorConversationList from '../components/TutorConversationList.vue'
import TutorMessageList from '../components/TutorMessageList.vue'
import TutorComposer from '../components/TutorComposer.vue'

const route = useRoute()
const router = useRouter()

const queryValue = (value: unknown) => typeof value === 'string' ? value : Array.isArray(value) ? String(value[0] || '') : ''
const initialEntity = queryValue(route.query.entity)

function hasRouteLearningContext(): boolean {
  return Boolean(
    queryValue(route.query.entity) ||
    queryValue(route.query.knowledgePointId) ||
    queryValue(route.query.courseId) ||
    queryValue(route.query.sectionId)
  )
}

function shouldCollapseSidebarByDefault(): boolean {
  const narrowDesktopOrSmaller = typeof window !== 'undefined' && window.innerWidth < 1360
  return narrowDesktopOrSmaller || hasRouteLearningContext()
}

const question = ref(initialEntity ? '请解释「' + initialEntity + '」的核心概念及相关知识。' : '')
const resolvedPointName = ref('')
const contextDismissed = ref(false)
const loading = ref(false)
const streamingAnswer = ref('')
const streamingEvidence = ref<{ triples?: unknown[] } | null>(null)
const error = ref('')
const failedQuestion = ref('')

const sessions = ref<TutorSession[]>([])
const sessionId = ref<number | null>(null)
const messages = ref<TutorChatMessage[]>([])
const historyError = ref('')
const hintLoading = ref(false)
const failedAsHint = ref(false)

const STEP_HINT_STORAGE_KEY = 'slp-tutor-step-hint-sessions'

function loadStepHintSessions(): Record<number, boolean> {
  if (typeof window === 'undefined') return {}
  try {
    const raw = window.localStorage.getItem(STEP_HINT_STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return {}
    const clean: Record<number, boolean> = {}
    for (const [key, value] of Object.entries(parsed)) {
      const id = Number(key)
      if (Number.isInteger(id) && id > 0 && typeof value === 'boolean') {
        clean[id] = value
      }
    }
    return clean
  } catch {
    return {}
  }
}

function persistStepHintSessions(map: Record<number, boolean>) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STEP_HINT_STORAGE_KEY, JSON.stringify(map))
  } catch {
    // 忽略存储配额限制或隐私模式写入失败
  }
}

const draftStepHint = ref(false)
const sessionStepHints = ref<Record<number, boolean>>(loadStepHintSessions())
const stepHintEnabled = computed<boolean>({
  get() {
    if (sessionId.value) return Boolean(sessionStepHints.value[sessionId.value])
    return draftStepHint.value
  },
  set(enabled: boolean) {
    if (sessionId.value) {
      const next = { ...sessionStepHints.value, [sessionId.value]: enabled }
      sessionStepHints.value = next
      persistStepHintSessions(next)
      return
    }
    draftStepHint.value = enabled
  },
})

const sidebarCollapsed = ref(shouldCollapseSidebarByDefault())
const mobileDrawerOpen = ref(false)
const composerRef = ref<InstanceType<typeof TutorComposer> | null>(null)
const messageListRef = ref<InstanceType<typeof TutorMessageList> | null>(null)

const context = computed<TutorContextChip[]>(() => {
  if (contextDismissed.value) return []
  return ([
    ['课程', route.query.courseId],
    ['小节', route.query.sectionId],
    ['知识点', route.query.knowledgePointId],
    ['实体', route.query.entity],
  ] as Array<[string, unknown]>).flatMap(([label, value]) => {
    const text = queryValue(value)
    return text ? [{ label, value: text }] : []
  })
})

const quickPrompts = computed<TutorQuickPrompt[]>(() => {
  const entityVal = queryValue(route.query.entity)
  const pointVal = resolvedPointName.value || queryValue(route.query.knowledgePointId)
  const target = entityVal || pointVal

  if (!target) {
    return []
  }

  return [
    { tag: '核心概念', title: `解析「${target}」`, prompt: `请解释「${target}」的核心概念、适用场景与关键要点。` },
    { tag: '工程实例', title: `「${target}」应用实例`, prompt: `结合软件工程课程案例，举一个「${target}」的具体例子。` },
    { tag: '易错辨析', title: `常见易混淆点`, prompt: `在学习「${target}」时，有哪些常见的易混淆概念？` },
    { tag: '自测巩固', title: `随堂自测练习`, prompt: `围绕「${target}」给我出一道自测练习题并附思考线索。` },
  ]
})

const learningContext = computed<LearningContext>(() => {
  const base = contextFromQuery(route.query as Record<string, string | string[]>)
  if (resolvedPointName.value && !base.knowledgePointTitle) {
    base.knowledgePointTitle = resolvedPointName.value
  }
  return base
})

const activeSessionTitle = computed(() => {
  if (!sessionId.value) return ''
  return sessions.value.find(s => s.id === sessionId.value)?.title || ''
})

async function resolvePointContext() {
  const entity = queryValue(route.query.entity)
  if (entity) return

  const rawPointId = queryValue(route.query.knowledgePointId)
  if (!rawPointId || !/^[1-9]\d*$/.test(rawPointId)) return
  const pointId = Number(rawPointId)

  const rawCourseId = queryValue(route.query.courseId)
  const courseId = rawCourseId && /^[1-9]\d*$/.test(rawCourseId) ? Number(rawCourseId) : undefined

  try {
    const list = await learningApi.knowledgePoints(courseId)
    if (!Array.isArray(list)) return
    const matched = list.find((item) => item && item.id === pointId)
    if (matched) {
      const name = (matched.name || matched.graphEntity || '').trim()
      if (name) {
        resolvedPointName.value = name
        if (!question.value.trim() && !queryValue(route.query.entity)) {
          question.value = `请解释「${name}」的核心概念及相关知识。`
        }
      }
    }
  } catch {
    // 未知或接口失败时不编造名称，保持既有边界
  }
}

let abortController: AbortController | null = null

watch(
  () => [route.query.entity, route.query.knowledgePointId, route.query.courseId, route.query.sectionId],
  () => {
    contextDismissed.value = false
    if (hasRouteLearningContext()) {
      sidebarCollapsed.value = true
    }
  }
)

onMounted(async () => {
  void resolvePointContext()
  try {
    sessions.value = await learningApi.sessions()
  } catch (e) {
    historyError.value = apiError(e, '会话历史暂不可用')
  }
})

function stopGenerating() {
  abortController?.abort()
}

onBeforeUnmount(stopGenerating)

async function selectSession() {
  error.value = ''
  failedQuestion.value = ''
  failedAsHint.value = false
  streamingAnswer.value = ''
  streamingEvidence.value = null
  messages.value = []
  if (!sessionId.value) return
  try {
    messages.value = await learningApi.sessionMessages(Number(sessionId.value)) as TutorChatMessage[]
  } catch (e) {
    historyError.value = apiError(e)
  }
}

function selectSessionById(id: number) {
  sessionId.value = id
  mobileDrawerOpen.value = false
  void selectSession()
}

function newSession() {
  abortController?.abort()
  sessionId.value = null
  draftStepHint.value = false
  messages.value = []
  failedQuestion.value = ''
  failedAsHint.value = false
  error.value = ''
  streamingAnswer.value = ''
  streamingEvidence.value = null
  historyError.value = ''
  mobileDrawerOpen.value = false
}

async function deleteSession() {
  if (!sessionId.value || loading.value || hintLoading.value) return
  const targetId = sessionId.value
  try {
    await learningApi.deleteSession(targetId)
    sessions.value = sessions.value.filter(s => s.id !== targetId)
    if (sessionStepHints.value[targetId] !== undefined) {
      const { [targetId]: _removed, ...rest } = sessionStepHints.value
      sessionStepHints.value = rest
      persistStepHintSessions(rest)
    }
    newSession()
  } catch (e) {
    historyError.value = apiError(e)
  }
}

function removeSessionById(id: number) {
  sessionId.value = id
  void deleteSession()
}

async function renameSession(id: number, title: string) {
  try {
    const updated = await learningApi.renameSession(id, title)
    sessions.value = sessions.value.map(item => item.id === id ? updated : item)
  } catch (e) {
    historyError.value = apiError(e, '会话标题保存失败')
  }
}

async function copyAnswer(value?: string) {
  if (value) await window.navigator.clipboard?.writeText(value)
}

async function ensureActiveSession(titleSource: string): Promise<number> {
  if (sessionId.value) return sessionId.value
  const pendingStepHint = draftStepHint.value
  const created = await learningApi.createSession(titleSource.slice(0, 100)) as TutorSession
  sessions.value = [created, ...sessions.value]
  sessionId.value = created.id
  const next = { ...sessionStepHints.value, [created.id]: pendingStepHint }
  sessionStepHints.value = next
  persistStepHintSessions(next)
  draftStepHint.value = false
  return created.id
}

async function appendUserPrompt(activeSessionId: number, prompt: string, clearComposerInput: boolean) {
  const userMessage = await learningApi.saveMessage(activeSessionId, {
    role: 'USER',
    content: prompt,
    mode: LOCKED_TUTOR_MODE,
  })
  messages.value.push(userMessage as TutorChatMessage)
  if (clearComposerInput) {
    question.value = ''
  }
}

function buildHistoryContext() {
  const lastMsg = messages.value[messages.value.length - 1]
  const historySource = lastMsg?.role === 'USER' ? messages.value.slice(0, -1) : messages.value
  const historyMessages = historySource.slice(-10).map(message => message.content.slice(0, 2000))
  const historyEntities = messages.value
    .filter(message => message.role === 'ASSISTANT')
    .flatMap(message => message.metadata?.results?.flatMap(result => result.entities?.map(entity => entity.canonicalName) || []) || [])
    .slice(-20)
  return { historyMessages, historyEntities }
}

async function saveAndPushHintMessage(activeSessionId: number, data: TutorHintResult) {
  const hintContent = `第${data.level}级学习提示：${data.content}`
  try {
    const saved = await learningApi.saveMessage(activeSessionId, {
      role: 'ASSISTANT',
      content: hintContent,
      mode: `TUTOR_HINT_${data.level}`,
      metadata: { hint: data },
    }) as TutorChatMessage
    messages.value.push({
      ...saved,
      metadata: saved?.metadata && Object.keys(saved.metadata).length ? saved.metadata : { hint: data },
    })
  } catch (cause) {
    historyError.value = apiError(cause, '提示已生成，但历史记录保存失败')
    messages.value.push({
      role: 'ASSISTANT',
      content: hintContent,
      mode: `TUTOR_HINT_${data.level}`,
      metadata: { hint: data },
    })
  }
}

async function requestHint(promptOverride?: string, level = 1) {
  const prompt = (promptOverride ?? question.value).trim()
  if (!prompt || hintLoading.value || loading.value) return
  hintLoading.value = true
  error.value = ''
  try {
    const { historyMessages, historyEntities } = buildHistoryContext()
    const data = await learningApi.tutorHint(prompt, level, historyMessages, historyEntities)
    const activeSessionId = await ensureActiveSession(prompt)
    if (!promptOverride && question.value.trim() === prompt) {
      await appendUserPrompt(activeSessionId, prompt, true)
    }
    await saveAndPushHintMessage(activeSessionId, data)
  } catch (cause) {
    error.value = apiError(cause, '提示暂时不可用，请检查图谱与模型服务')
  } finally {
    hintLoading.value = false
  }
}

async function submitStepHint(prompt: string, retrying: boolean, fromExplicitPrompt: boolean) {
  hintLoading.value = true
  error.value = ''
  failedQuestion.value = ''
  failedAsHint.value = false
  messageListRef.value?.scrollToBottom(false)

  try {
    const activeSessionId = await ensureActiveSession(prompt)
    if (!retrying) {
      await appendUserPrompt(activeSessionId, prompt, !fromExplicitPrompt)
    }
    const { historyMessages, historyEntities } = buildHistoryContext()
    const data = await learningApi.tutorHint(prompt, 1, historyMessages, historyEntities)
    await saveAndPushHintMessage(activeSessionId, data)
  } catch (cause) {
    error.value = apiError(cause, '提示暂时不可用，请检查图谱与模型服务')
    failedQuestion.value = prompt
    failedAsHint.value = true
  } finally {
    hintLoading.value = false
  }
}

async function submit(options: { retry?: boolean; prompt?: string; forceHint?: boolean } = {}) {
  const prompt = (options.prompt ?? question.value).trim()
  if (!prompt || loading.value || hintLoading.value) return
  const retrying = Boolean(options.retry)
  const useStepHint = options.forceHint !== undefined
    ? options.forceHint
    : (retrying ? failedAsHint.value : stepHintEnabled.value)

  if (useStepHint) {
    await submitStepHint(prompt, retrying, Boolean(options.prompt))
    return
  }

  loading.value = true
  error.value = ''
  failedQuestion.value = ''
  failedAsHint.value = false
  streamingAnswer.value = ''
  streamingEvidence.value = null
  abortController = new AbortController()
  messageListRef.value?.scrollToBottom(false)

  try {
    const activeSessionId = await ensureActiveSession(prompt)
    if (!retrying) {
      await appendUserPrompt(activeSessionId, prompt, !options.prompt)
    }

    const { historyMessages, historyEntities } = buildHistoryContext()

    const data = await learningApi.streamAsk(
      prompt,
      LOCKED_TUTOR_MODE,
      (name, payload) => {
        if (name === 'knowledge') streamingEvidence.value = payload
        if (name === 'delta') streamingAnswer.value += payload.content || ''
      },
      abortController.signal,
      { historyMessages, historyEntities }
    )

    const generatedResults: TutorAnswerResult[] = [data]
    const content = generatedResults
      .map(r => r.error ? stripLegacyModePrefix(r.error) : stripLegacyModePrefix(r.answer || r.content || ''))
      .join('\n\n')

    if (content.trim()) {
      try {
        const saved = await learningApi.saveMessage(activeSessionId, {
          role: 'ASSISTANT',
          content: content.slice(0, 20000),
          mode: LOCKED_TUTOR_MODE,
          metadata: { results: generatedResults },
        }) as TutorChatMessage
        messages.value.push({
          ...saved,
          metadata: saved?.metadata && Object.keys(saved.metadata).length ? saved.metadata : { results: generatedResults },
        })
      } catch (e) {
        historyError.value = apiError(e, '回答已生成，但历史记录保存失败')
        messages.value.push({
          role: 'ASSISTANT',
          content: content.slice(0, 20000),
          mode: LOCKED_TUTOR_MODE,
          metadata: { results: generatedResults },
        })
      }
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      if (streamingAnswer.value.trim()) {
        messages.value.push({
          role: 'ASSISTANT',
          content: `${streamingAnswer.value}\n\n（生成已停止，内容未完成）`,
          mode: LOCKED_TUTOR_MODE,
          metadata: { incomplete: true },
        })
      }
      error.value = '已停止生成，可修改问题后重试。'
    } else {
      error.value = apiError(e, 'AI 服务或知识图谱服务暂不可用，请检查依赖状态')
    }
    failedQuestion.value = prompt
  } finally {
    loading.value = false
    streamingAnswer.value = ''
    streamingEvidence.value = null
    abortController = null
  }
}

function retryQuestion(prompt: string) {
  void submit({ retry: true, prompt })
}

function regenerateQuestion(prompt: string) {
  if (!prompt.trim() || loading.value || hintLoading.value) return
  void submit({ retry: true, prompt, forceHint: false })
}

function selectQuickPrompt(prompt: string) {
  question.value = prompt
  composerRef.value?.focus()
}

function openGraph(entity?: string) {
  const target: LearningContext = {
    ...learningContext.value,
    ...(entity ? { entity } : {}),
  }
  void router.push(graphHref(target))
}

function toggleSidebarDesktop() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<template>
  <div class="tutor-page">
    <section class="tutor-workspace" aria-label="AI 学习助教工作台">
      <div
        v-if="mobileDrawerOpen"
        class="tutor-drawer-backdrop"
        aria-hidden="true"
        @click="mobileDrawerOpen = false"
      ></div>

      <div
        class="tutor-sidebar-pane"
        :class="{ 'is-collapsed': sidebarCollapsed, 'is-mobile-open': mobileDrawerOpen }"
      >
        <TutorConversationList
          :sessions="sessions"
          :active-id="sessionId"
          :disabled="loading"
          :error="historyError"
          @select="selectSessionById"
          @create="newSession"
          @rename="renameSession"
          @remove="removeSessionById"
          @collapse="toggleSidebarDesktop(); mobileDrawerOpen = false"
        />
      </div>

      <div class="tutor-main-pane">
        <!-- 无边框透明画布顶行：空态只显示右侧上下文捷径；有消息时展示会话标题与语义描述 -->
        <header
          class="tutor-canvas-header"
          :class="{ 'is-empty-state': !messages.length && !loading && !hintLoading && !error }"
        >
          <div class="tutor-canvas-left">
            <button
              type="button"
              class="icon-button tutor-expand-btn"
              :class="{ 'is-desktop-visible': sidebarCollapsed }"
              title="展开会话侧栏"
              aria-label="展开会话侧栏"
              data-testid="tutor-expand-sidebar"
              @click="sidebarCollapsed = false; mobileDrawerOpen = !mobileDrawerOpen"
            >
              <PanelLeftOpen :size="16" />
            </button>
            <button
              v-if="sidebarCollapsed"
              type="button"
              class="tutor-topbar-new-btn"
              title="新建学习对话"
              :disabled="loading"
              @click="newSession"
            >
              <Plus :size="14" />
              <span>新对话</span>
            </button>
            <div v-if="messages.length || loading || hintLoading || error" class="tutor-topbar-titles">
              <h1 class="tutor-heading-title">
                {{ activeSessionTitle || 'AI 学习助教' }}
              </h1>
              <p class="section-description tutor-heading-subtitle">
                {{ activeSessionTitle ? '课程知识图谱增强问答' : '基于课程知识图谱进行可溯源问答与分步引导' }}
              </p>
            </div>
          </div>

          <div class="tutor-topbar-right">
            <LearningContextActions :context="learningContext" current-view="tutor" compact />
          </div>
        </header>

        <TutorMessageList
          ref="messageListRef"
          :messages="messages"
          :streaming-answer="streamingAnswer"
          :streaming-evidence="streamingEvidence"
          :loading="loading"
          :error="error"
          :retry-question="failedQuestion"
          :quick-prompts="quickPrompts"
          :hint-loading="hintLoading"
          @retry="retryQuestion"
          @copy="copyAnswer"
          @regenerate="regenerateQuestion"
          @request-hint="requestHint"
          @open-graph="openGraph"
          @quick-prompt="selectQuickPrompt"
        />

        <div class="tutor-composer-dock">
          <TutorComposer
            ref="composerRef"
            :question="question"
            :loading="loading"
            :hint-loading="hintLoading"
            :step-hint-enabled="stepHintEnabled"
            :context="context"
            @update:question="question = $event"
            @update:step-hint-enabled="stepHintEnabled = $event"
            @submit="submit()"
            @stop="stopGenerating"
            @dismiss-context="contextDismissed = true"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tutor-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  background: var(--surface);
}

.tutor-workspace {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
  background: var(--surface);
  overflow: hidden;
}

.tutor-sidebar-pane {
  width: 228px;
  flex-shrink: 0;
  height: 100%;
  border-right: 1px solid var(--border-soft);
  transition: width var(--motion), opacity var(--motion);
  overflow: hidden;
}

.tutor-sidebar-pane.is-collapsed {
  width: 0;
  border-right: 0;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.tutor-main-pane {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: var(--surface);
}

.tutor-canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding: 10px 24px 2px;
  flex-shrink: 0;
}

.tutor-canvas-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.tutor-topbar-titles {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.tutor-heading-title {
  margin: 0;
  color: var(--text);
  font-size: 15.5px;
  font-weight: 650;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
  max-width: 24ch;
}

.tutor-heading-subtitle {
  margin: 0;
  color: var(--text-subtle);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.tutor-topbar-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.tutor-expand-btn {
  display: none;
  width: 32px;
  height: 32px;
  min-height: 32px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.tutor-expand-btn.is-desktop-visible {
  display: grid;
}

.tutor-topbar-new-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 4px 10px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 18%, transparent);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
}

.tutor-topbar-new-btn:hover:not(:disabled) {
  border-color: var(--border-focus);
}

.tutor-composer-dock {
  position: relative;
  flex-shrink: 0;
  padding: 4px 24px 14px;
  background: var(--surface);
}

.tutor-composer-dock::before {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 100%;
  height: 20px;
  background: linear-gradient(to bottom, transparent, var(--surface));
  pointer-events: none;
  content: "";
}

.tutor-drawer-backdrop {
  display: none;
}

@media (max-width: 1120px) {
  .tutor-heading-subtitle {
    display: none;
  }
}

@media (max-width: 900px) {
  .tutor-expand-btn {
    display: grid;
  }

  .tutor-sidebar-pane {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 20;
    width: min(268px, 85%);
    box-shadow: var(--shadow-md);
    transform: translateX(-100%);
    transition: transform var(--motion);
  }

  .tutor-sidebar-pane.is-collapsed {
    width: min(268px, 85%);
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    border-right: 1px solid var(--border-soft);
  }

  .tutor-sidebar-pane.is-mobile-open {
    transform: translateX(0);
  }

  .tutor-drawer-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 15;
    background: var(--el-overlay-color-light);
  }

  .tutor-canvas-header {
    padding: 8px 14px 2px;
  }

  .tutor-topbar-right {
    display: none;
  }

  .tutor-composer-dock {
    padding: 4px 14px 10px;
  }
}
</style>
