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
            v-model="keyword"
            clearable
            placeholder="搜索通话 ID / 主叫 / 被叫 / 结果"
            class="search-input"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
          <el-button @click="fetchList">查询</el-button>
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
              <el-button size="small" text type="primary" @click="openEvents(row)">轨迹</el-button>
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

    <el-dialog v-model="eventDialog.visible" title="通话轨迹" width="760px">
      <div class="event-head">
        <code class="code-chip">{{ eventDialog.call?.callUuid }}</code>
        <span>{{ eventDialog.call?.caller }} → {{ eventDialog.call?.callee }}</span>
      </div>
      <el-table :data="eventDialog.events" v-loading="eventDialog.loading" border>
        <el-table-column prop="eventTime" label="时间" width="170" />
        <el-table-column prop="eventType" label="事件" width="90">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.eventType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nodeType" label="节点类型" width="100" />
        <el-table-column prop="nodeKey" label="节点" width="120" show-overflow-tooltip />
        <el-table-column label="内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ payloadText(row.payload) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageCallLogs, listCallEvents } from '@/api/call'
import type { CallEventItem, CallLogItem } from '@/api/call'

const list = ref<CallLogItem[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

const eventDialog = reactive({
  visible: false,
  loading: false,
  call: null as CallLogItem | null,
  events: [] as CallEventItem[]
})

async function fetchList() {
  loading.value = true
  try {
    const res = await pageCallLogs({
      current: pagination.value.current,
      size: pagination.value.size,
      keyword: keyword.value
    })
    list.value = res.records
    pagination.value.total = res.total
  } finally {
    loading.value = false
  }
}

async function openEvents(row: CallLogItem) {
  eventDialog.call = row
  eventDialog.visible = true
  eventDialog.loading = true
  try {
    eventDialog.events = await listCallEvents(row.callUuid)
  } finally {
    eventDialog.loading = false
  }
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

function payloadText(payload: string) {
  if (!payload) return ''
  try {
    const data = JSON.parse(payload)
    return data.text || data.input || data.result || data.message || payload
  } catch {
    return payload
  }
}

onMounted(fetchList)
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
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.search-input { max-width: 360px; }
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
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}
</style>
