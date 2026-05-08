<template>
  <div class="page-container llm-logs-page">
    <section class="metric-grid">
      <div class="metric-card">
        <span class="metric-label">调用次数</span>
        <strong>{{ overview.totalCount || 0 }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">成功率</span>
        <strong>{{ overview.successRate || 0 }}%</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">平均耗时</span>
        <strong>{{ overview.avgLatencyMs || 0 }}ms</strong>
      </div>
      <div class="metric-card">
        <span class="metric-label">总 Token</span>
        <strong>{{ overview.totalTokens || 0 }}</strong>
      </div>
    </section>

    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>LLM 调用日志</h2>
          <p class="panel-sub">记录模型调用状态、token、耗时、输入输出摘要</p>
        </div>
        <el-button @click="reloadAll">刷新</el-button>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-select v-model="filters.scene" clearable placeholder="全部场景" style="width: 180px" @change="reload">
            <el-option v-for="item in scenes" :key="item" :label="sceneText(item)" :value="item" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索 trace / 模型 / 输入输出 / 错误"
            style="max-width: 360px"
            @keyup.enter="reload"
            @clear="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="createdAt" label="调用时间" width="170" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'danger'" effect="plain" size="small">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="场景" width="130">
            <template #default="{ row }">{{ sceneText(row.scene) }}</template>
          </el-table-column>
          <el-table-column prop="model" label="模型" width="160" show-overflow-tooltip />
          <el-table-column label="耗时" width="100">
            <template #default="{ row }">
              <span class="num">{{ row.latencyMs || 0 }}ms</span>
            </template>
          </el-table-column>
          <el-table-column label="Token" width="180">
            <template #default="{ row }">
              <span class="num strong">{{ row.totalTokens || 0 }}</span>
              <span class="muted"> / {{ row.promptTokens || 0 }}+{{ row.completionTokens || 0 }}</span>
              <el-tag v-if="row.tokenEstimated" class="estimate-tag" size="small" effect="plain">估</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="promptPreview" label="输入摘要" min-width="220" show-overflow-tooltip />
          <el-table-column prop="responsePreview" label="输出摘要" min-width="220" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailDialog.visible" title="调用详情" width="860px">
      <template v-if="detailDialog.row">
        <div class="detail-meta">
          <div><span>Trace</span><strong>{{ detailDialog.row.traceId }}</strong></div>
          <div><span>场景</span><strong>{{ sceneText(detailDialog.row.scene) }}</strong></div>
          <div><span>耗时</span><strong>{{ detailDialog.row.latencyMs || 0 }}ms</strong></div>
          <div><span>Token</span><strong>{{ detailDialog.row.totalTokens || 0 }}</strong></div>
        </div>
        <el-alert
          v-if="detailDialog.row.tokenEstimated"
          title="该记录的 token 为系统粗估，说明模型接口没有返回 usage。"
          type="info"
          show-icon
          :closable="false"
        />
        <div v-if="detailDialog.row.errorMessage" class="detail-block">
          <div class="block-title">错误信息</div>
          <pre>{{ detailDialog.row.errorMessage }}</pre>
        </div>
        <div class="detail-block">
          <div class="block-title">输入 Prompt</div>
          <pre>{{ detailDialog.row.promptPreview || '-' }}</pre>
        </div>
        <div class="detail-block">
          <div class="block-title">模型输出</div>
          <pre>{{ detailDialog.row.responsePreview || '-' }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getLlmLogOverview, listLlmLogScenes, pageLlmLogs } from '@/api/llm'
import type { LlmCallLog, LlmLogOverview } from '@/api/llm'

const overview = reactive<LlmLogOverview>({
  totalCount: 0,
  successCount: 0,
  failedCount: 0,
  successRate: 0,
  avgLatencyMs: 0,
  maxLatencyMs: 0,
  totalTokens: 0,
  avgTokens: 0
})
const scenes = ref<string[]>([])
const list = ref<LlmCallLog[]>([])
const loading = ref(false)
const filters = reactive({ scene: '', status: '', keyword: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })
const detailDialog = reactive({
  visible: false,
  row: null as LlmCallLog | null
})

async function fetchOverview() {
  Object.assign(overview, await getLlmLogOverview())
}

async function fetchScenes() {
  scenes.value = await listLlmLogScenes()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageLlmLogs({
      current: pagination.current,
      size: pagination.size,
      scene: filters.scene,
      status: filters.status,
      keyword: filters.keyword
    })
    list.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function reload() {
  pagination.current = 1
  await fetchList()
}

async function reloadAll() {
  await Promise.all([fetchOverview(), fetchScenes(), reload()])
}

function openDetail(row: LlmCallLog) {
  detailDialog.row = row
  detailDialog.visible = true
}

function sceneText(scene: string) {
  const map: Record<string, string> = {
    chat: '直接问答',
    chat_template: '模板问答',
    intent_detect: '意图识别'
  }
  return map[scene] || scene || '-'
}

onMounted(reloadAll)
</script>

<style scoped lang="scss">
.llm-logs-page {
  display: grid;
  gap: var(--space-4);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.metric-card {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg);

  strong {
    font-size: var(--text-xl);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
    font-variant-numeric: tabular-nums;
  }
}

.metric-label,
.panel-sub,
.muted {
  color: var(--color-text-muted);
}

.metric-label,
.panel-sub {
  font-size: var(--text-xs);
}

.panel {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: 1px solid var(--color-border);

  h2 {
    font-size: var(--text-sm);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
  }

  .panel-sub {
    margin-top: 2px;
  }
}

.panel-body {
  padding: var(--space-4);
}

.table-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.num {
  font-variant-numeric: tabular-nums;
}

.strong {
  font-weight: var(--weight-semibold);
  color: var(--color-text);
}

.estimate-tag {
  margin-left: var(--space-1);
}

.detail-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  margin-bottom: var(--space-3);

  div {
    display: grid;
    gap: 2px;
    padding: var(--space-2);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-neutral-50);
  }

  span {
    font-size: var(--text-xs);
    color: var(--color-text-muted);
  }

  strong {
    min-width: 0;
    overflow: hidden;
    color: var(--color-text);
    font-size: var(--text-xs);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.detail-block {
  margin-top: var(--space-3);
}

.block-title {
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

pre {
  max-height: 260px;
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

@media (max-width: 960px) {
  .metric-grid,
  .detail-meta {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
