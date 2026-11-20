<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Download, RefreshCw } from '@lucide/vue'
import { apiError, learningApi, type EvaluationSummary } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
const rows=ref<EvaluationSummary[]>([]),condition=ref(''),loading=ref(true),error=ref('')
async function load(){loading.value=true;error.value='';try{rows.value=await learningApi.evaluationSummary(condition.value||undefined)}catch(e){error.value=apiError(e,'评估汇总暂时无法读取')}finally{loading.value=false}}
async function download(){try{const blob=await learningApi.exportEvaluationSummary();const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download='learning-evaluation-summary.csv';link.click();URL.revokeObjectURL(url)}catch(e){error.value=apiError(e,'导出失败')}}
onMounted(()=>void load())
</script>
<template>
  <div class="page-stack"><SectionHeading eyebrow="管理后台 · 研究数据" title="匿名学习评估汇总" description="只展示按条件聚合的样本数、前后测变化、心理努力和用时，不展示参与者 ID 或反馈原文。"><button class="button button-outline" :disabled="loading" @click="load"><RefreshCw :size="15"/>刷新</button><button class="button button-dark" :disabled="loading" @click="download"><Download :size="15"/>导出 CSV</button></SectionHeading><div v-if="error" class="inline-notice" role="alert">{{ error }}</div><section class="content-section"><div class="evaluation-admin-filter"><label>条件筛选<select v-model="condition" @change="load"><option value="">全部条件</option><option value="METHODS">问答方法对照</option><option value="QWEN_MATRIX">Qwen基座与LoRA对照</option><option value="LEARNING_TASK">课程学习任务</option></select></label></div><div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在读取汇总</strong></div><div v-else-if="rows.length" class="evaluation-table"><div class="evaluation-row evaluation-head"><strong>条件</strong><strong>样本数</strong><strong>前测均值</strong><strong>后测均值</strong><strong>平均增益</strong><strong>心理努力</strong><strong>平均用时</strong><strong>反馈数</strong></div><div v-for="row in rows" :key="row.conditionKey" class="evaluation-row"><span>{{ row.conditionKey }}</span><span>{{ row.sampleCount }}</span><span>{{ row.meanPreScore ?? '—' }}</span><span>{{ row.meanPostScore ?? '—' }}</span><span>{{ row.meanGain ?? '—' }}</span><span>{{ row.meanEffort ?? '—' }}</span><span>{{ row.meanElapsedSeconds ?? '—' }} 秒</span><span>{{ row.feedbackCount }}</span></div></div><div v-else class="empty-state"><strong>暂无已完成评估</strong><span>完成评估会进入匿名聚合汇总。</span></div></section></div>
</template>
<style scoped>
.evaluation-admin-filter{display:flex;justify-content:flex-end;margin-bottom:15px}.evaluation-admin-filter label{display:grid;gap:6px;color:#59675d;font-size:11px}.evaluation-admin-filter select{min-width:180px;padding:8px;border:1px solid var(--line);border-radius:5px;background:#fff}.evaluation-table{overflow:auto}.evaluation-row{display:grid;grid-template-columns:1.2fr repeat(7,minmax(90px,1fr));gap:10px;min-width:760px;padding:13px 0;border-bottom:1px solid var(--line);color:#637168;font-size:11px}.evaluation-head{color:#435349;font-size:10px;font-weight:700}.evaluation-row span:first-child{color:#3e5145;font-weight:650}
</style>
