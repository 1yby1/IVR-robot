<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>知识文档</h2>
          <p class="panel-sub">录入文档后自动生成 RAG 检索切片</p>
        </div>
        <div class="head-actions">
          <input
            ref="fileInputRef"
            class="hidden-file"
            type="file"
            accept=".txt,.md,.markdown,.csv,.json,.log,.xml,.html,.htm,.docx"
            @change="onFileChange"
          />
          <el-button :loading="fileParsing" @click="triggerUpload">
            <Upload :size="14" :stroke-width="2" style="margin-right: 4px" />
            上传解析
          </el-button>
          <el-button type="primary" @click="openCreate">
            <Plus :size="14" :stroke-width="2" style="margin-right: 4px" />
            新建文档
          </el-button>
        </div>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-select v-model="filters.kbId" clearable placeholder="全部知识库" style="width: 220px" @change="reload">
            <el-option v-for="item in baseOptions" :key="item.id" :label="`${item.id} · ${item.kbName}`" :value="item.id" />
          </el-select>
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索标题 / 内容"
            style="max-width: 320px"
            @keyup.enter="reload"
            @clear="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="kbName" label="知识库" width="150">
            <template #default="{ row }">
              {{ row.kbId }} · {{ row.kbName }}
            </template>
          </el-table-column>
          <el-table-column prop="sourceFile" label="来源" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="plain" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="切片" width="80" />
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text @click="openEdit(row)">编辑</el-button>
                <el-button size="small" text type="primary" @click="previewRowChunks(row)">切片预览</el-button>
                <el-button size="small" text type="primary" @click="onReindex(row)">重建索引</el-button>
                <el-button size="small" text type="danger" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑文档' : '新建文档'" width="760px">
      <el-form label-position="top" :model="dialog.form">
        <div class="form-grid">
          <el-form-item label="知识库" required>
            <el-select v-model="dialog.form.kbId" placeholder="请选择知识库" style="width: 100%">
              <el-option v-for="item in baseOptions" :key="item.id" :label="`${item.id} · ${item.kbName}`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源文件">
            <el-input v-model="dialog.form.sourceFile" placeholder="例如 faq.txt" />
          </el-form-item>
        </div>
        <el-form-item label="标题" required>
          <el-input v-model="dialog.form.title" maxlength="255" placeholder="例如：售后退款规则" />
        </el-form-item>
          <el-form-item label="内容" required>
            <el-input
              v-model="dialog.form.content"
              type="textarea"
              :rows="14"
              maxlength="200000"
              show-word-limit
              placeholder="粘贴 FAQ、制度、业务说明等文本。建议一条规则一行，系统会自动切片。"
            />
          </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button :loading="chunkDialog.loading" @click="previewCurrentChunks">
          <Scissors :size="14" :stroke-width="2" style="margin-right: 4px" />
          预览切片
        </el-button>
        <el-button type="primary" :loading="dialog.saving" @click="onSubmit">保存并索引</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chunkDialog.visible" :title="chunkDialog.title" width="920px">
      <div v-if="chunkDialog.data" class="chunk-summary">
        <span>切片 {{ chunkDialog.data.totalCount || 0 }}</span>
        <span>字符 {{ chunkDialog.data.totalChars || 0 }}</span>
        <span>Token {{ chunkDialog.data.totalTokens || 0 }}</span>
      </div>
      <el-table
        :data="chunkDialog.data?.chunks || []"
        v-loading="chunkDialog.loading"
        border
        stripe
        max-height="520"
        empty-text="暂无切片"
      >
        <el-table-column prop="index" label="#" width="70" />
        <el-table-column prop="charCount" label="字符" width="90" />
        <el-table-column prop="tokenCount" label="Token" width="90" />
        <el-table-column prop="content" label="切片内容" min-width="520" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Scissors, Upload } from 'lucide-vue-next'
import {
  createKnowledgeDoc,
  deleteKnowledgeDoc,
  getKnowledgeDoc,
  listKnowledgeBaseOptions,
  pageKnowledgeDocs,
  parseKnowledgeDocFile,
  previewKnowledgeDocChunks,
  reindexKnowledgeDoc,
  updateKnowledgeDoc,
  KB_DOC_STATUS
} from '@/api/knowledge'
import type { KnowledgeBaseOption, KnowledgeChunkPreviewResponse, KnowledgeDoc } from '@/api/knowledge'

const list = ref<KnowledgeDoc[]>([])
const baseOptions = ref<KnowledgeBaseOption[]>([])
const loading = ref(false)
const fileParsing = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const filters = reactive({ kbId: '' as number | '', keyword: '' })
const pagination = ref({ current: 1, size: 10, total: 0 })
const dialog = reactive({
  visible: false,
  saving: false,
  form: {
    id: 0,
    kbId: undefined as number | undefined,
    title: '',
    content: '',
    sourceFile: '',
    fileType: 'txt'
  }
})
const chunkDialog = reactive({
  visible: false,
  loading: false,
  title: '切片预览',
  data: null as KnowledgeChunkPreviewResponse | null
})

async function fetchOptions() {
  baseOptions.value = await listKnowledgeBaseOptions()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageKnowledgeDocs({
      current: pagination.value.current,
      size: pagination.value.size,
      kbId: filters.kbId,
      keyword: filters.keyword
    })
    list.value = res.records
    pagination.value.total = res.total
  } finally {
    loading.value = false
  }
}

async function reload() {
  pagination.value.current = 1
  await fetchList()
}

function openCreate() {
  dialog.form = {
    id: 0,
    kbId: filters.kbId || baseOptions.value[0]?.id,
    title: '',
    content: '',
    sourceFile: '',
    fileType: 'txt'
  }
  dialog.visible = true
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  fileParsing.value = true
  try {
    const parsed = await parseKnowledgeDocFile(file)
    dialog.form = {
      id: 0,
      kbId: filters.kbId || baseOptions.value[0]?.id,
      title: parsed.title,
      content: parsed.content,
      sourceFile: parsed.sourceFile,
      fileType: parsed.fileType || 'txt'
    }
    dialog.visible = true
    ElMessage.success(`解析成功：${parsed.charCount || 0} 字`)
  } finally {
    fileParsing.value = false
  }
}

async function openEdit(row: KnowledgeDoc) {
  const detail = await getKnowledgeDoc(row.id)
  dialog.form = {
    id: detail.id,
    kbId: detail.kbId,
    title: detail.title,
    content: detail.content || '',
    sourceFile: detail.sourceFile || '',
    fileType: detail.fileType || 'txt'
  }
  dialog.visible = true
}

async function onSubmit() {
  if (!dialog.form.kbId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!dialog.form.title.trim() || !dialog.form.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  dialog.saving = true
  try {
    const payload = {
      kbId: dialog.form.kbId,
      title: dialog.form.title,
      content: dialog.form.content,
      sourceFile: dialog.form.sourceFile,
      fileType: dialog.form.fileType
    }
    if (dialog.form.id) {
      await updateKnowledgeDoc(dialog.form.id, payload)
    } else {
      await createKnowledgeDoc(payload)
    }
    dialog.visible = false
    ElMessage.success('保存成功')
    await fetchList()
  } finally {
    dialog.saving = false
  }
}

async function previewCurrentChunks() {
  if (!dialog.form.content.trim()) {
    ElMessage.warning('请先填写或上传文档内容')
    return
  }
  await openChunkPreview(dialog.form.content, dialog.form.title || '切片预览')
}

async function previewRowChunks(row: KnowledgeDoc) {
  const detail = await getKnowledgeDoc(row.id)
  if (!detail.content?.trim()) {
    ElMessage.warning('文档内容为空')
    return
  }
  await openChunkPreview(detail.content, detail.title)
}

async function openChunkPreview(content: string, title: string) {
  chunkDialog.title = `切片预览 · ${title}`
  chunkDialog.visible = true
  chunkDialog.loading = true
  chunkDialog.data = null
  try {
    chunkDialog.data = await previewKnowledgeDocChunks({ content })
  } finally {
    chunkDialog.loading = false
  }
}

async function onReindex(row: KnowledgeDoc) {
  await reindexKnowledgeDoc(row.id)
  ElMessage.success('索引已重建')
  await fetchList()
}

async function onDelete(row: KnowledgeDoc) {
  await ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '确认删除', { type: 'warning' })
  await deleteKnowledgeDoc(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}

function statusText(status: number) {
  if (status === KB_DOC_STATUS.INDEXED) return '已索引'
  if (status === KB_DOC_STATUS.INDEXING) return '索引中'
  if (status === KB_DOC_STATUS.FAILED) return '失败'
  return '待索引'
}

function statusType(status: number) {
  if (status === KB_DOC_STATUS.INDEXED) return 'success'
  if (status === KB_DOC_STATUS.FAILED) return 'danger'
  if (status === KB_DOC_STATUS.INDEXING) return 'warning'
  return 'info'
}

onMounted(async () => {
  await fetchOptions()
  await fetchList()
})
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
.head-actions,
.table-tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.hidden-file {
  display: none;
}
.table-tools {
  margin-bottom: var(--space-3);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
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
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}
.chunk-summary {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}
</style>
