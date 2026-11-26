<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  courses: any[]
  courseDraft: any
  editCourseId: number | null
  saving: boolean
  prerequisiteQuality: any
  qualityLoading: boolean
  qualityError: string
  selectedCourse?: any
  points?: any[]
  pointDraft?: any
  editPointId?: number | null
}>(), {
  points: () => [],
  editPointId: null,
})

const emit = defineEmits<{
  newCourse: []
  editCourse: [course: any]
  toggleCourse: [course: any]
  saveCourse: []
  retryQuality: []
  newPoint: []
  editPoint: [point: any]
  togglePoint: [point: any]
  savePoint: []
}>()

const pointNameById = computed(() => new Map((props.points || []).map((p) => [p.id, p.name])))

const topologicalLayers = computed(() => {
  const list = props.points || []
  if (!list.length) return []
  const byId = new Map(list.map((p) => [p.id, p]))
  const depthMemo = new Map<number, number>()
  const visiting = new Set<number>()

  function resolveDepth(id: number): number {
    if (depthMemo.has(id)) return depthMemo.get(id)!
    if (visiting.has(id)) return 0
    visiting.add(id)
    const node = byId.get(id)
    const prereqs = Array.isArray(node?.prerequisiteIds) ? node.prerequisiteIds : []
    let maxParent = -1
    for (const pid of prereqs) {
      if (byId.has(pid) && pid !== id) {
        maxParent = Math.max(maxParent, resolveDepth(pid))
      }
    }
    visiting.delete(id)
    const d = maxParent + 1
    depthMemo.set(id, d)
    return d
  }

  for (const p of list) resolveDepth(p.id)
  const grouped = new Map<number, any[]>()
  for (const p of list) {
    const d = depthMemo.get(p.id) ?? 0
    if (!grouped.has(d)) grouped.set(d, [])
    grouped.get(d)!.push(p)
  }
  return Array.from(grouped.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([level, nodes]) => ({ level: level + 1, nodes }))
})
</script>

<template>
  <section class="admin-course-panel"><div class="panel-heading"><div><p class="eyebrow">01 / 课程</p><h2>课程信息</h2></div><button class="button button-outline" :disabled="saving" @click="emit('newCourse')">新增课程</button></div>
    <div class="admin-columns"><div class="course-list"><div v-if="!courses.length" class="admin-empty">暂无课程，请添加第一门课程。</div><article v-for="course in courses" :key="course.id" class="course-row"><div><strong>{{ course.name }}</strong><small>{{ course.code }} · {{ course.term }}</small><span class="status-label" :class="{ disabled: !course.enabled }">{{ course.enabled ? '已启用' : '已停用' }}</span></div><div class="row-actions"><button class="text-button" :disabled="saving" @click="emit('editCourse', course)">编辑</button><button class="text-button" :disabled="saving" @click="emit('toggleCourse', course)">{{ course.enabled ? '停用' : '启用' }}</button></div></article></div>
      <form class="admin-form" @submit.prevent="emit('saveCourse')"><h3>{{ editCourseId ? '编辑课程' : '新增课程' }}</h3><label>课程代码<input v-model.trim="courseDraft.code" aria-label="课程代码" required maxlength="40" placeholder="例如 SE-2026-FALL" :disabled="saving" /></label><div class="form-pair"><label>课程名称<input v-model.trim="courseDraft.name" aria-label="课程名称" required maxlength="120" :disabled="saving" /></label><label>学期<input v-model.trim="courseDraft.term" aria-label="学期" required maxlength="40" placeholder="2026 秋季" :disabled="saving" /></label></div><label>课程简介<textarea v-model.trim="courseDraft.description" aria-label="课程简介" required maxlength="500" rows="3" :disabled="saving"></textarea></label><p class="form-hint">使用现有课程图空间 1。新课程的知识点映射与阅读内容需由教学数据配置后提供。</p><button class="button button-dark" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存课程' }}</button></form></div>
  </section>
  <section class="prerequisite-quality-panel"><div class="panel-heading"><div><p class="eyebrow">01A / 关系质量</p><h2>先修关系质量检查</h2></div><span v-if="prerequisiteQuality" class="status-label" :class="{ disabled: !prerequisiteQuality.valid || prerequisiteQuality.warningCount > 0 }">{{ !prerequisiteQuality.valid ? '需要检查' : prerequisiteQuality.warningCount ? '有提醒' : '关系完整' }}</span></div>
    <div v-if="qualityLoading" class="admin-empty" role="status">正在检查当前课程的先修关系…</div><div v-else-if="qualityError" class="admin-message error" role="alert">{{ qualityError }} <button class="text-button" @click="emit('retryQuality')">重试</button></div><template v-else-if="prerequisiteQuality"><p class="form-hint">已检查 {{ prerequisiteQuality.pointCount }} 个知识点，发现 {{ prerequisiteQuality.issues.length }} 项关系问题，{{ prerequisiteQuality.validEdgeCount }} 条有效先修关系。</p><div v-if="!prerequisiteQuality.issues.length" class="quality-ok"><span>当前课程的先修关系没有发现问题。</span></div><div v-else class="quality-issues"><article v-for="issue in prerequisiteQuality.issues" :key="issue.code + '-' + issue.pointId + '-' + issue.relatedPointId" class="quality-issue" :class="issue.severity.toLowerCase()"><div class="question-meta"><strong>{{ issue.code }}</strong><span>{{ issue.severity === 'ERROR' ? '错误' : '提醒' }}</span></div><p>{{ issue.message }}</p><small>{{ issue.pointName || '未知知识点' }}<span v-if="issue.relatedPointName"> ↔ {{ issue.relatedPointName }}</span></small></article></div></template><div v-else class="admin-empty">选择课程后显示关系检查结果。</div>
    <div v-if="topologicalLayers.length" class="dag-preview" data-testid="prerequisite-dag-preview">
      <h3>先修拓扑层级预览</h3>
      <div class="dag-layers">
        <div v-for="layer in topologicalLayers" :key="layer.level" class="dag-layer">
          <span class="dag-layer-label">第 {{ layer.level }} 阶</span>
          <div class="dag-layer-nodes">
            <span v-for="pt in layer.nodes" :key="pt.id" class="dag-node-pill" :class="{ disabled: !pt.enabled }">
              <strong>{{ pt.name }}</strong>
              <small v-if="pt.prerequisiteIds?.length">
                前置：{{ pt.prerequisiteIds.map((id: number) => pointNameById.get(id) || `#${id}`).join('、') }}
              </small>
            </span>
          </div>
        </div>
      </div>
    </div>
    <details v-if="pointDraft" class="point-admin-details">
      <summary>知识点与先修关系配置（共 {{ points.length }} 项）</summary>
      <div class="point-admin-grid">
        <div class="point-admin-list">
          <div v-for="pt in points" :key="pt.id" class="point-admin-row">
            <div>
              <strong>{{ pt.name }}</strong>
              <small>实体：{{ pt.graphEntity }} · 排序 {{ pt.sortOrder ?? 0 }}</small>
            </div>
            <div class="row-actions">
              <button type="button" class="text-button" :disabled="saving" @click="emit('editPoint', pt)">编辑</button>
              <button type="button" class="text-button" :disabled="saving" @click="emit('togglePoint', pt)">{{ pt.enabled ? '停用' : '启用' }}</button>
            </div>
          </div>
        </div>
        <form class="admin-form" @submit.prevent="emit('savePoint')">
          <div class="panel-heading"><h3>{{ editPointId ? '编辑知识点' : '新增知识点' }}</h3><button type="button" class="text-button" @click="emit('newPoint')">清空 / 新增</button></div>
          <div class="form-pair">
            <label>知识点名称<input v-model.trim="pointDraft.name" aria-label="知识点名称" required maxlength="160" :disabled="saving" /></label>
            <label>图谱实体名<input v-model.trim="pointDraft.graphEntity" aria-label="图谱实体名" required maxlength="200" :disabled="saving" /></label>
          </div>
          <div class="form-pair">
            <label>排序权重<input v-model.number="pointDraft.sortOrder" type="number" min="0" max="9999" aria-label="知识点排序" :disabled="saving" /></label>
            <label>简介（可选）<input v-model.trim="pointDraft.description" aria-label="知识点简介" maxlength="800" :disabled="saving" /></label>
          </div>
          <div v-if="points.length" class="prereq-checklist">
            <span>先修知识点：</span>
            <label v-for="cand in points.filter((p: any) => p.id !== editPointId)" :key="cand.id" class="prereq-check-item">
              <input v-model="pointDraft.prerequisiteIds" type="checkbox" :value="cand.id" :disabled="saving" />
              <span>{{ cand.name }}</span>
            </label>
          </div>
          <button type="submit" class="button button-dark" :disabled="saving || !selectedCourse?.enabled">{{ saving ? '正在保存…' : '保存知识点' }}</button>
        </form>
      </div>
    </details>
  </section>
</template>

<style scoped>
.admin-course-panel{min-width:0;padding:0;background:transparent;border:0;box-shadow:none}.prerequisite-quality-panel{margin-bottom:16px}.admin-course-panel .admin-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px;align-items:start}.admin-course-panel .course-list{padding:20px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}
.dag-preview{margin-top:14px;padding-top:14px;border-top:1px solid var(--border-soft);display:grid;gap:10px}
.dag-preview h3{margin:0;font-size:14px;color:var(--text)}
.dag-layers{display:grid;gap:8px}
.dag-layer{display:grid;grid-template-columns:72px minmax(0,1fr);gap:10px;align-items:start;padding:8px 10px;background:var(--surface-soft);border-radius:var(--radius-sm)}
.dag-layer-label{font-size:12px;font-weight:650;color:var(--accent-strong)}
.dag-layer-nodes{display:flex;flex-wrap:wrap;gap:6px}
.dag-node-pill{display:inline-flex;flex-direction:column;gap:2px;padding:4px 10px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-xs);font-size:12px}
.dag-node-pill.disabled{opacity:.6}
.dag-node-pill small{color:var(--text-muted);font-size:11.5px}
.point-admin-details{margin-top:14px;padding-top:12px;border-top:1px solid var(--border-soft)}
.point-admin-details summary{cursor:pointer;font-size:13px;font-weight:600;color:var(--accent-strong)}
.point-admin-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-top:12px;align-items:start}
.point-admin-list{display:grid;gap:6px;max-height:320px;overflow-y:auto}
.point-admin-row{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 10px;background:var(--surface-soft);border-radius:var(--radius-xs);font-size:12.5px}
.point-admin-row small{display:block;color:var(--text-muted);font-size:12px}
.prereq-checklist{display:flex;flex-wrap:wrap;gap:8px;align-items:center;font-size:12px;color:var(--text-muted)}
.prereq-check-item{display:inline-flex;align-items:center;gap:4px}
@media(max-width:900px){.admin-course-panel .admin-columns,.point-admin-grid{grid-template-columns:1fr}.dag-layer{grid-template-columns:1fr}}
</style>
