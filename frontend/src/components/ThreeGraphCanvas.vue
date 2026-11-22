<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import type { GraphEdge, GraphNode } from '../api/client'

const props = defineProps<{ nodes: GraphNode[]; edges: GraphEdge[]; selectedId?: string }>()
const emit = defineEmits<{ select: [id: string] }>()
const host = ref<HTMLElement | null>(null)
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let graphGroup: THREE.Group | null = null
let nodeMeshes: THREE.Mesh[] = []
let resizeObserver: ResizeObserver | null = null
let frame = 0

function labelSprite(text: string) {
  const canvas = document.createElement('canvas')
  canvas.width = 512; canvas.height = 96
  const context = canvas.getContext('2d')
  if (!context) return null
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.font = 'bold 30px sans-serif'; context.fillStyle = '#20382d'; context.textAlign = 'center'; context.textBaseline = 'middle'
  context.fillText(text.slice(0, 22), canvas.width / 2, canvas.height / 2)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }))
  sprite.scale.set(86, 16, 1)
  return sprite
}

function rebuild() {
  if (!graphGroup) return
  const group = graphGroup
  group.clear(); nodeMeshes = []
  const positions = new Map<string, THREE.Vector3>()
  const radius = Math.max(105, props.nodes.length * 15)
  props.nodes.forEach((node, index) => {
    const phi = Math.acos(1 - (2 * (index + 0.5)) / Math.max(1, props.nodes.length))
    const theta = Math.PI * (1 + Math.sqrt(5)) * index
    const position = new THREE.Vector3(radius * Math.cos(theta) * Math.sin(phi), radius * Math.cos(phi), radius * Math.sin(theta) * Math.sin(phi))
    positions.set(node.id, position)
    const selected = node.id === props.selectedId
    const mesh = new THREE.Mesh(new THREE.SphereGeometry(selected ? 13 : 10, 20, 14), new THREE.MeshStandardMaterial({ color: selected ? '#0f665e' : '#8fc7aa', roughness: .75 }))
    mesh.position.copy(position); mesh.userData.id = node.id; nodeMeshes.push(mesh); group.add(mesh)
    const label = labelSprite(node.label)
    if (label) { label.position.copy(position).add(new THREE.Vector3(0, -18, 0)); group.add(label) }
  })
  for (const edge of props.edges) {
    const source = positions.get(edge.source); const target = positions.get(edge.target)
    if (!source || !target) continue
    const geometry = new THREE.BufferGeometry().setFromPoints([source, target])
    group.add(new THREE.Line(geometry, new THREE.LineBasicMaterial({ color: selectedEdge(edge) ? '#3b9a73' : '#9eb8aa', transparent: true, opacity: selectedEdge(edge) ? .95 : .58 })))
  }
}

function selectedEdge(edge: GraphEdge) { return props.selectedId === edge.source || props.selectedId === edge.target }
function resize() {
  if (!host.value || !renderer || !camera) return
  const width = Math.max(1, host.value.clientWidth); const height = Math.max(1, host.value.clientHeight)
  camera.aspect = width / height; camera.updateProjectionMatrix(); renderer.setSize(width, height, false)
}
function onPointerDown(event: PointerEvent) {
  if (!host.value || !camera || !renderer) return
  const rect = renderer.domElement.getBoundingClientRect()
  const pointer = new THREE.Vector2(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1)
  const raycaster = new THREE.Raycaster(); raycaster.setFromCamera(pointer, camera)
  const hit = raycaster.intersectObjects(nodeMeshes)[0]?.object.userData.id
  if (typeof hit === 'string') emit('select', hit)
}
function animate() { frame = requestAnimationFrame(animate); controls?.update(); if (renderer && scene && camera) renderer.render(scene, camera) }

onMounted(() => {
  if (!host.value) return
  scene = new THREE.Scene(); scene.background = new THREE.Color('#f7f8fa')
  camera = new THREE.PerspectiveCamera(45, 1, 1, 1600); camera.position.set(0, 0, 360)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true }); renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); host.value.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement); controls.enableDamping = true; controls.dampingFactor = .08; controls.minDistance = 140; controls.maxDistance = 700
  scene.add(new THREE.AmbientLight('#ffffff', 2.1)); const light = new THREE.DirectionalLight('#d7f2e2', 2.5); light.position.set(180, 220, 220); scene.add(light)
  graphGroup = new THREE.Group(); scene.add(graphGroup); rebuild(); resize(); resizeObserver = new ResizeObserver(resize); resizeObserver.observe(host.value); renderer.domElement.addEventListener('pointerdown', onPointerDown); animate()
})
watch(() => [props.nodes, props.edges, props.selectedId], rebuild, { deep: true })
onBeforeUnmount(() => { cancelAnimationFrame(frame); resizeObserver?.disconnect(); renderer?.domElement.removeEventListener('pointerdown', onPointerDown); controls?.dispose(); renderer?.dispose(); renderer?.domElement.remove(); graphGroup?.clear(); renderer = null; scene = null; camera = null; controls = null; graphGroup = null })
</script>

<template><div ref="host" class="three-graph-canvas" data-testid="three-graph-canvas" aria-label="Three.js 三维知识图谱"><span class="three-graph-hint">拖拽旋转 · 滚轮缩放 · 点击节点查看证据</span></div></template>

<style scoped>
.three-graph-canvas{position:relative;min-width:0;min-height:390px;overflow:hidden;cursor:grab}
.three-graph-canvas:active{cursor:grabbing}
.three-graph-canvas :deep(canvas){position:absolute;inset:0;display:block;width:100%;height:100%}
.three-graph-hint{position:absolute;right:12px;bottom:12px;z-index:1;max-width:calc(100% - 24px);padding:5px 8px;color:var(--text-muted);background:var(--surface);border-radius:var(--radius-sm);font-size:12px;pointer-events:none}
@media(max-width:700px){.three-graph-canvas{min-height:320px}}
</style>
