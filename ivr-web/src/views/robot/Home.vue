<template>
  <div class="page-container">
    <!-- 统计卡片：中性白底 + 单色强调 -->
    <section class="stats" v-loading="loading">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-head">
          <span class="stat-label">{{ s.label }}</span>
          <component :is="s.icon" :size="16" :stroke-width="1.8" class="stat-icon" />
        </div>
        <div class="stat-value">
          {{ s.value }}
          <span v-if="s.unit" class="stat-unit">{{ s.unit }}</span>
        </div>
        <div class="stat-trend" :class="s.trend?.dir">
          <component
            v-if="s.trend"
            :is="s.trend.dir === 'up' ? ArrowUpRight : ArrowDownRight"
            :size="12"
            :stroke-width="2"
          />
          <span>{{ s.trend?.text || '较昨日持平' }}</span>
        </div>
      </div>
    </section>

    <!-- 下方并列：说明 + 快捷入口 -->
    <section class="bottom-grid">
      <article class="panel">
        <header class="panel-head">
          <h2>今日通话轨迹</h2>
        </header>
        <div class="panel-body">
          <el-table :data="recentCalls" size="small" border>
            <el-table-column prop="time" label="时间" width="90" />
            <el-table-column prop="caller" label="来电号码" width="140" />
            <el-table-column prop="flowName" label="命中流程" />
            <el-table-column prop="result" label="结果" width="120" />
          </el-table>
        </div>
      </article>

      <article class="panel">
        <header class="panel-head">
          <h2>快捷入口</h2>
        </header>
        <div class="panel-body">
          <RouterLink
            v-for="q in quickLinks"
            :key="q.path"
            :to="q.path"
            class="quick-item"
          >
            <component :is="q.icon" :size="16" :stroke-width="1.8" />
            <span>{{ q.label }}</span>
            <ChevronRight :size="14" :stroke-width="1.8" class="quick-arrow" />
          </RouterLink>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  PhoneIncoming, Workflow, UserRound, Sparkles,
  ArrowUpRight, ArrowDownRight,
  ChevronRight, Plus, Mic, BookOpen
} from 'lucide-vue-next'
import { getDashboardOverview } from '@/api/dashboard'
import type { DashboardOverview, RecentCall } from '@/api/dashboard'

const loading = ref(false)
const overview = ref<DashboardOverview | null>(null)
const recentCalls = computed<RecentCall[]>(() => overview.value?.recentCalls || [])
const stats = computed(() => [
  {
    label: '今日呼入',
    value: overview.value?.todayCalls ?? '—',
    unit: '通',
    icon: PhoneIncoming,
    trend: { dir: 'up', text: '模拟数据 +12%' }
  },
  {
    label: '在线流程',
    value: overview.value?.onlineFlows ?? '—',
    unit: '条',
    icon: Workflow,
    trend: { dir: 'up', text: `${overview.value?.draftFlows ?? 0} 条草稿` }
  },
  {
    label: '活跃用户',
    value: overview.value?.activeUsers ?? '—',
    unit: '人',
    icon: UserRound,
    trend: null
  },
  {
    label: 'AI 解决率',
    value: overview.value?.aiResolutionRate ?? '—',
    unit: '%',
    icon: Sparkles,
    trend: { dir: 'down', text: `转人工 ${overview.value?.transferRate ?? 0}%` }
  }
])

const quickLinks = [
  { path: '/flow/list',    label: '新建流程',       icon: Plus },
  { path: '/robot/home',   label: '上传语音资源',   icon: Mic },
  { path: '/flow/list',    label: '管理知识库',     icon: BookOpen }
]

async function fetchOverview() {
  loading.value = true
  try {
    overview.value = await getDashboardOverview()
  } finally {
    loading.value = false
  }
}

onMounted(fetchOverview)
</script>

<style scoped lang="scss">
// ---------- Stat cards ----------
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}
.stat-card {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  transition: border-color var(--tr-fast);
  &:hover {
    border-color: var(--color-neutral-300);
  }
}
.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}
.stat-label {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  letter-spacing: 0.2px;
}
.stat-icon {
  color: var(--color-text-subtle);
}
.stat-value {
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
  line-height: var(--leading-tight);
  font-variant-numeric: tabular-nums;
}
.stat-unit {
  font-size: var(--text-sm);
  font-weight: var(--weight-regular);
  color: var(--color-text-muted);
  margin-left: var(--space-1);
}
.stat-trend {
  margin-top: var(--space-3);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
  &.up { color: var(--color-success); }
  &.down { color: var(--color-error); }
}

// ---------- Bottom grid ----------
.bottom-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-4);
  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.panel-head {
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  h2 {
    font-size: var(--text-sm);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
  }
}
.panel-body { padding: var(--space-4); }

.quick-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  margin: 0 calc(-1 * var(--space-2));
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  color: var(--color-text);
  transition: background var(--tr-fast);
  &:hover {
    background: var(--color-neutral-50);
    color: var(--color-primary);
    .quick-arrow { color: var(--color-primary); }
  }
  .quick-arrow {
    margin-left: auto;
    color: var(--color-text-subtle);
    transition: color var(--tr-fast);
  }
}
</style>
