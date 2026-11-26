<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { BellRing, Pause, Play, RotateCcw, Timer } from '@lucide/vue'

export interface FocusablePlanTask {
  id: number
  title: string
  state: string
  minutes?: number
}

const props = withDefaults(
  defineProps<{
    tasks?: FocusablePlanTask[]
  }>(),
  {
    tasks: () => [],
  }
)

const PRESETS = [
  { key: 'focus25', label: '25 分钟专注', seconds: 25 * 60 },
  { key: 'sprint15', label: '15 分钟短冲', seconds: 15 * 60 },
  { key: 'break5', label: '5 分钟休息', seconds: 5 * 60 },
] as const

const pendingTasks = computed(() => props.tasks.filter((t) => t.state === 'PENDING'))
const selectedPresetKey = ref<'focus25' | 'sprint15' | 'break5'>('focus25')
const selectedTaskId = ref<number | undefined>(pendingTasks.value[0]?.id)
const remainingSeconds = ref(25 * 60)
const running = ref(false)
const finishedNotice = ref('')
let timerHandle: ReturnType<typeof setInterval> | null = null

watch(pendingTasks, (next) => {
  if (!next.length) {
    selectedTaskId.value = undefined
  } else if (!next.some((t) => t.id === selectedTaskId.value)) {
    selectedTaskId.value = next[0].id
  }
})

const totalDurationSeconds = computed(() => {
  const found = PRESETS.find((p) => p.key === selectedPresetKey.value)
  return found ? found.seconds : 25 * 60
})

const formattedClock = computed(() => {
  const mins = Math.floor(remainingSeconds.value / 60)
  const secs = remainingSeconds.value % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
})

const progressPct = computed(() => {
  const total = totalDurationSeconds.value || 1
  return Math.min(100, Math.max(0, Math.round(((total - remainingSeconds.value) / total) * 100)))
})

const focusedTaskTitle = computed(() =>
  pendingTasks.value.find((t) => t.id === selectedTaskId.value)?.title || '',
)

function stopTicking() {
  if (timerHandle !== null) {
    clearInterval(timerHandle)
    timerHandle = null
  }
  running.value = false
}

function selectPreset(key: 'focus25' | 'sprint15' | 'break5') {
  stopTicking()
  selectedPresetKey.value = key
  finishedNotice.value = ''
  remainingSeconds.value = totalDurationSeconds.value
}

function toggleRun() {
  if (running.value) {
    stopTicking()
    return
  }
  if (remainingSeconds.value <= 0) {
    remainingSeconds.value = totalDurationSeconds.value
  }
  finishedNotice.value = ''
  running.value = true
  timerHandle = setInterval(() => {
    if (remainingSeconds.value <= 1) {
      remainingSeconds.value = 0
      stopTicking()
      finishedNotice.value = selectedPresetKey.value === 'break5'
        ? '休息时间结束，可以回到今日学习任务继续保持专注。'
        : '专注计时已结束！请在对应阅读、答题或复习界面完成真实学习操作后，再手动标记任务完成。'
    } else {
      remainingSeconds.value -= 1
    }
  }, 1000)
}

function resetTimer() {
  stopTicking()
  finishedNotice.value = ''
  remainingSeconds.value = totalDurationSeconds.value
}

onBeforeUnmount(stopTicking)
</script>

<template>
  <section class="pomodoro-card no-print" data-testid="pomodoro-timer" aria-label="学习专注番茄钟">
    <div class="pomodoro-head">
      <div class="pomodoro-title-group">
        <span class="pomodoro-icon"><Timer :size="17" /></span>
        <div>
          <p class="eyebrow">FOCUS TIMER</p>
          <h3>番茄专注计时</h3>
        </div>
      </div>

      <div class="pomodoro-presets" role="group" aria-label="专注时长预设">
        <button
          v-for="preset in PRESETS"
          :key="preset.key"
          type="button"
          class="pomodoro-preset-btn"
          :class="{ active: selectedPresetKey === preset.key }"
          :data-testid="`pomodoro-preset-${preset.key}`"
          @click="selectPreset(preset.key)"
        >
          {{ preset.label }}
        </button>
      </div>
    </div>

    <div class="pomodoro-body">
      <div class="pomodoro-clock-wrap">
        <strong class="pomodoro-clock" data-testid="pomodoro-clock">{{ formattedClock }}</strong>
        <div class="pomodoro-bar">
          <i :style="{ width: progressPct + '%' }"></i>
        </div>
      </div>

      <div class="pomodoro-controls">
        <label v-if="pendingTasks.length" class="pomodoro-task-picker">
          <span>关联任务</span>
          <select v-model="selectedTaskId" aria-label="专注关联任务">
            <option v-for="t in pendingTasks" :key="t.id" :value="t.id">{{ t.title }}</option>
          </select>
        </label>
        <div class="pomodoro-actions">
          <button
            type="button"
            class="button button-dark button-small"
            data-testid="pomodoro-toggle"
            @click="toggleRun"
          >
            <Pause v-if="running" :size="14" /><Play v-else :size="14" />
            <span>{{ running ? '暂停' : remainingSeconds === totalDurationSeconds ? '开始专注' : '继续计时' }}</span>
          </button>
          <button
            type="button"
            class="button button-outline button-small"
            data-testid="pomodoro-reset"
            @click="resetTimer"
          >
            <RotateCcw :size="14" />重置
          </button>
        </div>
      </div>
    </div>

    <p v-if="focusedTaskTitle && !finishedNotice" class="pomodoro-subcopy">
      正在围绕「{{ focusedTaskTitle }}」保持专注 · 计时结束不会跳过真实学习验收校验。
    </p>
    <p v-if="finishedNotice" class="pomodoro-finish-alert" role="status" data-testid="pomodoro-finish-alert">
      <BellRing :size="14" aria-hidden="true" />
      <span>{{ finishedNotice }}</span>
    </p>
  </section>
</template>

<style scoped>
.pomodoro-card {
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}
.pomodoro-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 14px;
}
.pomodoro-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pomodoro-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--accent-strong);
}
.pomodoro-title-group h3,
.pomodoro-title-group p {
  margin: 0;
}
.pomodoro-title-group h3 {
  font-size: 15px;
  color: var(--text);
}
.pomodoro-presets {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 3px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}
.pomodoro-preset-btn {
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.pomodoro-preset-btn.active {
  color: var(--accent-strong);
  background: var(--surface);
  box-shadow: var(--shadow-xs);
}
.pomodoro-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 18px;
}
.pomodoro-clock-wrap {
  display: grid;
  gap: 6px;
  min-width: 140px;
}
.pomodoro-clock {
  font-size: 28px;
  line-height: 1.1;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--text);
}
.pomodoro-bar {
  height: 5px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--surface-tint);
}
.pomodoro-bar i {
  display: block;
  height: 100%;
  background: var(--accent);
  border-radius: inherit;
  transition: width 250ms linear;
}
.pomodoro-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.pomodoro-task-picker {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-muted);
}
.pomodoro-task-picker select {
  min-height: var(--control-height-small);
  max-width: 240px;
}
.pomodoro-actions {
  display: flex;
  gap: 8px;
}
.pomodoro-subcopy {
  margin: 0;
  color: var(--text-subtle);
  font-size: 12px;
}
.pomodoro-finish-alert {
  margin: 0;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 12.5px;
  font-weight: 600;
}
</style>
