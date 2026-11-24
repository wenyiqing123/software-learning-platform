<script setup lang="ts">
interface ExperimentResult {
  mode?: string
  resultKey?: string
  answer?: string
  error?: string
  latencyMs?: number
  triples?: { subject: string; predicate: string; object: string; score?: number }[]
  prompt?: string
  grounding?: { selectedIds: string[]; candidates: unknown[]; modelCallCount: number }
  provenance?: Record<string, unknown>
}

interface Scores {
  rougeL?: number
  bleu4?: number
  bleuOrder?: number
  entityRecall?: number
}

defineProps<{
  results: ExperimentResult[]
  ratings: Record<string, number>
  metrics?: Record<string, Scores>
  modeNames: Record<string, string>
  score: (value?: number) => string
  hideRating?: boolean
}>()

const emit = defineEmits<{
  rate: [key: string, value: number]
}>()

function isRankedMode(mode?: string, resultKey?: string): boolean {
  const m = (mode || '').toUpperCase()
  const r = (resultKey || '').toUpperCase()
  return m.includes('RANKED') || r.includes('RANKED')
}
</script>

<template>
  <div class="experiment-result-grid" role="region" aria-label="实验条件结果对比网格">
    <article
      v-for="result in results"
      :key="result.resultKey || result.mode"
      class="compare-result-card"
      :class="{ 'result-failed': result.error }"
    >
      <header class="compare-result-title">
        <strong>{{ modeNames[result.resultKey || result.mode || ''] || result.resultKey || result.mode }}</strong>
        <div>
          <span class="status-tag" :class="result.error ? 'failed' : 'completed'">
            {{ result.error ? '失败' : '成功' }}
          </span>
          <small>{{ result.latencyMs ?? 0 }} ms</small>
        </div>
      </header>

      <p v-if="result.error" class="compare-error">
        {{ result.error }}
        <span class="error-hint">条件未完成，不产生评估指标</span>
      </p>
      <p v-else class="compare-answer">{{ result.answer }}</p>

      <dl v-if="!result.error" class="experiment-scores">
        <div><dt>ROUGE-L</dt><dd>{{ score(metrics?.[result.resultKey || result.mode || '']?.rougeL) }}</dd></div>
        <div><dt>BLEU ≤ 4</dt><dd>{{ score(metrics?.[result.resultKey || result.mode || '']?.bleu4) }}</dd></div>
        <div><dt>有效阶数</dt><dd>{{ metrics?.[result.resultKey || result.mode || '']?.bleuOrder ?? '—' }}</dd></div>
        <div><dt>实体召回率</dt><dd>{{ score(metrics?.[result.resultKey || result.mode || '']?.entityRecall) }}</dd></div>
      </dl>

      <div class="compare-result-details">
        <label v-if="!result.error && !hideRating" class="rating-select">
          人工评分
          <select
            :value="ratings[result.resultKey || result.mode || ''] || ''"
            @change="emit('rate', result.resultKey || result.mode || '', Number(($event.target as HTMLSelectElement).value))"
          >
            <option value="">选择 1–5 分</option>
            <option v-for="n in 5" :key="n" :value="n">{{ n }} 分</option>
          </select>
        </label>
        <details v-if="result.triples?.length">
          <summary>{{ result.triples.length }} 条知识证据 · 查看返回顺序</summary>
          <ol>
            <li v-for="triple in result.triples" :key="triple.subject + triple.predicate + triple.object">
              {{ triple.subject }} — {{ triple.predicate }} — {{ triple.object }}
              <small v-if="triple.score !== undefined">（{{ Number(triple.score).toFixed(3) }}）</small>
            </li>
          </ol>
        </details>
        <details v-if="result.prompt && result.mode !== 'RANKED' && result.resultKey !== 'RANKED'">
          <summary>查看提示词（RANKED 不展示后台约束提示）</summary>
          <pre>{{ result.prompt }}</pre>
        </details>
        <details v-if="result.grounding">
          <summary>查看证据筛选与校验记录</summary>
          <p>已选择 {{ result.grounding.selectedIds.length }} / {{ result.grounding.candidates.length }} 条证据；{{ result.grounding.modelCallCount }} 次模型调用</p>
          <pre>{{ JSON.stringify(result.grounding, null, 2) }}</pre>
        </details>
        <details v-if="result.provenance">
          <summary>查看运行配置与证据来源</summary>
          <pre>{{ JSON.stringify({ model: result.provenance.model, temperature: result.provenance.temperature, maxTokens: result.provenance.maxTokens, topK: result.provenance.topK, systemPromptVersion: result.provenance.systemPromptVersion }, null, 2) }}</pre>
        </details>
      </div>
    </article>
  </div>
</template>

<style scoped>
.experiment-result-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
  font-variant-numeric: tabular-nums;
  max-height: 800px;
  overflow-y: auto;
  padding-right: 4px;
}

.compare-result-card {
  display: grid;
  grid-row: span 4;
  grid-template-rows: subgrid;
  min-width: 0;
  padding: 16px;
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-xs);
}

.result-failed {
  background: var(--danger-soft);
  border-color: var(--danger-border);
}

.compare-result-title {
  display: grid;
  align-content: start;
  gap: 6px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-default);
}

.compare-result-title strong {
  font-size: 15px;
  overflow-wrap: anywhere;
  color: var(--text);
}

.compare-result-title > div,
.rating-select {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.compare-result-title small,
.rating-select,
.experiment-scores dt,
.compare-result-details {
  color: var(--text-muted);
  font-size: 12px;
}

.compare-answer,
.compare-error {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.compare-answer {
  color: var(--text);
}

.compare-error {
  color: var(--danger);
}

.error-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.experiment-scores {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 6px 10px;
  margin: 0;
  padding-top: 8px;
  border-top: 1px solid var(--border-default);
}

.experiment-scores dd {
  margin: 0;
  color: var(--text);
  font-size: 13px;
  font-weight: 650;
}

.compare-result-details {
  grid-row: span 1;
  min-width: 0;
}

.result-failed .compare-result-details {
  grid-row: span 2;
}

.rating-select {
  padding: 6px 0;
}

.rating-select select {
  min-height: var(--control-height-small);
}

.compare-result-details details {
  padding-block: 6px;
  border-top: 1px solid var(--border-soft);
}

.compare-result-details summary {
  color: var(--accent-strong);
  cursor: pointer;
  font-size: 12px;
}

.compare-result-details ol {
  padding-left: 18px;
  margin: 6px 0;
  font-size: 12px;
}

.compare-result-details pre {
  max-height: 240px;
  margin: 8px 0 0;
  padding: 10px;
  background: var(--surface-tint);
  border-radius: var(--radius-sm);
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 11px;
}

@media (max-width: 900px) {
  .experiment-result-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .experiment-result-grid {
    grid-template-columns: 1fr;
  }
}
</style>
