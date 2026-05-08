<template>
  <div class="page-container rag-eval-page">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>RAG 评估</h2>
          <p class="panel-sub">维护测试问题，批量验证知识库命中与回答质量</p>
        </div>
        <div class="head-actions">
          <el-button @click="reloadAll">刷新</el-button>
          <el-button type="primary" @click="openCreateCase">
            <Plus :size="14" :stroke-width="2" style="margin-right: 4px" />
            新增用例
          </el-button>
        </div>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-select v-model="filters.kbId" clearable placeholder="全部知识库" style="width: 220px" @change="reloadAll">
            <el-option v-for="item in baseOptions" :key="item.id" :label="`${item.id} · ${item.kbName}`" :value="item.id" />
          </el-select>
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索问题 / 文档 / 关键词"
            style="max-width: 320px"
            @keyup.enter="reloadCases"
            @clear="reloadCases"
          />
          <el-button @click="reloadCases">查询</el-button>
          <div class="run-controls">
            <el-input-number v-model="runForm.topK" :min="1" :max="10" controls-position="right" />
            <el-switch v-model="runForm.generateAnswer" active-text="生成回答" inactive-text="只检索" />
            <el-button type="success" :loading="running" @click="startRun">
              <Play :size="14" :stroke-width="2" style="margin-right: 4px" />
              开始评估
            </el-button>
          </div>
        </div>

        <el-table :data="cases" v-loading="caseLoading" border stripe>
          <el-table-column prop="question" label="测试问题" min-width="240" show-overflow-tooltip />
          <el-table-column label="知识库" width="160">
            <template #default="{ row }">
              {{ row.kbId }} · {{ row.kbName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="expectedDocTitle" label="期望文档" width="180" show-overflow-tooltip />
          <el-table-column prop="expectedKeywords" label="期望关键词" min-width="180" show-overflow-tooltip />
          <el-table-column label="Fallback" width="100">
            <template #default="{ row }">
              <el-tag :type="row.shouldFallback ? 'warning' : 'info'" effect="plain" size="small">
                {{ row.shouldFallback ? '期望兜底' : '期望命中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain" size="small">
                {{ row.enabled ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text @click="openEditCase(row)">编辑</el-button>
                <el-button size="small" text type="danger" @click="onDeleteCase(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="casePagination.current"
            v-model:page-size="casePagination.size"
            :total="casePagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="fetchCases"
            @size-change="fetchCases"
          />
        </div>
      </div>
    </section>

    <section class="panel">
      <header class="panel-head compact">
        <div>
          <h2>评估批次</h2>
          <p class="panel-sub">查看每次批量评估的通过率与失败原因</p>
        </div>
      </header>

      <div class="panel-body">
        <el-table :data="runs" v-loading="runLoading" border stripe>
          <el-table-column prop="id" label="批次" width="90" />
          <el-table-column label="知识库" width="160">
            <template #default="{ row }">
              {{ row.kbId }} · {{ row.kbName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="topK" label="TopK" width="80" />
          <el-table-column label="模式" width="100">
            <template #default="{ row }">
              <el-tag :type="row.generateAnswer ? 'success' : 'info'" effect="plain" size="small">
                {{ row.generateAnswer ? '生成回答' : '只检索' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="通过" width="130">
            <template #default="{ row }">
              <span class="num strong">{{ row.passedCount }}</span>
              <span class="muted"> / {{ row.totalCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="整体通过率" width="130">
            <template #default="{ row }">
              <el-progress :percentage="row.passRate || 0" :stroke-width="8" />
            </template>
          </el-table-column>
          <el-table-column label="文档命中" width="120">
            <template #default="{ row }">{{ row.hitRate || 0 }}%</template>
          </el-table-column>
          <el-table-column label="关键词" width="120">
            <template #default="{ row }">{{ row.keywordPassRate || 0 }}%</template>
          </el-table-column>
          <el-table-column label="Fallback" width="120">
            <template #default="{ row }">{{ row.fallbackPassRate || 0 }}%</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="运行时间" width="170" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openResults(row)">结果</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="runPagination.current"
            v-model:page-size="runPagination.size"
            :total="runPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="fetchRuns"
            @size-change="fetchRuns"
          />
        </div>
      </div>
    </section>

    <el-dialog v-model="caseDialog.visible" :title="caseDialog.form.id ? '编辑评估用例' : '新增评估用例'" width="760px">
      <el-form label-position="top" :model="caseDialog.form">
        <el-form-item label="知识库" required>
          <el-select v-model="caseDialog.form.kbId" placeholder="请选择知识库" style="width: 100%">
            <el-option v-for="item in baseOptions" :key="item.id" :label="`${item.id} · ${item.kbName}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试问题" required>
          <el-input
            v-model="caseDialog.form.question"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="例如：退款多久到账？"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="期望文档标题">
            <el-input v-model="caseDialog.form.expectedDocTitle" maxlength="255" placeholder="例如：退款规则" />
          </el-form-item>
          <el-form-item label="是否期望兜底">
            <el-switch v-model="caseDialog.form.shouldFallback" active-text="是" inactive-text="否" />
          </el-form-item>
        </div>
        <el-form-item label="期望回答关键词">
          <el-input
            v-model="caseDialog.form.expectedKeywords"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="多个关键词可用逗号或换行分隔，例如：1-3个工作日，原路退回"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="caseDialog.form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="caseDialog.saving" @click="submitCase">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialog.visible" :title="`评估结果 #${resultDialog.runId || ''}`" width="1080px">
      <el-table :data="results" v-loading="resultDialog.loading" border stripe max-height="520">
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'" effect="plain" size="small">
              {{ row.passed ? '通过' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
        <el-table-column label="命中文档" width="100">
          <template #default="{ row }">
            <Check v-if="row.hitExpectedDoc" class="state-icon success" :size="16" />
            <X v-else class="state-icon danger" :size="16" />
          </template>
        </el-table-column>
        <el-table-column label="关键词" width="100">
          <template #default="{ row }">
            <Check v-if="row.keywordPassed" class="state-icon success" :size="16" />
            <X v-else class="state-icon danger" :size="16" />
          </template>
        </el-table-column>
        <el-table-column label="Fallback" width="100">
          <template #default="{ row }">
            <Check v-if="row.fallbackPassed" class="state-icon success" :size="16" />
            <X v-else class="state-icon danger" :size="16" />
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answer" label="模型回答" min-width="260" show-overflow-tooltip />
        <el-table-column label="切片" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openChunks(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="chunkDialog.visible" title="命中切片" width="820px">
      <pre class="chunk-json">{{ chunkDialog.content }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Play, Plus, X } from 'lucide-vue-next'
import {
  createRagEvalCase,
  deleteRagEvalCase,
  listKnowledgeBaseOptions,
  listRagEvalResults,
  pageRagEvalCases,
  pageRagEvalRuns,
  runRagEval,
  updateRagEvalCase
} from '@/api/knowledge'
import type { KnowledgeBaseOption, RagEvalCase, RagEvalResult, RagEvalRun } from '@/api/knowledge'

const baseOptions = ref<KnowledgeBaseOption[]>([])
const filters = reactive({ kbId: '' as number | '', keyword: '' })

const cases = ref<RagEvalCase[]>([])
const caseLoading = ref(false)
const casePagination = reactive({ current: 1, size: 10, total: 0 })

const runs = ref<RagEvalRun[]>([])
const runLoading = ref(false)
const runPagination = reactive({ current: 1, size: 10, total: 0 })

const running = ref(false)
const runForm = reactive({ topK: 3, generateAnswer: true })

const caseDialog = reactive({
  visible: false,
  saving: false,
  form: {
    id: 0,
    kbId: undefined as number | undefined,
    question: '',
    expectedDocTitle: '',
    expectedKeywords: '',
    shouldFallback: false,
    enabled: true
  }
})

const resultDialog = reactive({
  visible: false,
  loading: false,
  runId: 0
})
const results = ref<RagEvalResult[]>([])
const chunkDialog = reactive({ visible: false, content: '' })

async function fetchOptions() {
  baseOptions.value = await listKnowledgeBaseOptions()
}

async function fetchCases() {
  caseLoading.value = true
  try {
    const res = await pageRagEvalCases({
      current: casePagination.current,
      size: casePagination.size,
      kbId: filters.kbId,
      keyword: filters.keyword
    })
    cases.value = res.records
    casePagination.total = res.total
  } finally {
    caseLoading.value = false
  }
}

async function fetchRuns() {
  runLoading.value = true
  try {
    const res = await pageRagEvalRuns({
      current: runPagination.current,
      size: runPagination.size,
      kbId: filters.kbId
    })
    runs.value = res.records
    runPagination.total = res.total
  } finally {
    runLoading.value = false
  }
}

async function reloadCases() {
  casePagination.current = 1
  await fetchCases()
}

async function reloadAll() {
  casePagination.current = 1
  runPagination.current = 1
  await Promise.all([fetchCases(), fetchRuns()])
}

function openCreateCase() {
  caseDialog.form = {
    id: 0,
    kbId: filters.kbId || baseOptions.value[0]?.id,
    question: '',
    expectedDocTitle: '',
    expectedKeywords: '',
    shouldFallback: false,
    enabled: true
  }
  caseDialog.visible = true
}

function openEditCase(row: RagEvalCase) {
  caseDialog.form = {
    id: row.id,
    kbId: row.kbId,
    question: row.question,
    expectedDocTitle: row.expectedDocTitle || '',
    expectedKeywords: row.expectedKeywords || '',
    shouldFallback: row.shouldFallback,
    enabled: row.enabled
  }
  caseDialog.visible = true
}

async function submitCase() {
  if (!caseDialog.form.kbId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!caseDialog.form.question.trim()) {
    ElMessage.warning('请填写测试问题')
    return
  }

  caseDialog.saving = true
  try {
    const payload = {
      kbId: caseDialog.form.kbId,
      question: caseDialog.form.question,
      expectedDocTitle: caseDialog.form.expectedDocTitle,
      expectedKeywords: caseDialog.form.expectedKeywords,
      shouldFallback: caseDialog.form.shouldFallback,
      enabled: caseDialog.form.enabled
    }
    if (caseDialog.form.id) {
      await updateRagEvalCase(caseDialog.form.id, payload)
    } else {
      await createRagEvalCase(payload)
    }
    caseDialog.visible = false
    ElMessage.success('保存成功')
    await fetchCases()
  } finally {
    caseDialog.saving = false
  }
}

async function onDeleteCase(row: RagEvalCase) {
  await ElMessageBox.confirm(`确定删除这个评估用例吗？`, '确认删除', { type: 'warning' })
  await deleteRagEvalCase(row.id)
  ElMessage.success('删除成功')
  await fetchCases()
}

async function startRun() {
  const kbId = filters.kbId
  if (!kbId) {
    ElMessage.warning('请选择要评估的知识库')
    return
  }
  running.value = true
  try {
    const runId = await runRagEval({
      kbId,
      topK: runForm.topK,
      generateAnswer: runForm.generateAnswer
    })
    ElMessage.success('评估完成')
    runPagination.current = 1
    await fetchRuns()
    await openResults({ id: runId } as RagEvalRun)
  } finally {
    running.value = false
  }
}

async function openResults(row: Pick<RagEvalRun, 'id'>) {
  resultDialog.runId = row.id
  resultDialog.visible = true
  resultDialog.loading = true
  try {
    results.value = await listRagEvalResults(row.id)
  } finally {
    resultDialog.loading = false
  }
}

function openChunks(row: RagEvalResult) {
  chunkDialog.content = formatChunks(row.retrievedChunks)
  chunkDialog.visible = true
}

function formatChunks(value: string) {
  if (!value) {
    return '[]'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(async () => {
  await fetchOptions()
  await reloadAll()
})
</script>

<style scoped lang="scss">
.rag-eval-page {
  display: grid;
  gap: var(--space-4);
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
    font-size: var(--text-xs);
    color: var(--color-text-muted);
  }

  &.compact {
    align-items: center;
  }
}

.head-actions,
.table-tools,
.run-controls,
.action-list {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.table-tools {
  flex-wrap: wrap;
  margin-bottom: var(--space-3);
}

.run-controls {
  margin-left: auto;
}

.panel-body {
  padding: var(--space-4);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 180px;
  gap: var(--space-3);
}

.action-list {
  white-space: nowrap;

  :deep(.el-button) {
    margin-left: 0;
  }
}

.num {
  font-variant-numeric: tabular-nums;
}

.strong {
  font-weight: var(--weight-semibold);
  color: var(--color-text);
}

.muted {
  color: var(--color-text-muted);
}

.state-icon {
  display: inline-flex;
  vertical-align: middle;

  &.success {
    color: var(--color-success);
  }

  &.danger {
    color: var(--color-error);
  }
}

.chunk-json {
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

@media (max-width: 960px) {
  .panel-head,
  .head-actions,
  .run-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .run-controls {
    width: 100%;
    margin-left: 0;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
