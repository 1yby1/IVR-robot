<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>通话记录</h2>
          <p class="panel-sub">查看模拟呼叫与后续真实呼叫的执行轨迹</p>
        </div>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索通话 ID / 主叫 / 被叫 / 结果"
            class="search-input"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
          <el-select v-model="filters.flowId" clearable filterable placeholder="全部流程" class="flow-filter">
            <el-option
              v-for="flow in flowOptions"
              :key="flow.id"
              :label="`${flow.flowName}（${flow.flowCode}）`"
              :value="flow.id"
            />
          </el-select>
          <el-select v-model="filters.endReason" clearable placeholder="全部结果" class="reason-filter">
            <el-option label="进行中" value="running" />
            <el-option label="正常" value="normal" />
            <el-option label="转人工" value="transfer" />
            <el-option label="超时" value="timeout" />
            <el-option label="异常" value="error" />
          </el-select>
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="date-filter"
          />
          <el-button @click="fetchList">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="callUuid" label="通话 ID" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="code-chip">{{ row.callUuid }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="caller" label="主叫" width="130" />
          <el-table-column prop="callee" label="被叫" width="120" />
          <el-table-column label="命中流程" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="flow-cell">
                <span>{{ row.flowName }}</span>
                <code v-if="row.flowCode" class="flow-code">{{ row.flowCode }}</code>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="80">
            <template #default="{ row }">
              <span class="num">v{{ row.flowVersion || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }">
              <el-tag :type="reasonType(row.endReason)" effect="plain" size="small">
                {{ reasonText(row.endReason) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="90">
            <template #default="{ row }">
              <span class="num">{{ row.duration || 0 }}s</span>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" width="170" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openReplay(row)">回放</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="fetchList"
            @size-change="fetchList"
          />
        </div>
      </div>
    </section>

    <el-dialog v-model="replayDialog.visible" title="通话路径回放" width="980px">
      <div v-loading="replayDialog.loading" class="replay-dialog">
        <template v-if="replayDialog.data">
          <div class="event-head">
            <div>
              <code class="code-chip">{{ replayDialog.data.callUuid }}</code>
              <span>{{ replayDialog.data.caller }} → {{ replayDialog.data.callee }}</span>
            </div>
            <el-tag :type="reasonType(replayDialog.data.endReason)" effect="plain">
              {{ reasonText(replayDialog.data.endReason) }}
            </el-tag>
          </div>

          <div class="replay-meta">
            <span>{{ replayDialog.data.flowName }} · v{{ replayDialog.data.flowVersion || 0 }}</span>
            <span>开始 {{ replayDialog.data.startTime || '-' }}</span>
            <span>时长 {{ replayDialog.data.duration || 0 }}s</span>
            <span v-if="replayDialog.data.transferTo">转接 {{ replayDialog.data.transferTo }}</span>
          </div>

          <div class="replay-grid">
            <section class="replay-path">
              <div class="section-title">实际节点路径</div>
              <div v-if="replayDialog.data.path.length" class="path-list">
                <div
                  v-for="step in replayDialog.data.path"
                  :key="`${step.stepNo}-${step.nodeKey}`"
                  class="path-step"
                  :class="`is-${step.level}`"
                >
                  <span class="path-index">{{ step.stepNo }}</span>
                  <div class="path-body">
                    <strong>{{ step.nodeName || step.nodeKey }}</strong>
                    <span>{{ step.nodeType }} · {{ step.nodeKey }}</span>
                    <p>{{ step.summary }}</p>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无进入节点事件" :image-size="80" />
            </section>

            <section class="replay-events">
              <div class="section-title">事件时间线</div>
              <el-table
                :data="replayDialog.data.events"
                border
                stripe
                max-height="360"
                highlight-current-row
                @row-click="selectEvent"
              >
                <el-table-column prop="eventTime" label="时间" width="150" />
                <el-table-column label="级别" width="80">
                  <template #default="{ row }">
                    <el-tag :type="eventTagType(row.level)" effect="plain" size="small">
                      {{ eventLevelText(row.level) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="eventType" label="事件" width="90" />
                <el-table-column prop="nodeType" label="类型" width="90" />
                <el-table-column prop="nodeKey" label="节点" width="110" show-overflow-tooltip />
                <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
              </el-table>
            </section>
          </div>

          <div class="payload-panel">
            <div class="section-title">Payload 明细</div>
            <pre v-if="replayDialog.selectedEvent" class="payload-pre">{{ replayDialog.selectedEvent.payloadPretty || replayDialog.selectedEvent.payload }}</pre>
            <el-empty v-else description="点击上方事件查看 payload" :image-size="70" />
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getCallReplay, pageCallLogs } from '@/api/call'
import type { CallLogItem, CallReplayEvent, CallReplayResponse } from '@/api/call'
import { listPublishedFlows } from '@/api/flow'
import type { FlowOptionItem } from '@/api/flow'

const list = ref<CallLogItem[]>([])
const flowOptions = ref<FlowOptionItem[]>([])
const loading = ref(false)
const filters = reactive({
  keyword: '',
  flowId: '' as number | '',
  endReason: '',
  dateRange: [] as string[]
})
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

const replayDialog = reactive({
  visible: false,
  loading: false,
  call: null as CallLogItem | null,
  data: null as CallReplayResponse | null,
  selectedEvent: null as CallReplayEvent | null
})

async function fetchList() {
  loading.value = true
  try {
    const res = await pageCallLogs({
      current: pagination.value.current,
      size: pagination.value.size,
      keyword: filters.keyword,
      flowId: filters.flowId,
      endReason: filters.endReason,
      dateFrom: filters.dateRange?.[0],
      dateTo: filters.dateRange?.[1]
    })
    list.value = res.records
    pagination.value.total = res.total
  } finally {
    loading.value = false
  }
}

async function fetchFlowOptions() {
  flowOptions.value = await listPublishedFlows()
}

async function openReplay(row: CallLogItem) {
  replayDialog.call = row
  replayDialog.visible = true
  replayDialog.loading = true
  replayDialog.data = null
  replayDialog.selectedEvent = null
  try {
    replayDialog.data = await getCallReplay(row.callUuid)
    replayDialog.selectedEvent = replayDialog.data.events.find((item) => item.level === 'danger' || item.level === 'warning')
      || replayDialog.data.events[0]
      || null
  } finally {
    replayDialog.loading = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.flowId = ''
  filters.endReason = ''
  filters.dateRange = []
  pagination.value.current = 1
  fetchList()
}

function selectEvent(row: CallReplayEvent) {
  replayDialog.selectedEvent = row
}

function reasonText(reason: string) {
  const map: Record<string, string> = {
    running: '进行中',
    normal: '正常',
    transfer: '转人工',
    timeout: '超时',
    error: '异常'
  }
  return map[reason] || reason || '未知'
}

function reasonType(reason: string) {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    normal: 'success',
    transfer: 'warning',
    timeout: 'info',
    error: 'danger',
    running: 'primary'
  }
  return map[reason] || 'info'
}

function eventTagType(level: string) {
  if (level === 'danger') return 'danger'
  if (level === 'warning') return 'warning'
  if (level === 'success') return 'success'
  return 'info'
}

function eventLevelText(level: string) {
  if (level === 'danger') return '异常'
  if (level === 'warning') return '关注'
  if (level === 'success') return '成功'
  return '信息'
}

onMounted(async () => {
  await Promise.all([fetchList(), fetchFlowOptions()])
})
</script>

<style scoped lang="scss">
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
  .panel-sub {
    margin-top: 2px;
    font-size: var(--text-xs);
    color: var(--color-text-muted);
  }
}
.panel-body { padding: var(--space-4); }
.table-tools {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.search-input { max-width: 360px; }
.flow-filter { width: 260px; }
.reason-filter { width: 140px; }
.date-filter { width: 260px; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}
.code-chip,
.flow-code {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-neutral-100);
  color: var(--color-neutral-700);
}
.flow-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.num {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}
.event-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  > div {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    min-width: 0;
  }
}
.replay-dialog {
  min-height: 320px;
}
.replay-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  span {
    padding: 4px 8px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-neutral-50);
  }
}
.replay-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: var(--space-3);
}
.section-title {
  margin-bottom: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
.replay-path,
.replay-events,
.payload-panel {
  min-width: 0;
}
.path-list {
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}
.path-step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: var(--space-2);
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border);
  &:last-child {
    border-bottom: none;
  }
  &.is-danger .path-index {
    border-color: var(--color-error);
    color: var(--color-error);
  }
  &.is-warning .path-index {
    border-color: var(--color-warning);
    color: var(--color-warning);
  }
  &.is-success .path-index {
    border-color: var(--color-success);
    color: var(--color-success);
  }
}
.path-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}
.path-body {
  min-width: 0;
  strong {
    display: block;
    font-size: var(--text-sm);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
  }
  span {
    display: block;
    margin-top: 2px;
    font-size: var(--text-xs);
    color: var(--color-text-subtle);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  p {
    margin: var(--space-1) 0 0;
    font-size: var(--text-xs);
    color: var(--color-text-muted);
    line-height: 1.5;
  }
}
.payload-panel {
  margin-top: var(--space-3);
}
.payload-pre {
  max-height: 240px;
  overflow: auto;
  margin: 0;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  color: var(--color-text);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 900px) {
  .replay-grid {
    grid-template-columns: 1fr;
  }
}
</style>
