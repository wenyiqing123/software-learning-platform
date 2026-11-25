<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Graph } from '@antv/g6'
import { ArrowRight, CircleAlert, Network, RotateCcw } from '@lucide/vue'
import { api, apiError, learningApi, type MasteryMap, type MasteryMapNode } from '../api/client'
import { bindGraphViewportSync, graphViewportPadding, type GraphViewportInstance } from '../domain/graphViewport'
import SectionHeading from '../components/SectionHeading.vue'
import VisualizationViewport from '../components/VisualizationViewport.vue'
import GraphEvidenceDrawer from '../components/GraphEvidenceDrawer.vue'
import ThreeGraphCanvas from '../components/ThreeGraphCanvas.vue'
import LearningContextActions from '../components/LearningContextActions.vue'

interface Course { id: number; name: string; term: string }
const router = useRouter()
const host = ref<HTMLElement | null>(null)
const courses = ref<Course[]>([])
const courseId = ref<number | undefined>()
const map = ref<MasteryMap | null>(null)
const loading = ref(true)
const error = ref('')
const renderMode = ref<'2d' | '3d'>('2d')
const isFullscreen = ref(false)
const selectedNodeId = ref<number | undefined>()
const evidenceOpen = ref(false)
let graph: GraphViewportInstance | null = null
let viewportSync: ReturnType<typeof bindGraphViewportSync> | null = null

const selectedNode = computed(() => map.value?.nodes.find(node => node.id === selectedNodeId.value) ?? null)

function destroyGraph() {
  viewportSync?.destroy()
  viewportSync = null
  graph?.destroy()
  graph = null
}
function statusLabel(status: MasteryMapNode['status']) { return ({ MASTERED: '已掌握', IN_PROGRESS: '学习中', BLOCKED: '被先修知识点阻塞', UP_NEXT: '下一步学习' }[status]) }
function statusClass(status: MasteryMapNode['status']) { return 'mastery-' + status.toLowerCase() }
async function draw() {
  await nextTick()
  if (!host.value || !map.value) return
  destroyGraph()
  const instance = new Graph({
    container: host.value,
    autoResize: true,
    autoFit: 'view',
    padding: graphViewportPadding(isFullscreen.value),
    data: {
      nodes: map.value.nodes.map(node => ({ id: String(node.id), data: { label: node.name }, style: { fill: node.status === 'MASTERED' ? '#d9eee0' : node.status === 'BLOCKED' ? '#f4e2da' : '#fff4d8', stroke: node.status === 'MASTERED' ? '#4b986b' : node.status === 'BLOCKED' ? '#b86d59' : '#c79a42', lineWidth: 1.5, size: 50, labelText: node.name, labelFill: '#19332c', labelFontSize: 11, labelPlacement: 'bottom', labelOffsetY: 7 } })),
      edges: map.value.edges.map((edge, index) => ({ id: 'mastery-edge-' + index, source: String(edge.source), target: String(edge.target), data: { label: edge.relation }, style: { stroke: '#9bafa3', lineWidth: 1.2, endArrow: true, labelText: edge.relation, labelFill: '#60766e', labelFontSize: 9 } })),
    },
    layout: { type: 'force', preventOverlap: true, linkDistance: 150, nodeSize: 56, gravity: 14 },
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
  }) as GraphViewportInstance
  graph = instance
  viewportSync = bindGraphViewportSync(host.value, () => graph, () => isFullscreen.value)
  instance.on('afterlayout', () => { viewportSync?.syncFit(false) })
  instance.on('node:click', (event) => {
    const id = (event as { target?: { id?: string } }).target?.id
    if (id) { selectedNodeId.value = Number(id); evidenceOpen.value = true }
  })
  await instance.render()
  viewportSync.syncFit(false)
}
async function load() {
  loading.value = true; error.value = ''
  try { map.value = await learningApi.masteryMap(courseId.value) }
  catch (cause) { map.value = null; destroyGraph(); error.value = apiError(cause, '个人掌握图谱暂时无法加载。') }
  finally {
    loading.value = false
    if (map.value && renderMode.value === '2d') {
      await nextTick()
      await draw()
    }
  }
}
const graphNodes = computed(() => map.value?.nodes.map(node => ({ id: String(node.id), label: node.name })) || [])
const graphEdges = computed(() => map.value?.edges.map(edge => ({ source: String(edge.source), target: String(edge.target), label: edge.relation })) || [])
function switchRenderMode(next: '2d' | '3d') { renderMode.value = next; if (next === '3d') destroyGraph(); else void draw() }
function onFullscreenChange(fullscreen: boolean) {
  isFullscreen.value = fullscreen
  if (renderMode.value === '2d') viewportSync?.syncFit(true)
}
function focusSelected() {
  if (selectedNodeId.value && graph?.focusElement) graph.focusElement(String(selectedNodeId.value), { duration: 350 })
  else graph?.fitView?.({ when: 'always' }, { duration: 350 })
}
async function loadCourses() {
  try { courses.value = (await api.get<Course[]>('/courses')).data } catch { courses.value = [] }
}
function practice(node: MasteryMapNode) { void router.push({ path: '/practice', query: { knowledgePointId: String(node.id) } }) }
function study(node: MasteryMapNode) { void router.push({ path: '/courses', query: { courseId: String(node.courseId), knowledgePointId: String(node.id) } }) }
onMounted(async () => { await loadCourses(); await load() })
onBeforeUnmount(destroyGraph)
</script>

<template>
  <div class="page-stack mastery-page">
    <SectionHeading eyebrow="个人学习状态" title="个性化掌握图谱" description="节点状态来自当前账户的答题记录与复习队列，先修边来自课程配置。">
      <button class="button button-outline" :disabled="loading" @click="load"><RotateCcw :size="15" />刷新</button>
    </SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" />{{ error }}<button class="text-button" @click="load">重试</button></div>
    <section class="mastery-controls"><label><Network :size="15" />课程<select v-model.number="courseId" @change="load"><option :value="undefined">全部课程</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><span v-if="map" class="mastery-summary">{{ map.courseName }} · {{ map.nodes.length }} 个知识点 · {{ map.edges.length }} 条先修关系</span></section>
    <div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在汇总个人掌握记录</strong></div>
    <template v-else-if="map">
      <VisualizationViewport title="个人掌握关系" description="颜色表示当前掌握状态；可切换 2D/3D 视图并从节点继续学习。" :mode="renderMode" :status="renderMode === '3d' ? 'Three.js 3D' : 'G6 2D'" :loading="loading" :error="error" :empty="!map.nodes.length" @retry="load" @reset="load" @focus="focusSelected" @mode-change="switchRenderMode" @fullscreen-change="onFullscreenChange">
        <ThreeGraphCanvas v-if="renderMode === '3d'" :nodes="graphNodes" :edges="graphEdges" :selected-id="selectedNodeId ? String(selectedNodeId) : undefined" @select="selectedNodeId = Number($event)" />
        <div v-else ref="host" class="mastery-canvas" aria-label="个人掌握关系图"></div>
        <template #footer><div class="mastery-legend"><strong class="mastery-legend-title">掌握程度</strong><span><i class="legend-dot legend-mastered"></i>已掌握</span><span><i class="legend-dot legend-in-progress"></i>学习中/下一步</span><span><i class="legend-dot legend-blocked"></i>先修阻塞</span></div></template>
        <template #overlay>
          <article v-if="selectedNode" class="fullscreen-node-card">
            <div class="fullscreen-node-head"><strong>{{ selectedNode.name }}</strong><span class="fullscreen-node-tag" :class="{ 'is-warning': selectedNode.status === 'IN_PROGRESS' || selectedNode.status === 'UP_NEXT', 'is-danger': selectedNode.status === 'BLOCKED' }">{{ statusLabel(selectedNode.status) }}</span></div>
            <p class="fullscreen-node-meta">掌握度 {{ Math.round(selectedNode.mastery) }}% · {{ selectedNode.correctCount }} / {{ selectedNode.attemptCount }} 次答对</p>
            <div class="fullscreen-node-actions"><button class="text-button" type="button" @click="study(selectedNode)">课程阅读<ArrowRight :size="13" /></button><button class="text-button" type="button" @click="practice(selectedNode)">专项练习<ArrowRight :size="13" /></button></div>
          </article>
        </template>
      </VisualizationViewport>
      <GraphEvidenceDrawer v-model="evidenceOpen" :node="selectedNode?.name || ''" title="掌握图谱证据" empty-text="掌握图谱只使用当前账户的 MySQL 学习记录和课程先修配置；本视图不伪造 NebulaGraph 证据。" />
      <section class="mastery-node-list"><article v-for="node in map.nodes" :key="node.id" class="mastery-node" :class="[statusClass(node.status), { selected: selectedNodeId === node.id }]" @click="selectedNodeId = node.id; evidenceOpen = true"><div class="mastery-node-main"><div class="mastery-node-top"><span class="eyebrow">{{ node.graphEntity }}</span><strong>{{ statusLabel(node.status) }}</strong></div><h2>{{ node.name }}</h2><div class="mastery-progress"><i :style="{ width: node.mastery + '%' }"></i></div><div class="mastery-meta"><span>掌握度 {{ Math.round(node.mastery) }}%</span><span>{{ node.correctCount }} / {{ node.attemptCount }} 次答对</span><span v-if="node.dueReviewCount">待复习 {{ node.dueReviewCount }}</span></div><LearningContextActions compact :context="{ courseId: node.courseId, knowledgePointId: node.id, entity: node.graphEntity }" /></div><div class="mastery-actions"><button class="text-button" :data-testid="`mastery-study-${node.id}`" @click.stop="study(node)">课程阅读<ArrowRight :size="13" /></button><button class="text-button" :data-testid="`mastery-practice-${node.id}`" @click.stop="practice(node)">专项练习<ArrowRight :size="13" /></button></div></article></section>
    </template>
  </div>
</template>

<style scoped>
.mastery-controls,.mastery-node-list{border-radius:var(--radius-md)}
.mastery-controls{display:flex;align-items:center;flex-wrap:wrap;gap:12px;padding:10px 14px;background:var(--surface-tint)}
.mastery-controls label,.mastery-legend span{display:flex;align-items:center;gap:8px;color:var(--text-muted)}
.mastery-controls select{min-height:var(--control-height-small)}
.mastery-summary{margin-left:auto;color:var(--text-muted);font-size:13px;font-variant-numeric:tabular-nums}
.mastery-canvas{width:100%;height:100%;min-height:390px}
.mastery-legend{display:flex;align-items:center;flex-wrap:wrap;gap:8px 16px;font-size:12px}
.mastery-legend-title{color:var(--text);font-size:12px;font-weight:650}
.legend-dot{width:8px;height:8px;border-radius:50%;background:var(--accent)}
.legend-mastered{background:var(--accent)}.legend-in-progress{background:var(--warning)}.legend-blocked{background:var(--danger)}
.mastery-node-list{background:var(--surface);border:1px solid var(--border-default);overflow:hidden}
.mastery-node{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:16px;align-items:center;padding:16px;border-top:1px solid var(--border-soft);font-variant-numeric:tabular-nums}
.mastery-node:first-child{border-top:0}
.mastery-node:hover{background:var(--surface-soft)}
.mastery-node.selected{background:var(--accent-soft);box-shadow:inset 3px 0 var(--accent)}
.mastery-node-top,.mastery-meta,.mastery-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px 12px}
.mastery-node-top{justify-content:space-between}
.mastery-node-top .eyebrow{margin:0}
.mastery-node-top strong{padding:3px 8px;color:var(--warning);background:var(--warning-soft);border-radius:var(--radius-pill);font-size:12px}
.mastery-mastered .mastery-node-top strong{color:var(--accent-strong);background:var(--accent-soft)}
.mastery-blocked .mastery-node-top strong{color:var(--danger);background:var(--danger-soft)}
.mastery-node h2{margin:4px 0 8px;font-size:16px}
.mastery-progress{height:6px;overflow:hidden;background:var(--surface-tint);border-radius:var(--radius-pill)}
.mastery-progress i{display:block;height:100%;background:var(--accent);border-radius:inherit}
.mastery-meta{margin-top:8px;color:var(--text-muted);font-size:12px}
@media(max-width:700px){.mastery-summary{width:100%;margin-left:0}.mastery-node{grid-template-columns:minmax(0,1fr)}.mastery-canvas{min-height:320px}}
</style>
