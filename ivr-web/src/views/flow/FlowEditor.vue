<template>
  <div class="flow-editor">
    <!-- 左侧：节点物料库 -->
    <aside class="side-panel">
      <header class="side-head">
        <h3>节点</h3>
        <p>拖拽到画布</p>
      </header>
      <div class="side-body">
        <div v-for="g in nodeGroups" :key="g.title" class="node-group">
          <div class="node-group-title">{{ g.title }}</div>
          <div
            v-for="item in g.items"
            :key="item.type"
            class="node-item"
            :class="{ 'is-ai': item.ai }"
            draggable="true"
            @dragstart="onDragStart($event, item)"
          >
            <component :is="item.icon" :size="14" :stroke-width="1.8" class="node-icon" />
            <span>{{ item.label }}</span>
            <span v-if="item.ai" class="ai-dot" title="AI 节点"></span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 中部：画布 + 工具条 -->
    <section class="canvas-wrap">
      <header class="toolbar">
        <div class="toolbar-left">
          <button class="icon-btn" @click="$router.back()" title="返回">
            <ChevronLeft :size="16" :stroke-width="1.8" />
          </button>
          <span class="sep"></span>
          <span class="flow-title">{{ flowForm.flowName || '未命名流程' }}</span>
          <span class="flow-meta">{{ statusText }} · {{ dirty ? '未保存' : '已同步' }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-if="canSave && selectedElementId" size="small" text type="danger" @click="deleteSelected">
            <Trash2 :size="14" :stroke-width="1.8" style="margin-right: 4px" />
            删除选中
          </el-button>
          <el-button v-if="canSave" size="small" :loading="saving" @click="onSave">
            <Save :size="14" :stroke-width="1.8" style="margin-right: 4px" />
            保存
          </el-button>
          <el-button v-if="canPublish" size="small" type="primary" :loading="publishing" @click="onPublish">
            <Upload :size="14" :stroke-width="1.8" style="margin-right: 4px" />
            发布
          </el-button>
        </div>
      </header>
      <div class="flow-form">
        <el-input v-model="flowForm.flowName" placeholder="流程名称" @input="dirty = true" />
        <el-input v-model="flowForm.flowCode" placeholder="流程编码" @input="dirty = true" />
        <el-input v-model="flowForm.description" placeholder="流程描述" @input="dirty = true" />
      </div>
      <div ref="canvasRef" class="canvas"></div>
    </section>

    <!-- 右侧：属性面板 -->
    <aside class="prop-panel">
      <header class="side-head">
        <h3>属性</h3>
        <p>{{ propertyHint }}</p>
      </header>
      <div class="side-body">
        <div v-if="!selectedNode && !selectedEdge" class="empty-hint">
          <MousePointerClick :size="20" :stroke-width="1.5" />
          <span>选中节点或连线以编辑</span>
        </div>
        <el-form v-else-if="selectedNode" label-position="top" size="small">
          <el-form-item label="节点 ID">
            <el-input v-model="selectedNode.id" disabled />
          </el-form-item>
          <el-form-item label="类型">
            <el-tag effect="plain" size="small">{{ selectedBizType }}</el-tag>
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input
              v-model="selectedNode.properties.name"
              placeholder="节点显示名"
              @input="syncSelectedNode"
            />
          </el-form-item>
          <template v-if="selectedBizType === 'play'">
            <el-form-item label="播放文本 (TTS)">
              <el-input
                v-model="selectedNode.properties.ttsText"
                type="textarea"
                :rows="3"
                placeholder="例如：欢迎致电，按 1 查询…"
                @input="syncSelectedNode"
              />
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'dtmf'">
            <el-form-item label="最大位数">
              <el-input-number
                v-model="selectedNode.properties.maxDigits"
                :min="1" :max="16"
                style="width: 100%"
                @change="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="超时 (秒)">
              <el-input-number
                v-model="selectedNode.properties.timeoutSec"
                :min="1" :max="60"
                style="width: 100%"
                @change="syncSelectedNode"
              />
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'transfer'">
            <el-form-item label="人工坐席">
              <el-input
                v-model="selectedNode.properties.target"
                placeholder="例如：1000"
                @input="syncSelectedNode"
              />
            </el-form-item>
          </template>
        </el-form>
        <el-form v-else label-position="top" size="small">
          <el-form-item label="连线 ID">
            <el-input v-model="selectedEdge.id" disabled />
          </el-form-item>
          <el-form-item label="起点">
            <el-input :model-value="selectedEdge.sourceNodeId" disabled />
          </el-form-item>
          <el-form-item label="终点">
            <el-input :model-value="selectedEdge.targetNodeId" disabled />
          </el-form-item>
          <el-form-item label="分支按键">
            <el-input
              v-model="selectedEdge.properties.key"
              maxlength="16"
              placeholder="例如：1"
              @input="syncSelectedEdge"
            />
          </el-form-item>
          <el-form-item label="显示文本">
            <el-input
              v-model="selectedEdge.properties.label"
              placeholder="例如：业务咨询"
              @input="syncSelectedEdge"
            />
          </el-form-item>
        </el-form>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createFlow, getFlow, publishFlow, updateFlow } from '@/api/flow'
import { useUserStore } from '@/stores/user'
import {
  ChevronLeft, Save, Upload, MousePointerClick,
  Play, Hash, GitBranch, PhoneForwarded, Mail,
  Globe, Variable, Mic, Brain, BookOpen,
  PlayCircle, CircleStop, Trash2
} from 'lucide-vue-next'

interface NodeItem {
  type: string
  label: string
  icon: any
  ai?: boolean
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const nodeGroups: { title: string; items: NodeItem[] }[] = [
  {
    title: '流程控制',
    items: [
      { type: 'start', label: '开始', icon: markRaw(PlayCircle) },
      { type: 'end',   label: '结束', icon: markRaw(CircleStop) }
    ]
  },
  {
    title: '基础动作',
    items: [
      { type: 'play',       label: '播放语音', icon: markRaw(Play) },
      { type: 'dtmf',       label: '按键收集', icon: markRaw(Hash) },
      { type: 'condition',  label: '条件判断', icon: markRaw(GitBranch) },
      { type: 'var_assign', label: '变量赋值', icon: markRaw(Variable) },
      { type: 'http',       label: 'HTTP 调用', icon: markRaw(Globe) }
    ]
  },
  {
    title: 'AI 能力',
    items: [
      { type: 'asr',    label: '语音识别', icon: markRaw(Mic),       ai: true },
      { type: 'intent', label: 'AI 意图',  icon: markRaw(Brain),     ai: true },
      { type: 'rag',    label: 'AI 问答',  icon: markRaw(BookOpen),  ai: true }
    ]
  },
  {
    title: '服务转接',
    items: [
      { type: 'transfer',  label: '转人工', icon: markRaw(PhoneForwarded) },
      { type: 'voicemail', label: '留言',   icon: markRaw(Mail) }
    ]
  }
]

const canvasRef = ref<HTMLDivElement>()
const selectedNode = ref<any>(null)
const selectedEdge = ref<any>(null)
const saving = ref(false)
const publishing = ref(false)
const dirty = ref(false)
const flowId = ref<string>(String(route.params.id || 'new'))
const flowForm = reactive({
  flowCode: '',
  flowName: '未命名流程',
  description: '',
  status: 0,
  currentVersion: 0
})
let lf: any = null

const selectedBizType = computed(() => selectedNode.value?.properties?.bizType || selectedNode.value?.type || '')
const selectedElementId = computed(() => selectedNode.value?.id || selectedEdge.value?.id || '')
const propertyHint = computed(() => {
  if (selectedNode.value) return '编辑节点配置'
  if (selectedEdge.value) return '编辑连线配置'
  return '未选中元素'
})
const canSave = computed(() => flowId.value === 'new' ? userStore.hasPerm('flow:add') : userStore.hasPerm('flow:edit'))
const canPublish = computed(() => userStore.hasPerm('flow:publish'))
const statusText = computed(() => {
  if (flowForm.status === 1) return `已发布 v${flowForm.currentVersion || 1}`
  if (flowForm.status === 2) return '已下线'
  return '草稿'
})

onMounted(async () => {
  const { default: LogicFlow } = await import('@logicflow/core')
  await import('@logicflow/core/lib/style/index.css')

  lf = new LogicFlow({
    container: canvasRef.value!,
    grid: { size: 12, visible: true, type: 'dot', config: { color: '#e5e7eb' } },
    background: { color: '#ffffff' },
    keyboard: { enabled: true },
    style: {
      rect: {
        rx: 6, ry: 6,
        stroke: '#e5e7eb',
        strokeWidth: 1,
        fill: '#ffffff'
      },
      circle: {
        stroke: '#0f172a',
        strokeWidth: 1,
        fill: '#ffffff'
      },
      nodeText: {
        color: '#111827',
        fontSize: 12
      },
      polyline: {
        stroke: '#9ca3af',
        strokeWidth: 1.5
      },
      bezier: {
        stroke: '#9ca3af',
        strokeWidth: 1.5
      }
    }
  })

  lf.render(emptyGraph())

  lf.on('node:click', ({ data }: any) => {
    selectedNode.value = normalizeNodeData(data)
    selectedEdge.value = null
  })
  lf.on('edge:click', ({ data }: any) => {
    selectedEdge.value = normalizeEdgeData(data)
    selectedNode.value = null
  })
  lf.on('blank:click', () => {
    clearSelection()
  })
  ;[
    'node:add',
    'edge:add',
    'node:delete',
    'edge:delete',
    'node:dragend',
    'edge:adjust',
    'edge:exchange-node'
  ].forEach((eventName) => lf.on(eventName, markDirty))

  await loadFlow()
})

onBeforeUnmount(() => { lf = null })

function onDragStart(_e: DragEvent, item: NodeItem) {
  if (!lf) return
  lf.dnd.startDrag({
    type: item.type === 'start' || item.type === 'end' ? 'circle' : 'rect',
    text: item.label,
    properties: defaultNodeProperties(item)
  })
}

async function loadFlow() {
  if (flowId.value === 'new') {
    flowForm.flowCode = `flow-${Date.now()}`
    flowForm.flowName = '未命名流程'
    dirty.value = true
    return
  }
  const detail = await getFlow(flowId.value)
  flowForm.flowCode = detail.flowCode
  flowForm.flowName = detail.flowName
  flowForm.description = detail.description || ''
  flowForm.status = detail.status
  flowForm.currentVersion = detail.currentVersion
  renderGraph(detail.graphJson)
  dirty.value = false
}

async function onSave() {
  if (!lf) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (flowId.value === 'new') {
      const id = await createFlow(payload)
      flowId.value = String(id)
      await router.replace(`/flow/editor/${id}`)
    } else {
      await updateFlow(flowId.value, payload)
    }
    dirty.value = false
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}
async function onPublish() {
  if (!validateBeforePublish()) return
  if (dirty.value) {
    await onSave()
  }
  publishing.value = true
  try {
    await publishFlow(flowId.value)
    flowForm.status = 1
    flowForm.currentVersion += 1
    ElMessage.success('发布成功')
  } finally {
    publishing.value = false
  }
}

function syncSelectedNode() {
  if (!lf || !selectedNode.value) return
  lf.setProperties(selectedNode.value.id, selectedNode.value.properties)
  if (selectedNode.value.properties?.name) {
    lf.updateText(selectedNode.value.id, selectedNode.value.properties.name)
  }
  dirty.value = true
}

function syncSelectedEdge() {
  if (!lf || !selectedEdge.value) return
  const key = String(selectedEdge.value.properties?.key || '').trim()
  const label = String(selectedEdge.value.properties?.label || '').trim()
  selectedEdge.value.properties.key = key
  selectedEdge.value.properties.label = label
  lf.setProperties(selectedEdge.value.id, selectedEdge.value.properties)
  lf.updateText(selectedEdge.value.id, key || label)
  dirty.value = true
}

function deleteSelected() {
  if (!lf || !selectedElementId.value) return
  lf.deleteElement(selectedElementId.value)
  clearSelection()
  dirty.value = true
}

function buildPayload() {
  const graph = normalizeGraphForSave(lf.getGraphData())
  return {
    flowCode: flowForm.flowCode,
    flowName: flowForm.flowName,
    description: flowForm.description,
    graphJson: JSON.stringify(graph)
  }
}

function renderGraph(graphJson?: string) {
  if (!lf) return
  try {
    lf.render(graphJson ? JSON.parse(graphJson) : emptyGraph())
  } catch {
    lf.render(emptyGraph())
  }
  clearSelection()
}

function emptyGraph() {
  return { nodes: [], edges: [] }
}

function defaultNodeProperties(item: NodeItem) {
  const base: Record<string, any> = { bizType: item.type, name: item.label }
  if (item.type === 'play') base.ttsText = '欢迎致电，请根据语音提示选择服务。'
  if (item.type === 'dtmf') {
    base.maxDigits = 1
    base.timeoutSec = 8
  }
  if (item.type === 'transfer') base.target = '1000'
  return base
}

function normalizeNodeData(data: any) {
  return {
    ...data,
    properties: {
      bizType: data?.properties?.bizType || data?.type || '',
      name: data?.properties?.name || textValue(data?.text) || data?.id,
      ...(data?.properties || {})
    }
  }
}

function normalizeEdgeData(data: any) {
  const properties = data?.properties || {}
  const text = textValue(data?.text)
  return {
    ...data,
    properties: {
      ...properties,
      key: properties.key || text || '',
      label: properties.label || ''
    }
  }
}

function clearSelection() {
  selectedNode.value = null
  selectedEdge.value = null
}

function markDirty() {
  dirty.value = true
}

function normalizeGraphForSave(graph: any) {
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : []
  const edges = Array.isArray(graph.edges) ? graph.edges : []
  const normalizedNodes = nodes.map((node: any) => {
    const properties = {
      ...(node.properties || {}),
      bizType: node.properties?.bizType || node.type || '',
      name: node.properties?.name || textValue(node.text) || node.id
    }
    if (properties.bizType === 'dtmf') {
      properties.mappings = edges
        .filter((edge: any) => edge.sourceNodeId === node.id)
        .map((edge: any, index: number) => ({
          key: edgeBranchKey(edge) || String(index + 1),
          nextNode: edge.targetNodeId
        }))
    }
    return { ...node, properties }
  })
  const normalizedEdges = edges.map((edge: any) => {
    const key = edgeBranchKey(edge)
    return {
      ...edge,
      text: key || textValue(edge.text),
      properties: {
        ...(edge.properties || {}),
        ...(key ? { key } : {})
      }
    }
  })
  return { ...graph, nodes: normalizedNodes, edges: normalizedEdges }
}

function validateBeforePublish() {
  const errors = validateGraph(normalizeGraphForSave(lf.getGraphData()))
  if (errors.length === 0) return true
  ElMessageBox.alert(errors.join('\n'), '流程无法发布', {
    confirmButtonText: '知道了',
    type: 'warning'
  })
  return false
}

function validateGraph(graph: any) {
  const errors: string[] = []
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : []
  const edges = Array.isArray(graph.edges) ? graph.edges : []
  const nodeIds = new Set(nodes.map((node: any) => node.id))
  const startNodes = nodes.filter((node: any) => bizType(node) === 'start')
  const terminalNodes = nodes.filter((node: any) => ['end', 'transfer', 'voicemail'].includes(bizType(node)))

  if (!flowForm.flowName.trim()) errors.push('请填写流程名称')
  if (!flowForm.flowCode.trim()) errors.push('请填写流程编码')
  if (nodes.length === 0) errors.push('请至少添加一个节点')
  if (startNodes.length !== 1) errors.push('流程必须且只能有一个开始节点')
  if (terminalNodes.length === 0) errors.push('请至少添加一个结束、转人工或留言节点')

  edges.forEach((edge: any) => {
    if (!nodeIds.has(edge.sourceNodeId) || !nodeIds.has(edge.targetNodeId)) {
      errors.push(`连线 ${edge.id || ''} 指向了不存在的节点`)
    }
  })

  if (startNodes.length === 1 && outgoing(edges, startNodes[0].id).length === 0) {
    errors.push('开始节点必须连接到下一个节点')
  }

  nodes
    .filter((node: any) => bizType(node) === 'dtmf')
    .forEach((node: any) => {
      const outgoingEdges = outgoing(edges, node.id)
      if (outgoingEdges.length === 0) {
        errors.push(`按键节点「${nodeName(node)}」必须至少连接一个后续节点`)
      }
      const keys = outgoingEdges.map(edgeBranchKey).filter(Boolean)
      if (keys.length !== outgoingEdges.length) {
        errors.push(`按键节点「${nodeName(node)}」的每条连线都需要填写分支按键`)
      }
      if (new Set(keys).size !== keys.length) {
        errors.push(`按键节点「${nodeName(node)}」存在重复分支按键`)
      }
    })

  if (startNodes.length === 1) {
    const reachable = collectReachable(startNodes[0].id, edges)
    const unreachable = nodes.filter((node: any) => !reachable.has(node.id))
    if (unreachable.length > 0) {
      errors.push(`存在未接入主流程的节点：${unreachable.map(nodeName).join('、')}`)
    }
  }

  return Array.from(new Set(errors))
}

function collectReachable(startNodeId: string, edges: any[]) {
  const visited = new Set<string>()
  const queue = [startNodeId]
  while (queue.length > 0) {
    const nodeId = queue.shift()!
    if (visited.has(nodeId)) continue
    visited.add(nodeId)
    outgoing(edges, nodeId).forEach((edge) => {
      if (!visited.has(edge.targetNodeId)) queue.push(edge.targetNodeId)
    })
  }
  return visited
}

function outgoing(edges: any[], nodeId: string) {
  return edges.filter((edge) => edge.sourceNodeId === nodeId)
}

function bizType(node: any) {
  return node?.properties?.bizType || node?.type || ''
}

function nodeName(node: any) {
  return node?.properties?.name || textValue(node?.text) || node?.id || '未命名节点'
}

function edgeBranchKey(edge: any) {
  return String(edge?.properties?.key || textValue(edge?.text) || '').trim()
}

function textValue(text: any) {
  if (!text) return ''
  if (typeof text === 'string') return text
  return String(text.value || text.text || '')
}
</script>

<style scoped lang="scss">
.flow-editor {
  display: flex;
  height: calc(100vh - var(--topbar-height));
  background: var(--color-bg-muted);
}

// ---------- 通用侧边栏样式（左右共用） ----------
.side-panel, .prop-panel {
  width: 240px;
  background: var(--color-bg);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.prop-panel {
  border-right: none;
  border-left: 1px solid var(--color-border);
}
.side-head {
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  h3 {
    font-size: var(--text-sm);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
  }
  p {
    margin-top: 2px;
    font-size: var(--text-xs);
    color: var(--color-text-muted);
  }
}
.side-body {
  flex: 1;
  padding: var(--space-3);
  overflow-y: auto;
}

// ---------- 节点物料 ----------
.node-group { margin-bottom: var(--space-4); }
.node-group-title {
  padding: var(--space-1) var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
  letter-spacing: 0.4px;
  text-transform: uppercase;
  margin-bottom: var(--space-1);
}
.node-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  margin-bottom: 2px;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  font-size: var(--text-sm);
  color: var(--color-text);
  cursor: grab;
  user-select: none;
  transition: border-color var(--tr-fast), background var(--tr-fast);
  .node-icon { color: var(--color-text-muted); }
  &:hover {
    background: var(--color-neutral-50);
    border-color: var(--color-border);
  }
  &:active { cursor: grabbing; }
  &.is-ai {
    .node-icon { color: var(--color-primary); }
  }
}
.ai-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  margin-left: auto;
}

// ---------- 画布区 ----------
.canvas-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}
.toolbar {
  height: var(--topbar-height-sm);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg);
  flex-shrink: 0;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.toolbar-right { display: flex; gap: var(--space-2); }
.flow-form {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(160px, 220px) minmax(220px, 1.5fr);
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg);
}
.icon-btn {
  appearance: none;
  width: 28px; height: 28px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background var(--tr-fast), border-color var(--tr-fast);
  &:hover {
    background: var(--color-neutral-50);
    border-color: var(--color-border);
    color: var(--color-text);
  }
}
.sep {
  width: 1px; height: 16px;
  background: var(--color-border);
}
.flow-title {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--color-text);
}
.flow-meta {
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
  padding-left: var(--space-2);
  border-left: 1px solid var(--color-border);
  margin-left: var(--space-2);
}
.canvas { flex: 1; overflow: hidden; }

// ---------- 属性面板 ----------
.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-4);
  color: var(--color-text-subtle);
  font-size: var(--text-xs);
}
</style>
