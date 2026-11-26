<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, Compass, FileText, MessageSquare, Moon, Search, Sparkles, Sun, X } from '@lucide/vue'
import { learningApi } from '../api/client'
import type { TutorSession } from '../api/tutor'
import type { KnowledgePoint, LearningNote } from '../api/types'
import { STUDENT_SECTIONS } from './StudentLearningSections.vue'

interface PaletteItem {
  id: string
  group: string
  label: string
  hint: string
  keywords: string
  kind: 'route' | 'action' | 'knowledge' | 'note' | 'session'
  run: () => void | Promise<void>
}

const props = withDefaults(
  defineProps<{
    open: boolean
    role?: string
    isDark?: boolean
  }>(),
  {
    role: 'STUDENT',
    isDark: false,
  }
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'toggle-theme'): void
}>()

const router = useRouter()
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const listRef = ref<HTMLElement | null>(null)
const loadingData = ref(false)
const loadedStudentData = ref(false)
const knowledgeItems = ref<KnowledgePoint[]>([])
const noteItems = ref<LearningNote[]>([])
const sessionItems = ref<TutorSession[]>([])
let previouslyFocused: HTMLElement | null = null

const isAdmin = computed(() => props.role === 'ADMIN')

const ADMIN_ROUTES = [
  { path: '/teaching-dashboard', label: '教学总览', hint: '班级掌握趋势与薄弱环节分析' },
  { path: '/admin/content', label: '内容与题库中心', hint: '课程章节、微课生成与题库维护' },
  { path: '/admin/experiments', label: '实验与评价中心', hint: '对比实验分组、运行与结果导出' },
  { path: '/admin/users', label: '用户管理', hint: '师生账号与角色权限配置' },
]

const GROUP_LABELS: Record<string, string> = {
  overview: '概览与课程',
  'graph-tutor': '图谱与助教',
  practice: '练习与复习',
  plan: '计划与推荐',
  experiment: '实验与评估',
}

function closePalette() {
  emit('update:open', false)
}

function navigateTo(path: string, routeQuery?: Record<string, string | number>) {
  closePalette()
  void router.push(routeQuery ? { path, query: routeQuery } : { path })
}

async function ensureStudentSearchIndex() {
  if (isAdmin.value || loadedStudentData.value || loadingData.value) return
  loadingData.value = true
  try {
    const [kpsResult, notesResult, sessionsResult] = await Promise.allSettled([
      typeof learningApi.knowledgePoints === 'function' ? learningApi.knowledgePoints() : Promise.resolve([]),
      typeof learningApi.notes === 'function' ? learningApi.notes() : Promise.resolve([]),
      typeof learningApi.sessions === 'function' ? learningApi.sessions() : Promise.resolve([]),
    ])
    knowledgeItems.value = kpsResult.status === 'fulfilled' && Array.isArray(kpsResult.value) ? kpsResult.value : []
    noteItems.value = notesResult.status === 'fulfilled' && Array.isArray(notesResult.value) ? notesResult.value : []
    sessionItems.value = sessionsResult.status === 'fulfilled' && Array.isArray(sessionsResult.value) ? sessionsResult.value : []
    loadedStudentData.value = true
  } finally {
    loadingData.value = false
  }
}

const allItems = computed<PaletteItem[]>(() => {
  const items: PaletteItem[] = []

  if (isAdmin.value) {
    for (const r of ADMIN_ROUTES) {
      items.push({
        id: `route:${r.path}`,
        group: '管理工作台',
        label: r.label,
        hint: r.hint,
        keywords: `${r.label} ${r.path} ${r.hint}`,
        kind: 'route',
        run: () => navigateTo(r.path),
      })
    }
  } else {
    for (const [groupId, entries] of Object.entries(STUDENT_SECTIONS)) {
      const groupName = GROUP_LABELS[groupId] || '学习导航'
      for (const entry of entries) {
        items.push({
          id: `route:${entry.path}`,
          group: '学习导航',
          label: entry.label,
          hint: `${groupName} · ${entry.path}`,
          keywords: `${entry.label} ${groupName} ${entry.path}`,
          kind: 'route',
          run: () => navigateTo(entry.path),
        })
      }
    }
  }

  items.push({
    id: 'action:toggle-theme',
    group: '快捷操作',
    label: props.isDark ? '切换到浅色模式' : '切换到深色护眼模式',
    hint: '同步界面与图表配色主题',
    keywords: '主题 深色 浅色 dark light theme',
    kind: 'action',
    run: () => {
      emit('toggle-theme')
      closePalette()
    },
  })

  if (!isAdmin.value) {
    items.push({
      id: 'action:feynman',
      group: '快捷操作',
      label: '开启费曼试讲自检',
      hint: '在 AI 学习助教中验证知识点掌握程度',
      keywords: '费曼 feynman 自检 AI 助教 tutor',
      kind: 'action',
      run: () => navigateTo('/tutor', { activity: 'feynman' }),
    })

    for (const kp of knowledgeItems.value) {
      const desc = (kp.description || '').trim()
      items.push({
        id: `kp:${kp.id}`,
        group: '知识点直达',
        label: kp.name,
        hint: `${kp.graphEntity ? kp.graphEntity + (desc ? ' · ' : '') : ''}${desc ? desc.slice(0, 36) : kp.graphEntity ? '' : '核心知识点'}`,
        keywords: `${kp.name} ${kp.graphEntity || ''} ${desc}`,
        kind: 'knowledge',
        run: () => navigateTo('/courses', { knowledgePointId: kp.id }),
      })
    }

    for (const note of noteItems.value) {
      items.push({
        id: `note:${note.id}`,
        group: '学习笔记',
        label: note.title || '未命名笔记',
        hint: note.tags?.length ? `标签: ${note.tags.join(' / ')}` : note.content.slice(0, 40),
        keywords: `${note.title} ${note.content} ${(note.tags || []).join(' ')}`,
        kind: 'note',
        run: () => navigateTo('/notes'),
      })
    }

    for (const s of sessionItems.value) {
      items.push({
        id: `session:${s.id}`,
        group: '历史助教对话',
        label: s.title || `助教会话 #${s.id}`,
        hint: '打开 AI 学习助教查看历史对话',
        keywords: `${s.title} 对话 助教 session`,
        kind: 'session',
        run: () => navigateTo('/tutor'),
      })
    }
  }

  return items
})

const filteredItems = computed(() => {
  const kw = query.value.trim().toLowerCase()
  if (!kw) {
    // 空查询时优先展示全部导航、快捷操作及少量知识点，避免过长
    const base = allItems.value.filter(item => item.kind === 'route' || item.kind === 'action')
    const extraKps = allItems.value.filter(item => item.kind === 'knowledge').slice(0, 6)
    return [...base, ...extraKps]
  }
  const tokens = kw.split(/\s+/).filter(Boolean)
  return allItems.value
    .filter(item => {
      const haystack = item.keywords.toLowerCase()
      return tokens.every(t => haystack.includes(t))
    })
    .slice(0, 24)
})

watch(filteredItems, (list) => {
  if (list.length === 0) {
    activeIndex.value = -1
  } else if (activeIndex.value < 0 || activeIndex.value >= list.length) {
    activeIndex.value = 0
  }
})

watch(
  () => props.open,
  async (opened) => {
    if (opened) {
      previouslyFocused = typeof document !== 'undefined' ? (document.activeElement as HTMLElement | null) : null
      query.value = ''
      activeIndex.value = 0
      void ensureStudentSearchIndex()
      await nextTick()
      inputRef.value?.focus()
    } else if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
      previouslyFocused.focus()
      previouslyFocused = null
    }
  }
)

watch(activeIndex, async (idx) => {
  if (idx < 0 || !listRef.value) return
  await nextTick()
  const el = listRef.value.querySelector(`[data-palette-index="${idx}"]`) as HTMLElement | null
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ block: 'nearest' })
  }
})

function moveSelection(delta: number) {
  const total = filteredItems.value.length
  if (!total) return
  activeIndex.value = (activeIndex.value + delta + total) % total
}

function executeActive() {
  const item = filteredItems.value[activeIndex.value]
  if (item) void item.run()
}

function onInputKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveSelection(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveSelection(-1)
  } else if (event.key === 'Enter' && !event.isComposing) {
    event.preventDefault()
    executeActive()
  }
}

function onGlobalCaptureKeydown(event: KeyboardEvent) {
  if (!props.open) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopImmediatePropagation()
    closePalette()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalCaptureKeydown, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalCaptureKeydown, true)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="command-palette-backdrop"
      data-testid="command-palette-backdrop"
      @click.self="closePalette"
    >
      <div
        class="command-palette-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="全局命令与快速搜索"
        data-testid="command-palette-dialog"
      >
        <div class="command-palette-head">
          <Search :size="17" class="command-palette-search-icon" aria-hidden="true" />
          <input
            ref="inputRef"
            v-model="query"
            type="text"
            class="command-palette-input"
            data-testid="command-palette-input"
            placeholder="搜索页面、知识点、笔记、会话或输入操作命令..."
            aria-label="搜索命令与学习内容"
            @keydown="onInputKeydown"
          >
          <span v-if="loadingData" class="command-palette-status">索引同步中</span>
          <button
            type="button"
            class="command-palette-close"
            data-testid="command-palette-close"
            aria-label="关闭快速检索"
            @click="closePalette"
          >
            <X :size="16" />
          </button>
        </div>

        <div ref="listRef" class="command-palette-list" role="listbox" aria-label="匹配结果">
          <div v-if="!filteredItems.length" class="command-palette-empty">
            未找到匹配 “{{ query }}” 的页面、知识点或笔记
          </div>
          <button
            v-for="(item, idx) in filteredItems"
            :key="item.id"
            type="button"
            role="option"
            :aria-selected="idx === activeIndex"
            :data-palette-index="idx"
            class="command-palette-item"
            :class="{ active: idx === activeIndex }"
            @mouseenter="activeIndex = idx"
            @click="item.run()"
          >
            <span class="command-palette-item-icon" aria-hidden="true">
              <Compass v-if="item.kind === 'route'" :size="16" />
              <Sun v-else-if="item.id === 'action:toggle-theme' && isDark" :size="16" />
              <Moon v-else-if="item.id === 'action:toggle-theme'" :size="16" />
              <Sparkles v-else-if="item.kind === 'action'" :size="16" />
              <BookOpen v-else-if="item.kind === 'knowledge'" :size="16" />
              <FileText v-else-if="item.kind === 'note'" :size="16" />
              <MessageSquare v-else :size="16" />
            </span>
            <span class="command-palette-item-body">
              <strong>{{ item.label }}</strong>
              <small>{{ item.hint }}</small>
            </span>
            <span class="command-palette-item-group">{{ item.group }}</span>
          </button>
        </div>

        <div class="command-palette-foot">
          <span>↑↓ 选择</span>
          <span>Enter 跳转/执行</span>
          <span>Esc 关闭</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.command-palette-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2100;
  background: var(--surface-overlay);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: min(12vh, 88px) 16px 24px;
}

.command-palette-dialog {
  width: min(640px, 100%);
  max-height: min(72vh, 560px);
  display: flex;
  flex-direction: column;
  background: var(--surface);
  color: var(--text);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.command-palette-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-soft);
}

.command-palette-search-icon {
  color: var(--text-subtle);
  flex-shrink: 0;
}

.command-palette-input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 15px;
  outline: none;
}

.command-palette-input::placeholder {
  color: var(--text-subtle);
}

.command-palette-status {
  font-size: 12px;
  color: var(--text-subtle);
  white-space: nowrap;
}

.command-palette-close {
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--text-muted);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.command-palette-close:hover {
  color: var(--text);
  border-color: var(--border-strong);
}

.command-palette-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.command-palette-empty {
  padding: 28px 16px;
  text-align: center;
  color: var(--text-subtle);
  font-size: 14px;
}

.command-palette-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  transition: background var(--motion), border-color var(--motion);
}

.command-palette-item.active,
.command-palette-item:hover {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent) 26%, var(--border-default));
}

.command-palette-item-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--accent-strong);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.command-palette-item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.command-palette-item-body strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.command-palette-item-body small {
  font-size: 12px;
  color: var(--text-subtle);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.command-palette-item-group {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  white-space: nowrap;
}

.command-palette-foot {
  display: flex;
  gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid var(--border-soft);
  background: var(--surface-soft);
  color: var(--text-subtle);
  font-size: 12px;
}
</style>
