<script setup lang="ts">
import type { TutorHintEvidence } from '../api/types'

defineProps<{
  evidence: TutorHintEvidence[]
}>()

const emit = defineEmits<{
  'open-graph': [entity: string]
}>()
</script>

<template>
  <div class="hint-evidence-table-wrap">
    <table class="hint-evidence-table">
      <thead>
        <tr>
          <th scope="col">核心实体</th>
          <th scope="col">图谱关系</th>
          <th scope="col">关联结论 / 对象</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="ev in evidence"
          :key="ev.subject + ev.predicate + ev.object"
        >
          <td class="hint-table-subject">
            <button
              type="button"
              class="hint-entity-link"
              :title="`在知识图谱中查看「${ev.subject}」`"
              @click="emit('open-graph', ev.subject)"
            >
              {{ ev.subject }}
            </button>
          </td>
          <td class="hint-table-predicate">
            <span class="hint-relation-pill">{{ ev.predicate }}</span>
          </td>
          <td class="hint-table-object">{{ ev.object }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.hint-evidence-table-wrap {
  min-width: 0;
  overflow-x: auto;
}

.hint-evidence-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 13px;
  line-height: 1.55;
  overflow: hidden;
}

.hint-evidence-table th,
.hint-evidence-table td {
  padding: 9px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-soft);
  vertical-align: top;
}

.hint-evidence-table th {
  color: var(--text-muted);
  background: var(--surface-tint);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.hint-evidence-table tbody tr:last-child td {
  border-bottom: 0;
}

.hint-evidence-table tbody tr:hover td {
  background: var(--surface-soft);
}

.hint-table-subject {
  width: 24%;
  min-width: 96px;
}

.hint-table-predicate {
  width: 18%;
  min-width: 84px;
  white-space: nowrap;
}

.hint-table-object {
  color: var(--text);
  overflow-wrap: anywhere;
}

.hint-entity-link {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 7px;
  color: var(--accent-strong);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 5px;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--motion), border-color var(--motion);
}

.hint-entity-link:hover {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent) 22%, transparent);
}

.hint-relation-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-radius: var(--radius-pill);
  font-size: 11.5px;
  font-weight: 600;
}
</style>
