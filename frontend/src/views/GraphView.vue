<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Graph } from '@antv/g6'
import { ArrowUpRight, BookOpen, CheckSquare, CircleHelp, Clock3, GraduationCap, Network, Sparkles } from '@lucide/vue'
import { learningApi, type GraphResponse, type KnowledgePoint } from '../api/client'
import { bindGraphViewportSync, graphViewportPadding, type GraphViewportInstance } from '../domain/graphViewport'
import { effectiveMasteryPercent, peakMasteryPercent, retentionAlertLabel } from '../domain/masteryRetention'
import SectionHeading from '../components/SectionHeading.vue'
import PrerequisitePathPanel from './PrerequisitePathPanel.vue'
import VisualizationViewport from '../components/VisualizationViewport.vue'
import ThreeGraphCanvas from '../components/ThreeGraphCanvas.vue'
import GraphEvidenceDrawer, { type EvidenceTriple } from '../components/GraphEvidenceDrawer.vue'

const graphHost = ref<HTMLElement | null>(null)
const route = useRoute()
const router = useRouter()
const mode = ref<'graph' | 'path'>(route.query.view === 'path' ? 'path' : 'graph')
const courseId = computed(() => Number(route.query.courseId) || undefined)
const pathTargetId = computed(() => Number(route.query.knowledgePointId) || undefined)
const entity = ref('需求分析')
const selectedNode = ref('需求分析')
const graphData = ref<GraphResponse>({ nodes: [], edges: [], evidence: [], available: false })
const coursePoints = ref<KnowledgePoint[]>([])
const loading = ref(false)
const error = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null
const graphUnavailable = computed(() => !loading.value && !error.value && !graphData.value.available)
const renderMode = ref<'2d' | '3d'>('2d')
const isFullscreen = ref(false)
const relationFilter = ref('ALL')
const relationOptions = computed(() => Array.from(new Set(graphData.value.edges.map(edge => edge.label))).sort())
const visibleEdges = computed(() => relationFilter.value === 'ALL' ? graphData.value.edges : graphData.value.edges.filter(edge => edge.label === relationFilter.value))
const averageEvidenceScore = computed(() => { const values = visibleEdges.value.map(edge => edge.score).filter((score): score is number => typeof score === 'number'); return values.length ? (values.reduce((sum, score) => sum + score, 0) / values.length).toFixed(2) : '—' })
const evidenceOpen = ref(false)
const selectedEvidence = computed<EvidenceTriple[]>(() => graphData.value.evidence.filter((value): value is EvidenceTriple => {
  if (!value || typeof value !== 'object') return false
  const triple = value as Partial<EvidenceTriple>
  return typeof triple.subject === 'string' && typeof triple.predicate === 'string' && typeof triple.object === 'string' && (triple.subject === selectedNode.value || triple.object === selectedNode.value)
}))
let graph: GraphViewportInstance | null = null
let viewportSync: ReturnType<typeof bindGraphViewportSync> | null = null

function destroyGraph() {
  viewportSync?.destroy()
  viewportSync = null
  graph?.destroy()
  graph = null
}

async function drawGraph() {
  await nextTick()
  if (!graphHost.value) return
  destroyGraph()
  if (!graphData.value.nodes.length || !graphData.value.edges.length) return

  const instance = new Graph({
    container: graphHost.value,
    autoResize: true,
    autoFit: 'view',
    padding: graphViewportPadding(isFullscreen.value),
    data: {
      nodes: graphData.value.nodes.map((node) => ({
        id: node.id,
        data: { label: node.label },
        style: { fill: node.id === selectedNode.value ? '#176b57' : '#e6f3ed', stroke: '#348b70', lineWidth: 1.4, size: node.id === selectedNode.value ? 58 : 44, labelText: node.label, labelFill: '#19332c', labelFontSize: 12, labelPlacement: 'bottom', labelOffsetY: 7 },
      })),
      edges: visibleEdges.value.map((edge, index) => ({
        id: 'edge-' + index,
        source: edge.source,
        target: edge.target,
        data: { label: edge.label },
        style: { stroke: '#849b95', lineWidth: 1.4, endArrow: true, labelText: edge.label, labelFill: '#475b56', labelFontSize: 12, labelBackground: true, labelBackgroundFill: '#f7f8fa' },
      })),
    },
    layout: { type: 'force', preventOverlap: true, linkDistance: 160, nodeSize: 56, gravity: 14 },
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element', 'click-select'],
  }) as GraphViewportInstance
  graph = instance
  viewportSync = bindGraphViewportSync(graphHost.value, () => graph, () => isFullscreen.value)
  instance.on('afterlayout', () => { viewportSync?.syncFit(false) })
  instance.on('node:click', (event) => {
    const nodeId = (event as { target?: { id?: string } }).target?.id
    if (typeof nodeId === 'string') { selectedNode.value = nodeId; evidenceOpen.value = true }
  })
  await instance.render()
  viewportSync.syncFit(false)
}

async function searchEntity(value = entity.value) {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  const normalized = value.trim()
  if (!normalized) return
  entity.value = normalized
  selectedNode.value = normalized
  loading.value = true
  error.value = ''
  try {
    graphData.value = await learningApi.graph(normalized)
    if (renderMode.value === '2d') void drawGraph()
  } catch {
    graphData.value = { nodes: [], edges: [], evidence: [], available: false }
    error.value = '知识服务暂时不可用，请确认 Python Bridge 与 NebulaGraph 已启动。'
    destroyGraph()
  } finally {
    loading.value = false
  }
}

function scheduleSearch(value: string) {
  entity.value = value
  if (searchTimer) clearTimeout(searchTimer)
  const normalized = value.trim()
  if (!normalized) return
  searchTimer = setTimeout(() => {
    void searchEntity(normalized)
  }, 260)
}

async function loadKnowledgePoints() {
  if (typeof learningApi.knowledgePoints !== 'function') return
  try {
    const list = await learningApi.knowledgePoints(courseId.value)
    coursePoints.value = Array.isArray(list) ? list : []
  } catch {
    coursePoints.value = []
  }
}
function focusGraph() {
  if (graph?.focusElement && selectedNode.value) graph.focusElement(selectedNode.value, { duration: 350 })
  else graph?.fitView?.({ when: 'always' }, { duration: 350 })
}
function onFullscreenChange(fullscreen: boolean) {
  isFullscreen.value = fullscreen
  if (renderMode.value === '2d') viewportSync?.syncFit(true)
}

const exampleEntities = ['需求分析', '数据流图DFD', '用例图', '机票预定系统']
const selectedRelations = computed(() => visibleEdges.value.filter((edge) => edge.source === selectedNode.value || edge.target === selectedNode.value))
const selectedMasteryPoint = computed(() =>
  coursePoints.value.find((point) => point.graphEntity === selectedNode.value || point.name === selectedNode.value) ?? null,
)
const emit = defineEmits<{ navigate: [view: string, entity?: string] }>()

function setPathEntity(value: string) {
  selectedNode.value = value
  entity.value = value
}

async function switchMode(next: 'graph' | 'path') {
  if (mode.value === next) return
  mode.value = next
  if (next === 'path') {
    destroyGraph()
    return
  }
  if (graphData.value.available && graphData.value.nodes.length && renderMode.value === '2d') await drawGraph()
  else await searchEntity(entity.value)
}

async function switchRenderMode(next: '2d' | '3d') {
  if (renderMode.value === next) return
  renderMode.value = next
  if (next === '3d') { destroyGraph(); return }
  if (graphData.value.available && graphData.value.nodes.length) await drawGraph()
}
watch(relationFilter, () => { if (renderMode.value === '2d') void drawGraph() })

onMounted(() => {
  void loadKnowledgePoints()
  if (mode.value === 'graph') searchEntity(new URLSearchParams(window.location.search).get('entity') || entity.value)
  window.addEventListener('knowledge:select', onSelectEntity)
})
function onSelectEntity(event: Event) {
  const value = (event as CustomEvent<string>).detail
  if (value) {
    if (value === entity.value && (loading.value || graphData.value.available)) return
    entity.value = value
    selectedNode.value = value
    mode.value = 'graph'
    void searchEntity(value)
  }
}
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  window.removeEventListener('knowledge:select', onSelectEntity)
  destroyGraph()
})
</script>

<template>
  <div class="page-stack graph-page">
    <SectionHeading eyebrow="软件工程知识网络" title="知识图谱" description="从一个概念出发，沿着关系探索课程知识。">
      <button class="button button-outline" @click="emit('navigate', 'tutor', selectedNode)"><Sparkles :size="16" />围绕此知识提问</button>
    </SectionHeading>
    <div class="graph-mode-switch" role="tablist" aria-label="知识关系视图"><button :class="{ active: mode === 'graph' }" role="tab" aria-label="图谱浏览" :aria-selected="mode === 'graph'" aria-controls="knowledge-graph-panel" @click="switchMode('graph')">图谱浏览</button><button :class="{ active: mode === 'path' }" role="tab" aria-label="先修学习路径" :aria-selected="mode === 'path'" aria-controls="prerequisite-path-panel" @click="switchMode('path')">先修学习路径</button></div>
    <PrerequisitePathPanel v-if="mode === 'path'" id="prerequisite-path-panel" role="tabpanel" :course-id="courseId" :initial-target-id="pathTargetId" @target-change="setPathEntity" />
    <div v-else id="knowledge-graph-panel" class="graph-browser" role="tabpanel">
    <section class="graph-toolbar" aria-label="图谱数据概况">
      <span class="graph-count"><Network :size="16" />{{ graphData.nodes.length }} 个实体</span>
      <span class="graph-count">{{ graphData.edges.length }} 条关系</span>
      <span class="graph-count">当前查询：{{ selectedNode }}</span>
    </section>
    <section class="graph-explore-layout">
      <div class="graph-panel">
        <VisualizationViewport title="课程知识关系" description="拖动节点、缩放画布，或切换实体查看相邻关系。" :mode="renderMode" :status="renderMode === '3d' ? 'Three.js 3D' : 'G6 2D'" :loading="loading" :error="error" :unavailable="graphUnavailable" :empty="!graphData.nodes.length && graphData.available" searchable :search-value="entity" @search="scheduleSearch" @focus="focusGraph" @retry="searchEntity()" @reset="searchEntity('需求分析')" @mode-change="switchRenderMode" @fullscreen-change="onFullscreenChange">
          <ThreeGraphCanvas v-if="renderMode === '3d'" :nodes="graphData.nodes" :edges="visibleEdges" :selected-id="selectedNode" @select="selectedNode = $event" />
          <div v-else ref="graphHost" class="graph-canvas" data-testid="knowledge-graph"></div>
          <template #footer>
            <div class="graph-viewport-footer"><div class="graph-legend"><span><i class="legend-node"></i>课程知识点</span><span><i class="legend-line"></i>知识关系</span><span class="graph-hint"><CircleHelp :size="14" />拖动节点或画布探索</span></div><div class="graph-examples"><span>快速查看</span><button v-for="item in exampleEntities" :key="item" @click="searchEntity(item)">{{ item }}</button></div></div>
          </template>
          <template #overlay>
            <article v-if="selectedNode" class="fullscreen-node-card">
              <div class="fullscreen-node-head"><strong>{{ selectedNode }}</strong><span class="fullscreen-node-tag">{{ selectedRelations.length }} 条相邻关系</span></div>
              <div class="fullscreen-node-actions"><button class="text-button" type="button" @click="emit('navigate', 'tutor', selectedNode)"><Sparkles :size="13" />围绕此知识提问</button></div>
            </article>
          </template>
        </VisualizationViewport>
      </div>
      <aside class="entity-panel">
        <p class="eyebrow">{{ graphData.available ? '当前选中实体' : '查询实体' }}</p><h2>{{ selectedNode }}</h2><span v-if="graphData.available" class="entity-type">软件工程知识点</span>
        <div class="entity-stats"><div><strong>{{ selectedRelations.length }}</strong><span>关联关系</span></div><div><strong>{{ graphData.evidence.length }}</strong><span>证据三元组</span></div></div>
        <div v-if="selectedMasteryPoint" class="entity-mastery-card" data-testid="entity-mastery-card">
          <div class="entity-mastery-head">
            <span>个人掌握保持度</span>
            <strong>{{ effectiveMasteryPercent(selectedMasteryPoint) }}%</strong>
          </div>
          <div class="entity-mastery-bar">
            <i :style="{ width: effectiveMasteryPercent(selectedMasteryPoint) + '%' }"></i>
          </div>
          <div class="entity-mastery-meta">
            <span>历史峰值 {{ peakMasteryPercent(selectedMasteryPoint) }}%</span>
            <span v-if="retentionAlertLabel(selectedMasteryPoint)" class="entity-mastery-alert">
              <Clock3 :size="12" aria-hidden="true" />{{ retentionAlertLabel(selectedMasteryPoint) }}
            </span>
          </div>
          <div class="entity-mastery-actions">
            <button type="button" class="text-button" @click="router.push({ path: '/courses', query: { courseId: String(selectedMasteryPoint.courseId), knowledgePointId: String(selectedMasteryPoint.id) } })"><BookOpen :size="13" />阅读小节</button>
            <button type="button" class="text-button" @click="router.push({ path: '/practice', query: { knowledgePointId: String(selectedMasteryPoint.id) } })"><CheckSquare :size="13" />专项练习</button>
            <button type="button" class="text-button" @click="router.push({ path: '/tutor', query: { activity: 'feynman', entity: selectedMasteryPoint.graphEntity || selectedMasteryPoint.name, courseId: String(selectedMasteryPoint.courseId) } })"><GraduationCap :size="13" />费曼自检</button>
          </div>
        </div>
        <div v-if="renderMode === '3d'" class="graph-analysis"><p class="mini-heading">交互分析</p><label>关系筛选<select v-model="relationFilter" aria-label="关系类型筛选"><option value="ALL">全部关系</option><option v-for="item in relationOptions" :key="item" :value="item">{{ item }}</option></select></label><div><span>节点度数</span><strong>{{ selectedRelations.length }}</strong></div><div><span>平均证据分</span><strong>{{ averageEvidenceScore }}</strong></div></div>
        <div class="relation-list"><p class="mini-heading">相邻知识</p><div v-if="selectedRelations.length === 0" class="empty-relations">搜索并加载图谱后查看相邻知识。</div><button v-for="edge in selectedRelations" :key="edge.source + edge.label + edge.target" class="relation-item" @click="searchEntity(edge.source === selectedNode ? edge.target : edge.source)"><span>{{ edge.source === selectedNode ? edge.label + ' →' : '← ' + edge.label }}</span><strong>{{ edge.source === selectedNode ? edge.target : edge.source }}</strong><ArrowUpRight :size="14" /></button></div>
         <GraphEvidenceDrawer v-model="evidenceOpen" :node="selectedNode" :triples="selectedEvidence" @ask="emit('navigate', 'tutor', $event)" />
      </aside>
    </section>
    </div>
  </div>
</template>

<style scoped>
.entity-mastery-card {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}
.entity-mastery-head,
.entity-mastery-meta,
.entity-mastery-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
}
.entity-mastery-head {
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--text-muted);
}
.entity-mastery-head strong {
  color: var(--accent-strong);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}
.entity-mastery-bar {
  height: 5px;
  overflow: hidden;
  background: var(--surface-tint);
  border-radius: var(--radius-pill);
}
.entity-mastery-bar i {
  display: block;
  height: 100%;
  background: var(--accent);
  border-radius: inherit;
}
.entity-mastery-meta {
  font-size: 12px;
  color: var(--text-muted);
}
.entity-mastery-alert {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--warning-strong);
  font-weight: 600;
}
</style>
