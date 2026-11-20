<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, BookOpenCheck, CalendarDays, CircleAlert, Clock3, RotateCcw, SkipForward } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { learningApi, type StudyPlan, type StudyPlanTask } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
const plan=ref<StudyPlan|null>(null)
const budget=ref(45)
const loading=ref(true)
const saving=ref(false)
const error=ref('')
const pending=computed(()=>plan.value?.tasks.filter(task=>task.state==='PENDING')??[])
const used=computed(()=>plan.value?.tasks.filter(task=>task.state==='COMPLETED').reduce((sum,task)=>sum+task.minutes,0)??0)
const scheduled=computed(()=>plan.value?.tasks.reduce((sum,task)=>sum+task.minutes,0)??0)
async function load(){loading.value=true;error.value='';try{plan.value=await learningApi.studyPlanToday();budget.value=plan.value.budgetMinutes}catch{error.value='暂时无法读取每日计划，请检查服务连接后重试。'}finally{loading.value=false}}
async function generate(){if(saving.value)return;saving.value=true;error.value='';try{plan.value=await learningApi.generateStudyPlan(budget.value)}catch(cause){const e=cause as {response?:{data?:{message?:string;detail?:string}}};error.value=e.response?.data?.message||e.response?.data?.detail||'计划生成失败，请检查服务连接。'}finally{saving.value=false}}
async function action(task:StudyPlanTask,kind:'COMPLETE'|'SKIP'){if(saving.value)return;saving.value=true;error.value='';try{await learningApi.updateStudyPlanTask(task.id,kind);plan.value=await learningApi.studyPlanToday()}catch(cause){const e=cause as {response?:{data?:{message?:string;detail?:string}}};error.value=e.response?.data?.message||e.response?.data?.detail||'任务状态更新失败。未完成相应学习行为前不能标记完成。'}finally{saving.value=false}}
function label(type:string){return type==='REVIEW'?'间隔复习':type==='READING'?'课程阅读':'针对练习'}
onMounted(()=>void load())
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="学习安排" title="每日学习计划" description="按固定规则从到期复习、未读小节和薄弱题目生成任务。"><button class="button button-outline" :disabled="loading||saving" @click="load"><RotateCcw :size="15"/>刷新</button></SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16"/><span>{{ error }}</span><button class="text-button" @click="load">重试</button></div>
    <section class="plan-controls"><label><Clock3 :size="16"/>每日时间预算<select v-model.number="budget" :disabled="saving"><option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="45">45 分钟</option><option :value="60">60 分钟</option><option :value="90">90 分钟</option><option :value="120">120 分钟</option></select></label><button class="button button-dark" :disabled="loading||saving" @click="generate"><CalendarDays :size="15"/>{{ saving?'保存中…':'生成计划' }}</button><div v-if="plan" class="plan-summary"><span>{{ plan.date }}</span><span>已完成 {{ used }} 分钟</span><span>计划 {{ scheduled }} / {{ plan.budgetMinutes }} 分钟</span></div></section>
    <FeedbackState v-if="loading" loading loading-title="正在生成今日计划" loading-text="正在读取当前账户的到期复习、课程阅读和练习任务。" />
    <section v-else-if="plan?.tasks.length" class="plan-task-list"><article v-for="(task,index) in plan.tasks" :key="task.id" class="plan-task" :class="'task-'+task.state.toLowerCase()"><div class="plan-index">{{ String(index+1).padStart(2,'0') }}</div><div class="plan-task-copy"><div class="plan-task-top"><span class="eyebrow">{{ label(task.type) }}</span><span class="plan-minutes"><Clock3 :size="13"/>{{ task.minutes }} 分钟</span></div><h2>{{ task.title }}</h2><p>{{ task.detail }}</p><small>{{ task.reason }}</small></div><div class="plan-task-actions"><RouterLink class="button button-outline button-small" :to="task.path">开始<ArrowRight :size="14"/></RouterLink><button v-if="task.state==='PENDING'" class="icon-button" title="跳过任务" :disabled="saving" @click="action(task,'SKIP')"><SkipForward :size="16"/></button><button v-if="task.state==='PENDING'" class="icon-button" title="完成任务（需先完成对应学习行为）" :disabled="saving" @click="action(task,'COMPLETE')"><BookOpenCheck :size="16"/></button><span v-else class="task-state-label">{{ task.state==='COMPLETED'?'已完成':'已跳过' }}</span></div></article></section>
    <section v-else-if="!loading" class="empty-state"><BookOpenCheck :size="24"/><strong>今日没有可安排任务</strong><span>练习错题或新增课程进度后可以重新生成计划。</span></section>
  </div>
</template>
