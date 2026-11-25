<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { PanelLeftOpen, Plus } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { apiError, learningApi } from '../api/client'
import type { TutorSession } from '../api/tutor'
import {
  LOCKED_TUTOR_MODE,
  stripLegacyModePrefix,
  type TutorAnswerResult,
  type TutorChatMessage,
  type TutorContextChip,
  type TutorQuickPrompt,
} from '../domain/tutorChat'
import { contextFromQuery, graphHref, type LearningContext } from '../domain/learningContext'
import SectionHeading from '../components/SectionHeading.vue'
import LearningContextActions from '../components/LearningContextActions.vue'
import TutorConversationList from '../components/TutorConversationList.vue'
import TutorMessageList from '../components/TutorMessageList.vue'
import TutorComposer from '../components/TutorComposer.vue'

const route = useRoute()
const router = useRouter()

const queryValue = (value: unknown) => typeof value === 'string' ? value : Array.isArray(value) ? String(value[0] || '') : ''
const initialEntity = queryValue(route.query.entity)

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

const sidebarCollapsed = ref(false)
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
  if (!sessionId.value) return '新对话'
  return sessions.value.find(s => s.id === sessionId.value)?.title || '学习对话'
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
  messages.value = []
  failedQuestion.value = ''
  error.value = ''
  streamingAnswer.value = ''
  streamingEvidence.value = null
  historyError.value = ''
  mobileDrawerOpen.value = false
}

async function deleteSession() {
  if (!sessionId.value || loading.value) return
  try {
    await learningApi.deleteSession(sessionId.value)
    sessions.value = sessions.value.filter(s => s.id !== sessionId.value)
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

async function requestHint(promptOverride?: string, level = 1) {
  const prompt = (promptOverride ?? question.value).trim()
  if (!prompt || hintLoading.value || loading.value) return
  hintLoading.value = true
  error.value = ''
  try {
    const historyMessages = messages.value.slice(-10).map(message => message.content.slice(0, 2000))
    const historyEntities = messages.value
      .filter(message => message.role === 'ASSISTANT')
      .flatMap(message => message.metadata?.results?.flatMap(result => result.entities?.map(entity => entity.canonicalName) || []) || [])
      .slice(-20)

    const data = await learningApi.tutorHint(prompt, level, historyMessages, historyEntities)
    if (!sessionId.value) {
      const created = await learningApi.createSession(prompt.slice(0, 100)) as TutorSession
      sessions.value = [created, ...sessions.value]
      sessionId.value = created.id
    }
    if (!promptOverride && question.value.trim() === prompt) {
      const userMessage = await learningApi.saveMessage(sessionId.value!, {
        role: 'USER',
        content: prompt,
        mode: LOCKED_TUTOR_MODE,
      })
      messages.value.push(userMessage as TutorChatMessage)
      question.value = ''
    }
    const hintContent = `第${data.level}级学习提示：${data.content}`
    try {
      const saved = await learningApi.saveMessage(sessionId.value!, {
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
  } catch (cause) {
    error.value = apiError(cause, '提示暂时不可用，请检查图谱与模型服务')
  } finally {
    hintLoading.value = false
  }
}

async function submit(options: { retry?: boolean; prompt?: string } = {}) {
  const prompt = (options.prompt ?? question.value).trim()
  if (!prompt || loading.value) return
  const retrying = Boolean(options.retry)

  loading.value = true
  error.value = ''
  failedQuestion.value = ''
  streamingAnswer.value = ''
  streamingEvidence.value = null
  abortController = new AbortController()
  messageListRef.value?.scrollToBottom(false)

  try {
    if (!sessionId.value) {
      const created = await learningApi.createSession(prompt.slice(0, 100)) as TutorSession
      sessions.value = [created, ...sessions.value]
      sessionId.value = created.id
    }
    if (!retrying) {
      const userMessage = await learningApi.saveMessage(sessionId.value!, {
        role: 'USER',
        content: prompt,
        mode: LOCKED_TUTOR_MODE,
      })
      messages.value.push(userMessage as TutorChatMessage)
      if (!options.prompt) {
        question.value = ''
      }
    }

    const lastMsg = messages.value[messages.value.length - 1]
    const historySource = lastMsg?.role === 'USER' ? messages.value.slice(0, -1) : messages.value
    const historyMessages = historySource.slice(-10).map(m => m.content.slice(0, 2000))
    const historyEntities = messages.value
      .filter(m => m.role === 'ASSISTANT')
      .flatMap(m => m.metadata?.results?.flatMap(r => r.entities?.map(e => e.canonicalName) || []) || [])
      .slice(-20)

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
        const saved = await learningApi.saveMessage(sessionId.value!, {
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
  if (!prompt.trim() || loading.value) return
  void submit({ retry: true, prompt })
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
    <header class="tutor-page-bar">
      <SectionHeading title="AI 学习助教" description="基于课程知识图谱进行连续学习问答与分步思考引导。">
        <LearningContextActions :context="learningContext" current-view="tutor" compact />
      </SectionHeading>
    </header>

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
        <div class="tutor-chat-topbar">
          <div class="tutor-chat-topbar-left">
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
            <h2 class="tutor-current-title">{{ activeSessionTitle }}</h2>
          </div>
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
        </div>

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
            :context="context"
            @update:question="question = $event"
            @submit="submit()"
            @stop="stopGenerating"
            @request-hint="requestHint()"
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
  gap: 12px;
}

.tutor-page-bar {
  flex-shrink: 0;
}

.tutor-page-bar :deep(.section-heading) {
  gap: 8px 16px;
}

.tutor-workspace {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.tutor-sidebar-pane {
  width: 256px;
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

.tutor-chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 46px;
  padding: 6px 16px;
  border-bottom: 1px solid var(--border-soft);
  flex-shrink: 0;
}

.tutor-chat-topbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.tutor-expand-btn {
  display: none;
  width: 32px;
  height: 32px;
  min-height: 32px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
}

.tutor-expand-btn.is-desktop-visible {
  display: grid;
}

.tutor-current-title {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tutor-topbar-new-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 4px 10px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 550;
  cursor: pointer;
  flex-shrink: 0;
}

.tutor-topbar-new-btn:hover:not(:disabled) {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--border-default);
}

.tutor-composer-dock {
  flex-shrink: 0;
  padding: 4px 20px 10px;
  background: var(--surface);
}

.tutor-drawer-backdrop {
  display: none;
}

@media (max-width: 820px) {
  .tutor-expand-btn {
    display: grid;
  }

  .tutor-sidebar-pane {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 20;
    width: min(272px, 85%);
    box-shadow: var(--shadow-md);
    transform: translateX(-100%);
    transition: transform var(--motion);
  }

  .tutor-sidebar-pane.is-collapsed {
    width: min(272px, 85%);
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

  .tutor-composer-dock {
    padding: 8px 14px 10px;
  }
}
</style>
