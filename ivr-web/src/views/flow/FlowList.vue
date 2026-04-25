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
          <el-table-column prop="currentVersion" label="版本" width="80">
            <template #default="{ row }">
              <span class="num">v{{ row.currentVersion }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column v-if="hasFlowActions" label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text type="success" @click="onDebug(row)">调试</el-button>
                <el-button v-if="userStore.hasPerm('flow:edit')" size="small" text @click="onEdit(row)">编辑</el-button>
                <el-button v-if="userStore.hasPerm('flow:publish')" size="small" text type="primary" @click="onPublish(row)">发布</el-button>
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

    <el-dialog v-model="debugDialog.visible" title="模拟呼叫" width="680px">
      <div class="debug-head">
        <div>
          <div class="debug-title">{{ debugDialog.flow?.flowName }}</div>
          <div class="debug-meta">
            {{ debugDialog.response?.status === 'ended' ? '已结束' : debugDialog.response?.status === 'waiting' ? '等待输入' : '运行中' }}
            <span v-if="debugDialog.response?.currentNodeName"> · {{ debugDialog.response.currentNodeName }}</span>
          </div>
        </div>
        <el-button size="small" :loading="debugDialog.loading" @click="restartDebug">重新开始</el-button>
      </div>

      <div class="debug-form">
        <el-input v-model="debugDialog.caller" placeholder="主叫号码" />
        <el-input v-model="debugDialog.callee" placeholder="被叫号码" />
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
          placeholder="输入按键，例如 1"
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
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from 'lucide-vue-next'
import { deleteFlow, pageFlows, publishFlow, sendFlowDebugInput, startFlowDebug } from '@/api/flow'
import type { FlowDebugResponse, FlowItem } from '@/api/flow'
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
async function onPublish(row: FlowItem) {
  await ElMessageBox.confirm(`确定发布「${row.flowName}」吗？发布后立即生效。`, '确认发布', {
    type: 'warning'
  })
  await publishFlow(row.id)
  ElMessage.success('发布成功')
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
.debug-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
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
