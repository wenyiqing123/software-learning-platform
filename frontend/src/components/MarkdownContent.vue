<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Copy } from '@lucide/vue'
import { parseInlineMarkdown, parseSimpleMarkdown } from '../domain/markdown'

const props = withDefaults(defineProps<{ content?: string }>(), { content: '' })
const blocks = computed(() => parseSimpleMarkdown(props.content))
const copyResult = ref<{ index: number; text: string; status: '已复制' | '复制失败' }>()

async function copyCode(index: number, text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copyResult.value = { index, text, status: '已复制' }
  } catch {
    copyResult.value = { index, text, status: '复制失败' }
  }
}
</script>

<template>
  <div class="markdown-content">
    <template v-for="(block, index) in blocks" :key="index">
      <h3 v-if="block.kind === 'heading' && block.level === 3">
        <template v-for="(span, sIdx) in parseInlineMarkdown(block.text)" :key="sIdx">
          <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
          <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
          <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
          <template v-else>{{ span.text }}</template>
        </template>
      </h3>

      <h4 v-else-if="block.kind === 'heading' && block.level === 4">
        <template v-for="(span, sIdx) in parseInlineMarkdown(block.text)" :key="sIdx">
          <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
          <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
          <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
          <template v-else>{{ span.text }}</template>
        </template>
      </h4>

      <p v-else-if="block.kind === 'paragraph'">
        <template v-for="(line, lineIndex) in block.lines" :key="lineIndex">
          <br v-if="lineIndex">
          <template v-for="(span, sIdx) in parseInlineMarkdown(line)" :key="sIdx">
            <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
            <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
            <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
            <template v-else>{{ span.text }}</template>
          </template>
        </template>
      </p>

      <ol v-else-if="block.kind === 'list' && block.ordered">
        <li v-for="(line, lineIndex) in block.lines" :key="lineIndex">
          <template v-for="(span, sIdx) in parseInlineMarkdown(line)" :key="sIdx">
            <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
            <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
            <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
            <template v-else>{{ span.text }}</template>
          </template>
        </li>
      </ol>

      <ul v-else-if="block.kind === 'list'">
        <li v-for="(line, lineIndex) in block.lines" :key="lineIndex">
          <template v-for="(span, sIdx) in parseInlineMarkdown(line)" :key="sIdx">
            <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
            <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
            <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
            <template v-else>{{ span.text }}</template>
          </template>
        </li>
      </ul>

      <blockquote v-else-if="block.kind === 'blockquote'" class="markdown-quote">
        <p>
          <template v-for="(line, lineIndex) in block.lines" :key="lineIndex">
            <br v-if="lineIndex">
            <template v-for="(span, sIdx) in parseInlineMarkdown(line)" :key="sIdx">
              <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
              <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
              <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
              <template v-else>{{ span.text }}</template>
            </template>
          </template>
        </p>
      </blockquote>

      <div v-else-if="block.kind === 'table'" class="markdown-table-wrap">
        <table class="markdown-table">
          <thead>
            <tr>
              <th v-for="(header, hIdx) in block.headers" :key="hIdx" scope="col">
                <template v-for="(span, sIdx) in parseInlineMarkdown(header)" :key="sIdx">
                  <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
                  <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
                  <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
                  <template v-else>{{ span.text }}</template>
                </template>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rIdx) in block.rows" :key="rIdx">
              <td v-for="(cell, cIdx) in row" :key="cIdx">
                <template v-for="(span, sIdx) in parseInlineMarkdown(cell)" :key="sIdx">
                  <strong v-if="span.kind === 'strong'">{{ span.text }}</strong>
                  <em v-else-if="span.kind === 'em'">{{ span.text }}</em>
                  <code v-else-if="span.kind === 'code'">{{ span.text }}</code>
                  <template v-else>{{ span.text }}</template>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <hr v-else-if="block.kind === 'hr'" class="markdown-hr">

      <div v-else-if="block.kind === 'code'" class="markdown-code">
        <div class="markdown-code-bar">
          <span>{{ block.language }}</span>
          <span v-if="copyResult?.index === index && copyResult.text === block.text" role="status">{{ copyResult.status }}</span>
          <button type="button" class="icon-button" aria-label="复制代码" title="复制代码" @click="copyCode(index, block.text)">
            <Check v-if="copyResult?.index === index && copyResult.text === block.text && copyResult.status === '已复制'" :size="15" />
            <Copy v-else :size="15" />
          </button>
        </div>
        <pre><code :class="'language-' + block.language">{{ block.text }}</code></pre>
      </div>
    </template>
  </div>
</template>

<style scoped>
.markdown-code{margin:14px 0;min-width:0;border:1px solid var(--border-default);border-radius:var(--radius-sm);background:var(--surface-soft)}
.markdown-code-bar{display:flex;align-items:center;gap:8px;padding:3px 8px 3px 14px;border-bottom:1px solid var(--border-soft);color:var(--text-muted);font-size:12px}
.markdown-code-bar>span:first-child{flex:1;min-width:0;overflow-wrap:anywhere;font-family:ui-monospace,monospace}
</style>
