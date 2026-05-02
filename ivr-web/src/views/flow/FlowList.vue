<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>流程列表</h2>
          <p class="panel-sub">管理所有 IVR 呼叫流程</p>
        </div>
        <el-button v-if="userStore.hasPerm('flow:add')" type="primary" @click="onAdd">
          <Plus :size="14" :stroke-width="2" style="margin-right: 4px" />
          新建流程
        </el-button>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索流程编码 / 名称 / 描述"
            style="max-width: 320px"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
          <el-button @click="fetchList">查询</el-button>
        </div>
        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="flowCode" label="编码" width="180">
            <template #default="{ row }">
              <code class="code-chip">{{ row.flowCode }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="flowName" label="名称" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" effect="plain" size="small">已发布</el-tag>
              <el-tag v-else-if="row.status === 0" type="info" effect="plain" size="small">草稿</el-tag>
              <el-tag v-else type="warning" effect="plain" size="small">已下线</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentVersion" label="版本" width="150">
            <template #default="{ row }">
              <div class="version-inline">
                <span class="num">v{{ row.currentVersion }}</span>
                <el-tag v-if="row.hasDraftDiff" type="warning" effect="plain" size="small">未发布修改</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column v-if="hasFlowActions" label="操作" width="390" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text type="success" @click="onDebug(row)">调试</el-button>
                <el-button size="small" text type="primary" @click="onHealth(row)">健康</el-button>
                <el-button v-if="userStore.hasPerm('flow:edit')" size="small" text @click="onEdit(row)">编辑</el-button>
                <el-button size="small" text @click="onVersions(row)">版本</el-button>
                <el-button v-if="userStore.hasPerm('flow:publish')" size="small" text type="primary" @click="onPublish(row)">发布</el-button>
                <el-button v-if="row.status === 1 && userStore.hasPerm('flow:publish')" size="small" text type="warning" @click="onOffline(row)">下线</el-button>
                <el-button v-if="userStore.hasPerm('flow:delete')" size="small" text type="danger" @click="onDelete(row)">删除</el-button>
              </div>
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

    <el-dialog v-model="healthDialog.visible" title="流程健康检查" width="920px">
      <div v-loading="healthDialog.loading" class="health-dialog">
        <template v-if="healthDialog.response">
          <div class="health-head">
            <div class="health-score" :class="`is-${healthDialog.response.grade.toLowerCase()}`">
              <strong>{{ healthDialog.response.score }}</strong>
              <span>评级 {{ healthDialog.response.grade }}</span>
            </div>
            <div class="health-copy">
              <div class="debug-title">{{ healthDialog.response.flowName }}</div>
              <div class="debug-meta">{{ healthDialog.response.summary }}</div>
            </div>
          </div>

          <div class="health-stats">
            <div class="health-stat">
              <span>样本通话</span>
              <strong>{{ healthDialog.response.runtimeStats.sampleCalls }}</strong>
            </div>
            <div class="health-stat">
              <span>已结束</span>
              <strong>{{ healthDialog.response.runtimeStats.endedCalls }}</strong>
            </div>
            <div class="health-stat">
              <span>转人工</span>
              <strong>{{ healthDialog.response.runtimeStats.transferCalls }}</strong>
            </div>
            <div class="health-stat">
              <span>错误</span>
              <strong>{{ healthDialog.response.runtimeStats.errorCalls }}</strong>
            </div>
            <div class="health-stat">
              <span>超时</span>
              <strong>{{ healthDialog.response.runtimeStats.timeoutCalls }}</strong>
            </div>
            <div class="health-stat">
              <span>平均时长</span>
              <strong>{{ healthDialog.response.runtimeStats.avgDurationSeconds }}s</strong>
            </div>
          </div>

          <el-tabs model-value="issues">
            <el-tab-pane label="问题清单" name="issues">
              <el-table
                :data="healthDialog.response.issues"
                border
                stripe
                max-height="280"
                empty-text="暂无明显问题"
              >
                <el-table-column label="级别" width="90">
                  <template #default="{ row }">
                    <el-tag :type="issueTagType(row.level)" effect="plain" size="small">
                      {{ issueLevelLabel(row.level) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="category" label="类别" width="100" />
                <el-table-column label="节点" width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.nodeName || row.nodeId || '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="message" label="问题" min-width="190" show-overflow-tooltip />
                <el-table-column prop="suggestion" label="建议" min-width="240" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="节点效果" name="nodes">
              <el-table
                :data="healthDialog.response.nodes"
                border
                stripe
                max-height="320"
                empty-text="暂无节点"
              >
                <el-table-column prop="nodeName" label="节点" min-width="140" show-overflow-tooltip />
                <el-table-column prop="nodeType" label="类型" width="90" />
                <el-table-column label="连接" width="90">
                  <template #default="{ row }">{{ row.incoming }} / {{ row.outgoing }}</template>
                </el-table-column>
                <el-table-column prop="enterCount" label="进入" width="80" />
                <el-table-column prop="fallbackCount" label="Fallback" width="90" />
                <el-table-column prop="errorCount" label="失败" width="80" />
                <el-table-column prop="aiHitCount" label="AI 命中" width="90" />
                <el-table-column label="成功率" width="90">
                  <template #default="{ row }">{{ rateText(row.successRate) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="nodeTagType(row.healthLevel)" effect="plain" size="small">
                      {{ nodeLevelLabel(row.healthLevel) }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="debugDialog.visible" title="模拟呼叫" width="760px">
      <div class="debug-head">
        <div>
          <div class="debug-title">{{ debugDialog.flow?.flowName }}</div>
          <div class="debug-meta">
            {{ statusLabel }}
            <span v-if="debugDialog.response?.currentNodeName"> · {{ debugDialog.response.currentNodeName }}</span>
            <span v-if="waitingHint" class="waiting-hint"> · {{ waitingHint }}</span>
          </div>
        </div>
        <el-button size="small" :loading="debugDialog.loading" @click="restartDebug">重新开始</el-button>
      </div>

      <div class="debug-form">
        <el-input v-model="debugDialog.caller" placeholder="主叫号码" />
        <el-input v-model="debugDialog.callee" placeholder="被叫号码" />
      </div>

      <div v-if="debugDialog.response" class="debug-inspector">
        <div class="debug-current">
          <div class="debug-current-item">
            <span>当前节点</span>
            <strong>{{ debugDialog.response.currentNodeName || '-' }}</strong>
          </div>
          <div class="debug-current-item">
            <span>节点 ID</span>
            <code>{{ debugDialog.response.currentNodeId || '-' }}</code>
          </div>
          <div class="debug-current-item">
            <span>运行状态</span>
            <strong>{{ statusLabel }}</strong>
          </div>
          <div class="debug-current-item">
            <span>等待输入</span>
            <strong>{{ debugDialog.response.waitingFor || '-' }}</strong>
          </div>
        </div>

        <div class="debug-vars">
          <div class="debug-section-title">变量表</div>
          <el-table
            :data="debugVariables"
            size="small"
            border
            max-height="180"
            empty-text="暂无变量"
          >
            <el-table-column prop="key" label="变量" width="150">
              <template #default="{ row }">
                <code class="code-chip">{{ row.key }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="value" label="当前值" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <div class="debug-log" v-loading="debugDialog.loading && debugDialog.messages.length === 0">
        <div
          v-for="(message, index) in debugDialog.messages"
          :key="`${message.type}-${index}`"
          class="debug-message"
          :class="`is-${message.type}`"
        >
          <span class="debug-message-label">{{ messageLabel(message.type) }}</span>
          <span>{{ message.text }}</span>
        </div>
      </div>

      <div v-if="debugDialog.response?.options?.length" class="debug-options">
        <el-button
          v-for="option in debugDialog.response.options"
          :key="`${option.key}-${option.targetNodeId}`"
          size="small"
          plain
          @click="sendDebugInput(option.key)"
        >
          {{ option.key }} · {{ option.label }}
        </el-button>
      </div>

      <div class="debug-input">
        <el-input
          v-model="debugDialog.input"
          :disabled="debugDialog.response?.status === 'ended'"
          :placeholder="inputPlaceholder"
          @keyup.enter="sendDebugInput()"
        />
        <el-button
          type="primary"
          :loading="debugDialog.sending"
          :disabled="debugDialog.response?.status === 'ended'"
          @click="sendDebugInput()"
        >
          发送
        </el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="versionDialog.visible" title="流程版本" width="860px">
      <div class="version-head">
        <div>
          <div class="debug-title">{{ versionDialog.flow?.flowName }}</div>
          <div class="debug-meta">
            当前发布版本 v{{ versionDialog.flow?.currentVersion || 0 }}
            <span v-if="versionDialog.flow?.hasDraftDiff" class="version-diff-hint">
              · 草稿与当前发布版本不一致
            </span>
          </div>
        </div>
        <el-button size="small" :loading="versionDialog.loading" @click="fetchVersions">刷新</el-button>
      </div>

      <el-table
        :data="versionDialog.list"
        v-loading="versionDialog.loading"
        border
        stripe
        max-height="420"
      >
        <el-table-column prop="versionLabel" label="版本" width="100">
          <template #default="{ row }">
            <div class="version-cell">
              <span>{{ row.versionLabel }}</span>
              <el-tag v-if="row.current" type="success" effect="plain" size="small">当前</el-tag>
              <el-tag v-else-if="row.draft" type="info" effect="plain" size="small">草稿</el-tag>
              <el-tag v-if="row.diffFromPublished" type="warning" effect="plain" size="small">有差异</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结构" width="110">
          <template #default="{ row }">
            <span class="num">{{ row.nodeCount }} 节点 / {{ row.edgeCount }} 线</span>
          </template>
        </el-table-column>
        <el-table-column prop="changeNote" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="action-list">
              <el-button size="small" text @click="previewVersion(row)">预览</el-button>
              <el-button
                v-if="!row.draft && userStore.hasPerm('flow:edit')"
                size="small"
                text
                type="primary"
                @click="restoreVersion(row)"
              >
                恢复草稿
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="previewDialog.visible" :title="previewDialog.title" width="780px">
      <pre class="version-preview">{{ formattedPreviewJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from 'lucide-vue-next'
import {
  deleteFlow,
  getFlowHealth,
  listFlowVersions,
  offlineFlow,
  pageFlows,
  publishFlow,
  restoreFlowVersionToDraft,
  sendFlowDebugInput,
  startFlowDebug
} from '@/api/flow'
import type { FlowDebugResponse, FlowHealthResponse, FlowItem, FlowVersionItem } from '@/api/flow'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref<FlowItem[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})
const hasFlowActions = computed(() => userStore.hasAnyPerm(['flow:list', 'flow:edit', 'flow:publish', 'flow:delete']))
const debugDialog = reactive({
  visible: false,
  loading: false,
  sending: false,
  flow: null as FlowItem | null,
  caller: '13800000000',
  callee: '4001',
  input: '',
  response: null as FlowDebugResponse | null,
  messages: [] as { type: 'event' | 'prompt' | 'input' | 'result'; text: string }[]
})
const healthDialog = reactive({
  visible: false,
  loading: false,
  flow: null as FlowItem | null,
  response: null as FlowHealthResponse | null
})
const versionDialog = reactive({
  visible: false,
  loading: false,
  flow: null as FlowItem | null,
  list: [] as FlowVersionItem[]
})
const previewDialog = reactive({
  visible: false,
  title: '',
  graphJson: ''
})

const statusLabel = computed(() => {
  const status = debugDialog.response?.status
  if (status === 'ended') return '已结束'
  if (status === 'waiting') {
    return debugDialog.response?.waitingFor === 'asr' ? '等待语音输入' : '等待按键'
  }
  return '运行中'
})

const inputPlaceholder = computed(() => {
  if (debugDialog.response?.waitingFor === 'asr') {
    return '输入模拟语音文本，如「我想查账单」'
  }
  return '输入按键，例如 1'
})

const waitingHint = computed(() => {
  if (debugDialog.response?.status !== 'waiting') return ''
  return debugDialog.response?.waitingFor === 'asr'
    ? '语音节点已暂停，输入文本即可继续'
    : '按键节点已暂停，输入或点击下方选项'
})

const debugVariables = computed(() => {
  const vars = debugDialog.response?.variables || {}
  return Object.entries(vars)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => ({
      key,
      value: String(value ?? '')
    }))
})

const formattedPreviewJson = computed(() => {
  try {
    return JSON.stringify(JSON.parse(previewDialog.graphJson || '{}'), null, 2)
  } catch {
    return previewDialog.graphJson
  }
})

async function fetchList() {
  loading.value = true
  try {
    const res = await pageFlows({
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

function onAdd() {
  router.push('/flow/editor/new')
}
function onEdit(row: any) {
  router.push(`/flow/editor/${row.id}`)
}
async function onDebug(row: FlowItem) {
  debugDialog.flow = row
  debugDialog.visible = true
  await restartDebug()
}
async function onHealth(row: FlowItem) {
  healthDialog.flow = row
  healthDialog.visible = true
  healthDialog.loading = true
  healthDialog.response = null
  try {
    healthDialog.response = await getFlowHealth(row.id)
  } finally {
    healthDialog.loading = false
  }
}
async function onPublish(row: FlowItem) {
  await ElMessageBox.confirm(`确定发布「${row.flowName}」吗？发布后立即生效。`, '确认发布', {
    type: 'warning'
  })
  await publishFlow(row.id)
  ElMessage.success('发布成功')
  await fetchList()
}
async function onOffline(row: FlowItem) {
  await ElMessageBox.confirm(`确定下线「${row.flowName}」吗？下线后热线不能再绑定这个流程。`, '确认下线', {
    type: 'warning'
  })
  await offlineFlow(row.id)
  ElMessage.success('下线成功')
  await fetchList()
}
async function onVersions(row: FlowItem) {
  versionDialog.flow = row
  versionDialog.visible = true
  await fetchVersions()
}
async function fetchVersions() {
  if (!versionDialog.flow) return
  versionDialog.loading = true
  try {
    versionDialog.list = await listFlowVersions(versionDialog.flow.id)
    versionDialog.flow.hasDraftDiff = versionDialog.list.some((item) => item.diffFromPublished)
  } finally {
    versionDialog.loading = false
  }
}
function previewVersion(row: FlowVersionItem) {
  previewDialog.title = `版本预览 - ${row.versionLabel}`
  previewDialog.graphJson = row.graphJson || ''
  previewDialog.visible = true
}
async function restoreVersion(row: FlowVersionItem) {
  if (!versionDialog.flow) return
  await ElMessageBox.confirm(
    `确定把「${row.versionLabel}」恢复为当前草稿吗？恢复后需要重新发布才会影响线上流程。`,
    '恢复草稿',
    { type: 'warning' }
  )
  await restoreFlowVersionToDraft(versionDialog.flow.id, row.version)
  ElMessage.success('已恢复为当前草稿')
  await fetchVersions()
  await fetchList()
}
async function onDelete(row: FlowItem) {
  await ElMessageBox.confirm(`确定删除「${row.flowName}」吗？`, '确认删除', {
    type: 'warning'
  })
  await deleteFlow(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}

async function restartDebug() {
  if (!debugDialog.flow) return
  debugDialog.loading = true
  debugDialog.input = ''
  debugDialog.messages = []
  try {
    const res = await startFlowDebug(debugDialog.flow.id, {
      caller: debugDialog.caller,
      callee: debugDialog.callee
    })
    applyDebugResponse(res)
  } finally {
    debugDialog.loading = false
  }
}

async function sendDebugInput(value?: string) {
  const input = (value || debugDialog.input).trim()
  if (!debugDialog.response?.sessionId || !input || debugDialog.response.status === 'ended') return
  debugDialog.sending = true
  debugDialog.messages.push({ type: 'input', text: input })
  debugDialog.input = ''
  try {
    const res = await sendFlowDebugInput(debugDialog.response.sessionId, input)
    applyDebugResponse(res)
  } finally {
    debugDialog.sending = false
  }
}

function applyDebugResponse(res: FlowDebugResponse) {
  debugDialog.response = res
  res.events.forEach((text) => debugDialog.messages.push({ type: 'event', text }))
  res.prompts.forEach((text) => debugDialog.messages.push({ type: 'prompt', text }))
  if (res.status === 'ended' && res.result) {
    debugDialog.messages.push({ type: 'result', text: res.result })
  }
}

function messageLabel(type: 'event' | 'prompt' | 'input' | 'result') {
  const labels = {
    event: '系统',
    prompt: '语音',
    input: '按键',
    result: '结果'
  }
  return labels[type]
}

function issueTagType(level: string) {
  if (level === 'error') return 'danger'
  if (level === 'warning') return 'warning'
  return 'info'
}

function issueLevelLabel(level: string) {
  if (level === 'error') return '严重'
  if (level === 'warning') return '风险'
  return '提示'
}

function nodeTagType(level: string) {
  if (level === 'danger') return 'danger'
  if (level === 'warning') return 'warning'
  if (level === 'success') return 'success'
  return 'info'
}

function nodeLevelLabel(level: string) {
  if (level === 'danger') return '异常'
  if (level === 'warning') return '关注'
  if (level === 'success') return '正常'
  return '无样本'
}

function rateText(value?: number) {
  if (value === undefined || value === null) return '-'
  return `${Math.round(value * 100)}%`
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
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
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
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.code-chip {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-neutral-100);
  color: var(--color-neutral-700);
}
.num {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}
.action-list {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  white-space: nowrap;
  :deep(.el-button) {
    margin-left: 0;
  }
}
.debug-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.version-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.version-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.version-inline {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}
.version-diff-hint {
  color: var(--color-warning);
}
.version-preview {
  max-height: 520px;
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
.health-dialog {
  min-height: 220px;
}
.health-head {
  display: grid;
  grid-template-columns: 112px 1fr;
  gap: var(--space-3);
  align-items: stretch;
  margin-bottom: var(--space-3);
}
.health-score {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 88px;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  strong {
    font-size: 30px;
    line-height: 1;
    color: var(--color-text);
  }
  span {
    margin-top: var(--space-2);
    font-size: var(--text-xs);
    color: var(--color-text-muted);
  }
  &.is-a {
    border-color: var(--color-success);
  }
  &.is-b {
    border-color: var(--color-primary);
  }
  &.is-c {
    border-color: var(--color-warning);
  }
  &.is-d {
    border-color: var(--color-error);
  }
}
.health-copy {
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}
.health-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.health-stat {
  min-width: 0;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  span {
    display: block;
    margin-bottom: 4px;
    font-size: var(--text-xs);
    color: var(--color-text-subtle);
  }
  strong {
    font-size: var(--text-sm);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
  }
}
.debug-title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
}
.debug-meta {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
.waiting-hint {
  color: var(--color-primary);
}
.debug-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.debug-inspector {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.debug-current {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  padding: var(--space-3);
}
.debug-current-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-bottom: 1px solid var(--color-border);
  &:last-child {
    border-bottom: none;
  }
  span {
    font-size: var(--text-xs);
    color: var(--color-text-subtle);
  }
  strong {
    min-width: 0;
    text-align: right;
    font-size: var(--text-xs);
    font-weight: var(--weight-medium);
    color: var(--color-text);
  }
  code {
    min-width: 0;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.debug-vars {
  min-width: 0;
}
.debug-section-title {
  margin-bottom: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
.debug-log {
  min-height: 260px;
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  padding: var(--space-3);
}
.debug-message {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  color: var(--color-text);
  &:last-child {
    margin-bottom: 0;
  }
  &.is-prompt {
    color: var(--color-primary);
  }
  &.is-input {
    color: var(--color-success);
  }
  &.is-result {
    color: var(--color-warning);
  }
}
.debug-message-label {
  flex: 0 0 34px;
  color: var(--color-text-subtle);
  font-size: var(--text-xs);
}
.debug-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
  :deep(.el-button) {
    margin-left: 0;
  }
}
.debug-input {
  display: grid;
  grid-template-columns: 1fr 88px;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
</style>
