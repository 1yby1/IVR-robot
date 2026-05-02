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
          <span class="flow-meta">
            {{ statusText }}
            <span v-if="draftDiffText" class="flow-diff"> · {{ draftDiffText }}</span>
            · {{ dirty ? '未保存' : '已同步' }}
          </span>
        </div>
        <div class="toolbar-right">
          <el-button v-if="canAiGenerate" size="small" :loading="aiDialog.loading" @click="openAiGenerate">
            <Sparkles :size="14" :stroke-width="1.8" style="margin-right: 4px" />
            AI 生成
          </el-button>
          <el-button v-if="canSave && selectedElementId" size="small" text type="danger" @click="deleteSelected">
            <Trash2 :size="14" :stroke-width="1.8" style="margin-right: 4px" />
            删除选中
          </el-button>
          <el-button v-if="canDebug" size="small" :loading="debugPanel.loading" @click="startEditorDebug">
            <Play :size="14" :stroke-width="1.8" style="margin-right: 4px" />
            调试
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
      <div v-if="debugPanel.visible" class="debug-dock">
        <header class="debug-dock-head">
          <div>
            <div class="debug-title">可视化调试</div>
            <div class="debug-meta">
              {{ debugStatusLabel }}
              <span v-if="debugPanel.response?.currentNodeName"> · {{ debugPanel.response.currentNodeName }}</span>
              <span v-if="debugWaitingHint" class="debug-waiting"> · {{ debugWaitingHint }}</span>
            </div>
          </div>
          <div class="debug-actions">
            <el-button size="small" text :loading="debugPanel.loading" @click="startEditorDebug">
              <RotateCcw :size="14" :stroke-width="1.8" />
            </el-button>
            <el-button size="small" text @click="closeEditorDebug">
              <X :size="14" :stroke-width="1.8" />
            </el-button>
          </div>
        </header>

        <div class="debug-call-form">
          <el-input v-model="debugPanel.caller" size="small" placeholder="主叫号码" />
          <el-input v-model="debugPanel.callee" size="small" placeholder="被叫号码" />
        </div>

        <div v-if="debugPanel.response" class="debug-current-row">
          <div>
            <span>当前节点</span>
            <strong>{{ debugPanel.response.currentNodeName || '-' }}</strong>
          </div>
          <div>
            <span>节点 ID</span>
            <code>{{ debugPanel.response.currentNodeId || '-' }}</code>
          </div>
          <div>
            <span>已走节点</span>
            <strong>{{ debugPanel.response.visitedNodeIds?.length || 0 }}</strong>
          </div>
        </div>

        <div class="debug-dock-body">
          <div class="debug-log" v-loading="debugPanel.loading && debugPanel.messages.length === 0">
            <div
              v-for="(message, index) in debugPanel.messages"
              :key="`${message.type}-${index}`"
              class="debug-message"
              :class="`is-${message.type}`"
            >
              <span class="debug-message-label">{{ messageLabel(message.type) }}</span>
              <span>{{ message.text }}</span>
            </div>
          </div>
          <div class="debug-vars">
            <div class="debug-section-title">变量表</div>
            <el-table :data="debugVariables" size="small" border max-height="156" empty-text="暂无变量">
              <el-table-column prop="key" label="变量" width="110">
                <template #default="{ row }">
                  <code class="code-chip">{{ row.key }}</code>
                </template>
              </el-table-column>
              <el-table-column prop="value" label="值" show-overflow-tooltip />
            </el-table>
          </div>
        </div>

        <div v-if="debugPanel.response?.options?.length" class="debug-options">
          <el-button
            v-for="option in debugPanel.response.options"
            :key="`${option.key}-${option.targetNodeId}`"
            size="small"
            plain
            @click="sendEditorDebugInput(option.key)"
          >
            {{ option.key }} · {{ option.label }}
          </el-button>
        </div>

        <div class="debug-input">
          <el-input
            v-model="debugPanel.input"
            size="small"
            :disabled="debugPanel.response?.status === 'ended'"
            :placeholder="debugInputPlaceholder"
            @keyup.enter="sendEditorDebugInput()"
          />
          <el-button
            size="small"
            type="primary"
            :loading="debugPanel.sending"
            :disabled="debugPanel.response?.status === 'ended'"
            @click="sendEditorDebugInput()"
          >
            <Send :size="14" :stroke-width="1.8" />
          </el-button>
        </div>
      </div>
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
          <template v-if="selectedBizType === 'condition'">
            <el-form-item label="判断表达式">
              <el-input
                v-model="selectedNode.properties.expression"
                type="textarea"
                :rows="3"
                placeholder="例如：lastDtmf == '1' ? 'vip' : 'normal'"
                @input="syncSelectedNode"
              />
              <div class="form-tip">表达式结果会作为分支标识，连线可填写 true、false、vip 等。</div>
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'var_assign'">
            <el-form-item label="变量名">
              <el-input
                v-model="selectedNode.properties.varName"
                placeholder="例如：customerLevel"
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="变量值">
              <el-input
                v-model="selectedNode.properties.value"
                type="textarea"
                :rows="2"
                placeholder="支持变量模板，例如：${caller}"
                @input="syncSelectedNode"
              />
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'http'">
            <el-form-item label="请求方法">
              <el-select
                v-model="selectedNode.properties.method"
                style="width: 100%"
                @change="syncSelectedNode"
              >
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="PATCH" value="PATCH" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
            <el-form-item label="请求 URL">
              <el-input
                v-model="selectedNode.properties.url"
                placeholder="例如：https://example.com/api/order/${lastDtmf}"
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="超时（秒）">
              <el-input-number
                v-model="selectedNode.properties.timeoutSec"
                :min="1" :max="30"
                style="width: 100%"
                @change="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="请求体模板">
              <el-input
                v-model="selectedNode.properties.bodyTemplate"
                type="textarea"
                :rows="3"
                placeholder='例如：{"caller":"${caller}","question":"${lastAsr}"}'
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="响应变量">
              <el-input
                v-model="selectedNode.properties.responseVar"
                placeholder="httpResponse"
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="状态码变量">
              <el-input
                v-model="selectedNode.properties.statusVar"
                placeholder="httpStatus"
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="失败分支">
              <el-input
                v-model="selectedNode.properties.fallbackBranch"
                placeholder="例如：fallback"
                @input="syncSelectedNode"
              />
              <div class="form-tip">2xx 状态码走默认成功连线，非 2xx 或异常走失败分支。</div>
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
          <template v-if="selectedBizType === 'voicemail'">
            <el-form-item label="最大留言时长（秒）">
              <el-input-number
                v-model="selectedNode.properties.maxSeconds"
                :min="5" :max="300"
                style="width: 100%"
                @change="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="保存路径">
              <el-input
                v-model="selectedNode.properties.filePath"
                placeholder="留空使用系统默认录音路径"
                @input="syncSelectedNode"
              />
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'asr'">
            <el-form-item label="最大录音时长（秒）">
              <el-input-number
                v-model="selectedNode.properties.maxSeconds"
                :min="1" :max="60"
                style="width: 100%"
                @change="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="识别语种">
              <el-select
                v-model="selectedNode.properties.language"
                style="width: 100%"
                @change="syncSelectedNode"
              >
                <el-option label="中文（普通话）" value="zh-CN" />
                <el-option label="英文" value="en-US" />
              </el-select>
            </el-form-item>
            <el-form-item label="录音前提示语">
              <el-input
                v-model="selectedNode.properties.prompt"
                type="textarea"
                :rows="2"
                placeholder="例如：请说出您的问题"
                @input="syncSelectedNode"
              />
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'intent'">
            <el-form-item label="输入变量">
              <el-input
                v-model="selectedNode.properties.inputVar"
                placeholder="lastAsr / lastDtmf / 自定义变量名"
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="候选意图">
              <el-select
                v-model="selectedNode.properties.intents"
                multiple filterable allow-create
                :reserve-keyword="false"
                placeholder="输入意图标签后回车"
                style="width: 100%"
                @change="syncSelectedNode"
              >
                <el-option
                  v-for="tag in selectedNode.properties.intents || []"
                  :key="tag"
                  :label="tag"
                  :value="tag"
                />
              </el-select>
              <div class="form-tip">每个意图对应一条出边，把出边的分支标识填成对应意图标签。</div>
            </el-form-item>
            <el-form-item label="未命中分支">
              <el-input
                v-model="selectedNode.properties.fallbackBranch"
                placeholder="例如：other"
                @input="syncSelectedNode"
              />
            </el-form-item>
          </template>
          <template v-if="selectedBizType === 'rag'">
            <el-form-item label="知识库 ID">
              <el-select
                v-model="selectedNode.properties.kbId"
                clearable
                filterable
                placeholder="留空使用默认全局知识库"
                style="width: 100%"
                @change="syncSelectedNode"
              >
                <el-option
                  v-for="item in knowledgeBaseOptions"
                  :key="item.id"
                  :label="`${item.id} · ${item.kbName}`"
                  :value="item.id"
                />
                <template #empty>
                  <span class="select-empty">暂无知识库，请先到知识库页面创建</span>
                </template>
              </el-select>
              <div class="form-tip">只能选择已有知识库；清空后走默认全局知识库。</div>
            </el-form-item>
            <el-form-item label="检索片段数 (topK)">
              <el-input-number
                v-model="selectedNode.properties.topK"
                :min="1" :max="10"
                style="width: 100%"
                @change="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="问题变量">
              <el-input
                v-model="selectedNode.properties.questionVar"
                placeholder="lastAsr"
                @input="syncSelectedNode"
              />
            </el-form-item>
            <el-form-item label="答案变量">
              <el-input
                v-model="selectedNode.properties.answerVar"
                placeholder="ragAnswer"
                @input="syncSelectedNode"
              />
              <div class="form-tip">答案写入此变量，后续 play 节点可用 <code>{{ '${' + (selectedNode.properties.answerVar || 'ragAnswer') + '}' }}</code> 引用。</div>
            </el-form-item>
            <el-form-item label="未命中分支">
              <el-input
                v-model="selectedNode.properties.fallbackBranch"
                placeholder="例如：fallback"
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
          <el-form-item label="分支标识">
            <el-input
              v-model="selectedEdge.properties.key"
              maxlength="16"
              placeholder="例如：1 / true / fallback / 查询账单"
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

    <el-dialog v-model="aiDialog.visible" title="AI 生成流程" width="720px">
      <el-form label-position="top">
        <el-form-item label="业务描述">
          <el-input
            v-model="aiDialog.requirement"
            type="textarea"
            :rows="6"
            maxlength="1200"
            show-word-limit
            placeholder="例如：生成一个售后热线流程，先播放欢迎语，按 1 查询订单走 AI 问答，按 2 退款转人工 1001，未命中走兜底提示。"
          />
        </el-form-item>
      </el-form>

      <div v-if="aiDialog.summary || aiDialog.warnings.length || aiDialog.validationErrors.length" class="ai-result">
        <div v-if="aiDialog.summary" class="ai-summary">{{ aiDialog.summary }}</div>
        <el-alert
          v-if="aiDialog.warnings.length"
          type="warning"
          :closable="false"
          show-icon
          class="ai-alert"
        >
          <template #title>{{ aiDialog.warnings.join('；') }}</template>
        </el-alert>
        <el-alert
          v-if="aiDialog.validationErrors.length"
          type="error"
          :closable="false"
          show-icon
          class="ai-alert"
        >
          <template #title>{{ aiDialog.validationErrors.join('；') }}</template>
        </el-alert>
      </div>

      <template #footer>
        <el-button @click="aiDialog.visible = false">关闭</el-button>
        <el-button type="primary" :loading="aiDialog.loading" @click="generateAiFlow">生成到画布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createFlow,
  generateFlowByAi,
  getFlow,
  publishFlow,
  sendFlowDebugInput,
  startFlowDebug,
  updateFlow
} from '@/api/flow'
import type { FlowDebugResponse } from '@/api/flow'
import { listKnowledgeBaseOptions } from '@/api/knowledge'
import type { KnowledgeBaseOption } from '@/api/knowledge'
import { useUserStore } from '@/stores/user'
import {
  ChevronLeft, Save, Upload, MousePointerClick,
  Play, Hash, GitBranch, PhoneForwarded, Mail,
  Globe, Variable, Mic, Brain, BookOpen,
  PlayCircle, CircleStop, Trash2, RotateCcw, Send, X, Sparkles
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
const knowledgeBaseOptions = ref<KnowledgeBaseOption[]>([])
const saving = ref(false)
const publishing = ref(false)
const dirty = ref(false)
const flowId = ref<string>(String(route.params.id || 'new'))
const flowForm = reactive({
  flowCode: '',
  flowName: '未命名流程',
  description: '',
  status: 0,
  currentVersion: 0,
  hasDraftDiff: false
})
const debugPanel = reactive({
  visible: false,
  loading: false,
  sending: false,
  caller: '13800000000',
  callee: '4001',
  input: '',
  response: null as FlowDebugResponse | null,
  messages: [] as { type: 'event' | 'prompt' | 'input' | 'result'; text: string }[]
})
const aiDialog = reactive({
  visible: false,
  loading: false,
  requirement: '',
  summary: '',
  warnings: [] as string[],
  validationErrors: [] as string[]
})
const debugStyledNodeIds = new Set<string>()
const debugStyledEdgeIds = new Set<string>()
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
const canDebug = computed(() => userStore.hasPerm('flow:list') && (flowId.value !== 'new' || canSave.value))
const canAiGenerate = computed(() => canSave.value && userStore.hasAnyPerm(['flow:add', 'flow:edit']))
const statusText = computed(() => {
  if (flowForm.status === 1) return `已发布 v${flowForm.currentVersion || 1}`
  if (flowForm.status === 2) return '已下线'
  return '草稿'
})
const draftDiffText = computed(() => {
  if (flowForm.currentVersion > 0 && flowForm.hasDraftDiff) {
    return '草稿有未发布修改'
  }
  return ''
})
const debugStatusLabel = computed(() => {
  const status = debugPanel.response?.status
  if (status === 'ended') return '已结束'
  if (status === 'waiting') {
    return debugPanel.response?.waitingFor === 'asr' ? '等待语音输入' : '等待按键'
  }
  return debugPanel.response ? '运行中' : '未开始'
})
const debugWaitingHint = computed(() => {
  if (debugPanel.response?.status !== 'waiting') return ''
  return debugPanel.response?.waitingFor === 'asr'
    ? '输入模拟语音文本继续'
    : '输入按键或点击分支选项'
})
const debugInputPlaceholder = computed(() => {
  if (debugPanel.response?.waitingFor === 'asr') {
    return '输入模拟语音文本，如「我想查账单」'
  }
  return '输入按键，例如 1'
})
const debugVariables = computed(() => {
  const vars = debugPanel.response?.variables || {}
  return Object.entries(vars)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => ({
      key,
      value: String(value ?? '')
    }))
})

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)

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

  await loadKnowledgeBaseOptions()
  await loadFlow()
})

onBeforeRouteLeave(() => confirmLeaveIfDirty())

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  clearDebugHighlight()
  lf = null
})

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
  flowForm.hasDraftDiff = detail.hasDraftDiff
  renderGraph(detail.graphJson)
  dirty.value = false
}

async function loadKnowledgeBaseOptions() {
  if (!userStore.hasPerm('kb:base:list')) {
    knowledgeBaseOptions.value = []
    return
  }
  knowledgeBaseOptions.value = await listKnowledgeBaseOptions()
}

async function onSave() {
  await saveFlow(false)
}

async function saveFlow(silent = false) {
  if (!lf) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (flowId.value === 'new') {
      const id = await createFlow(payload)
      flowId.value = String(id)
      dirty.value = false
      await router.replace(`/flow/editor/${id}`)
    } else {
      await updateFlow(flowId.value, payload)
      if (flowForm.currentVersion > 0) {
        flowForm.hasDraftDiff = true
      }
      dirty.value = false
    }
    if (!silent) {
      ElMessage.success('保存成功')
    }
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
    flowForm.hasDraftDiff = false
    ElMessage.success('发布成功')
  } finally {
    publishing.value = false
  }
}

function openAiGenerate() {
  aiDialog.visible = true
  aiDialog.summary = ''
  aiDialog.warnings = []
  aiDialog.validationErrors = []
}

async function generateAiFlow() {
  const requirement = aiDialog.requirement.trim()
  if (!requirement) {
    ElMessage.warning('请先输入业务描述')
    return
  }
  if (dirty.value && hasCanvasContent()) {
    try {
      await ElMessageBox.confirm(
        'AI 生成会替换当前画布内容，当前未保存修改可能丢失。确定继续吗？',
        '确认生成',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }

  aiDialog.loading = true
  try {
    const res = await generateFlowByAi({
      requirement,
      flowName: flowForm.flowName
    })
    aiDialog.summary = res.summary || ''
    aiDialog.warnings = res.warnings || []
    aiDialog.validationErrors = res.validationErrors || []
    debugPanel.response = null
    debugPanel.messages = []
    renderGraph(res.graphJson)
    dirty.value = true
    if (flowForm.currentVersion > 0) {
      flowForm.hasDraftDiff = true
    }
    if (aiDialog.validationErrors.length > 0) {
      ElMessage.warning('已生成到画布，但还有校验问题，请调整后再保存')
    } else {
      ElMessage.success('已生成到画布，检查后保存为草稿')
    }
  } finally {
    aiDialog.loading = false
  }
}

async function startEditorDebug() {
  if (!lf || !canDebug.value) return
  if (dirty.value || flowId.value === 'new') {
    await saveFlow(true)
  }
  debugPanel.visible = true
  debugPanel.loading = true
  debugPanel.input = ''
  debugPanel.messages = []
  clearDebugHighlight()
  try {
    const res = await startFlowDebug(flowId.value, {
      caller: debugPanel.caller,
      callee: debugPanel.callee
    })
    applyEditorDebugResponse(res)
  } finally {
    debugPanel.loading = false
  }
}

async function sendEditorDebugInput(value?: string) {
  const input = (value || debugPanel.input).trim()
  if (!debugPanel.response?.sessionId || !input || debugPanel.response.status === 'ended') return
  debugPanel.sending = true
  debugPanel.messages.push({ type: 'input', text: input })
  debugPanel.input = ''
  try {
    const res = await sendFlowDebugInput(debugPanel.response.sessionId, input)
    applyEditorDebugResponse(res)
  } finally {
    debugPanel.sending = false
  }
}

function closeEditorDebug() {
  debugPanel.visible = false
  clearDebugHighlight()
}

function applyEditorDebugResponse(res: FlowDebugResponse) {
  debugPanel.response = res
  res.events.forEach((text) => debugPanel.messages.push({ type: 'event', text }))
  res.prompts.forEach((text) => debugPanel.messages.push({ type: 'prompt', text }))
  if (res.status === 'ended' && res.result) {
    debugPanel.messages.push({ type: 'result', text: res.result })
  }
  highlightDebugPath(res)
}

function hasCanvasContent() {
  if (!lf) return false
  const graph = lf.getGraphData()
  return (Array.isArray(graph.nodes) && graph.nodes.length > 0)
    || (Array.isArray(graph.edges) && graph.edges.length > 0)
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
  clearDebugHighlight()
  try {
    lf.render(graphJson ? JSON.parse(graphJson) : emptyGraph())
  } catch {
    lf.render(emptyGraph())
  }
  clearSelection()
}

function highlightDebugPath(res: FlowDebugResponse) {
  if (!lf) return
  clearDebugHighlight()
  const visitedNodeIds = (res.visitedNodeIds?.length ? res.visitedNodeIds : [res.currentNodeId]).filter(Boolean)
  const visitedSet = new Set(visitedNodeIds)
  visitedSet.forEach((nodeId) => styleDebugNode(nodeId, false))
  deriveVisitedEdgeIds(visitedNodeIds).forEach(styleDebugEdge)
  if (res.currentNodeId) {
    styleDebugNode(res.currentNodeId, true)
    try {
      lf.focusOn(res.currentNodeId)
    } catch {
      // ignore focus failures from stale node ids
    }
  }
}

function clearDebugHighlight() {
  if (!lf) return
  debugStyledNodeIds.forEach((nodeId) => {
    const model = lf.getNodeModelById(nodeId)
    if (model) {
      model.updateStyles({})
      model.setZIndex(1)
    }
  })
  debugStyledEdgeIds.forEach((edgeId) => {
    const model = lf.getEdgeModelById(edgeId)
    if (model) {
      model.updateStyles({})
      model.setZIndex(1)
    }
  })
  debugStyledNodeIds.clear()
  debugStyledEdgeIds.clear()
}

function styleDebugNode(nodeId: string, current: boolean) {
  const model = lf?.getNodeModelById(nodeId)
  if (!model) return
  model.setStyles(current
    ? { stroke: '#f59e0b', strokeWidth: 3, fill: '#fffbeb' }
    : { stroke: '#0f766e', strokeWidth: 2, fill: '#f0fdfa' })
  model.setZIndex(current ? 999 : 998)
  debugStyledNodeIds.add(nodeId)
}

function styleDebugEdge(edgeId: string) {
  const model = lf?.getEdgeModelById(edgeId)
  if (!model) return
  model.setStyles({ stroke: '#0f766e', strokeWidth: 3 })
  model.setZIndex(997)
  debugStyledEdgeIds.add(edgeId)
}

function deriveVisitedEdgeIds(visitedNodeIds: string[]) {
  if (!lf || visitedNodeIds.length < 2) return []
  const graph = lf.getGraphData()
  const edges = Array.isArray(graph.edges) ? graph.edges : []
  const ids: string[] = []
  for (let index = 0; index < visitedNodeIds.length - 1; index++) {
    const sourceNodeId = visitedNodeIds[index]
    const targetNodeId = visitedNodeIds[index + 1]
    edges
      .filter((edge: any) => edge.sourceNodeId === sourceNodeId && edge.targetNodeId === targetNodeId)
      .forEach((edge: any) => {
        if (edge.id) ids.push(String(edge.id))
      })
  }
  return Array.from(new Set(ids))
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
  if (item.type === 'condition') {
    base.expression = "lastDtmf == '1' ? 'yes' : 'no'"
  }
  if (item.type === 'var_assign') {
    base.varName = 'customerLevel'
    base.value = 'normal'
  }
  if (item.type === 'http') {
    base.method = 'GET'
    base.url = ''
    base.timeoutSec = 5
    base.bodyTemplate = ''
    base.responseVar = 'httpResponse'
    base.statusVar = 'httpStatus'
    base.fallbackBranch = 'fallback'
  }
  if (item.type === 'transfer') base.target = '1000'
  if (item.type === 'voicemail') {
    base.maxSeconds = 60
    base.filePath = ''
  }
  if (item.type === 'asr') {
    base.maxSeconds = 8
    base.language = 'zh-CN'
    base.prompt = '请说出您的问题'
  }
  if (item.type === 'intent') {
    base.inputVar = 'lastAsr'
    base.intents = []
    base.fallbackBranch = 'other'
  }
  if (item.type === 'rag') {
    base.topK = 3
    base.questionVar = 'lastAsr'
    base.answerVar = 'ragAnswer'
    base.fallbackBranch = 'fallback'
  }
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
  if (debugPanel.response) {
    clearDebugHighlight()
  }
}

function messageLabel(type: 'event' | 'prompt' | 'input' | 'result') {
  const labels = {
    event: '系统',
    prompt: '语音',
    input: '输入',
    result: '结果'
  }
  return labels[type]
}

function confirmLeaveIfDirty() {
  if (!dirty.value) {
    return true
  }
  return ElMessageBox.confirm(
    '当前流程还有未保存的修改，离开后这些修改会丢失。确定离开吗？',
    '未保存修改',
    {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
      distinguishCancelAndClose: true
    }
  )
    .then(() => true)
    .catch(() => false)
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
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
    if (properties.bizType === 'rag' && !hasValue(properties.kbId)) {
      delete properties.kbId
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

  nodes.forEach((node: any) => {
    const type = bizType(node)
    const outgoingEdges = outgoing(edges, node.id)

    if (!supportedNodeTypes().includes(type)) {
      errors.push(`节点「${nodeName(node)}」类型暂不支持：${type}`)
      return
    }
    if (!isTerminalNode(type) && outgoingEdges.length === 0) {
      errors.push(`节点「${nodeName(node)}」必须连接后续节点`)
    }

    validateNodeConfig(node, outgoingEdges, errors)
    validateDuplicateBranches(node, outgoingEdges, errors)
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

function supportedNodeTypes() {
  return [
    'start', 'end', 'play', 'dtmf', 'condition', 'var_assign',
    'http', 'transfer', 'voicemail', 'asr', 'intent', 'rag'
  ]
}

function isTerminalNode(type: string) {
  return ['end', 'transfer', 'voicemail'].includes(type)
}

function validateNodeConfig(node: any, outgoingEdges: any[], errors: string[]) {
  const type = bizType(node)
  const props = node.properties || {}
  if (type === 'play' && !hasValue(props.ttsText) && !hasValue(props.audioUrl)) {
    errors.push(`播放节点「${nodeName(node)}」需要配置播放文本或音频地址`)
  }
  if (type === 'transfer' && !hasValue(props.target)) {
    errors.push(`转人工节点「${nodeName(node)}」需要配置坐席号`)
  }
  if (type === 'condition' && !hasValue(props.expression)) {
    errors.push(`条件节点「${nodeName(node)}」需要配置判断表达式`)
  }
  if (type === 'var_assign' && !hasValue(props.varName)) {
    errors.push(`变量赋值节点「${nodeName(node)}」需要配置变量名`)
  }
  if (type === 'http') {
    if (!hasValue(props.url)) {
      errors.push(`HTTP 节点「${nodeName(node)}」需要配置请求 URL`)
    }
    if (!hasDefaultBranch(outgoingEdges)) {
      errors.push(`HTTP 节点「${nodeName(node)}」需要一条默认成功连线`)
    }
    requireBranch(node, outgoingEdges, props.fallbackBranch || 'fallback', errors)
  }
  if (type === 'dtmf') {
    const keys = outgoingEdges.map(edgeBranchKey).filter(Boolean)
    if (keys.length !== outgoingEdges.length) {
      errors.push(`按键节点「${nodeName(node)}」的每条连线都需要填写分支按键`)
    }
  }
  if (type === 'intent') {
    const intents = Array.isArray(props.intents) ? props.intents.filter(hasValue) : []
    if (intents.length === 0) {
      errors.push(`AI 意图节点「${nodeName(node)}」需要配置候选意图`)
    }
    intents.forEach((intent: string) => requireBranch(node, outgoingEdges, intent, errors))
    requireBranch(node, outgoingEdges, props.fallbackBranch || 'other', errors)
  }
  if (type === 'rag') {
    if (!hasDefaultBranch(outgoingEdges)) {
      errors.push(`AI 问答节点「${nodeName(node)}」需要一条默认成功连线`)
    }
    requireBranch(node, outgoingEdges, props.fallbackBranch || 'fallback', errors)
  }
}

function validateDuplicateBranches(node: any, outgoingEdges: any[], errors: string[]) {
  const keys = outgoingEdges.map(edgeBranchKey).filter(Boolean)
  if (new Set(keys).size !== keys.length) {
    errors.push(`节点「${nodeName(node)}」存在重复分支标识`)
  }
}

function requireBranch(node: any, outgoingEdges: any[], branchKey: string, errors: string[]) {
  if (!hasValue(branchKey)) return
  if (!outgoingEdges.some((edge) => edgeBranchKey(edge) === branchKey)) {
    errors.push(`节点「${nodeName(node)}」缺少分支连线：${branchKey}`)
  }
}

function hasDefaultBranch(outgoingEdges: any[]) {
  return outgoingEdges.some((edge) => !edgeBranchKey(edge))
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

function hasValue(value: any) {
  return value !== null && value !== undefined && String(value).trim() !== ''
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
  position: relative;
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
.flow-diff {
  color: var(--color-warning);
}
.canvas { flex: 1; overflow: hidden; }
.debug-dock {
  position: absolute;
  right: var(--space-4);
  bottom: var(--space-4);
  width: min(720px, calc(100% - var(--space-8)));
  max-height: min(520px, calc(100% - var(--space-8)));
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg);
  z-index: 20;
}
.debug-dock-head,
.debug-actions,
.debug-current-row,
.debug-input {
  display: flex;
  align-items: center;
}
.debug-dock-head {
  justify-content: space-between;
  gap: var(--space-3);
}
.debug-actions {
  gap: var(--space-1);
  :deep(.el-button) {
    margin-left: 0;
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
.debug-waiting {
  color: var(--color-primary);
}
.debug-call-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
}
.debug-current-row {
  display: grid;
  grid-template-columns: 1fr 1fr 96px;
  gap: var(--space-2);
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  div {
    min-width: 0;
  }
  span {
    display: block;
    margin-bottom: 2px;
    font-size: var(--text-xs);
    color: var(--color-text-subtle);
  }
  strong,
  code {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--text-xs);
    color: var(--color-text);
  }
}
.debug-dock-body {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(220px, 0.8fr);
  gap: var(--space-2);
}
.debug-log {
  min-height: 180px;
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  padding: var(--space-2);
}
.debug-message {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  font-size: var(--text-xs);
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
  flex: 0 0 30px;
  color: var(--color-text-subtle);
}
.debug-vars {
  min-width: 0;
}
.debug-section-title {
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
.debug-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  :deep(.el-button) {
    margin-left: 0;
  }
}
.debug-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  gap: var(--space-2);
}
.code-chip {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  background: var(--color-neutral-100);
  color: var(--color-neutral-700);
}
.ai-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.ai-summary {
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  color: var(--color-text);
  font-size: var(--text-sm);
}
.ai-alert {
  margin: 0;
}

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
.form-tip {
  margin-top: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
  line-height: 1.5;
  code {
    font-family: var(--font-mono, monospace);
    padding: 1px 4px;
    background: var(--color-neutral-100);
    border-radius: 4px;
    color: var(--color-text);
  }
}
.select-empty {
  display: block;
  padding: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
}
</style>
