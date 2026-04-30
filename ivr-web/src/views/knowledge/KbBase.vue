<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>知识库</h2>
          <p class="panel-sub">管理 RAG 使用的知识库分组</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <Plus :size="14" :stroke-width="2" style="margin-right: 4px" />
          新建知识库
        </el-button>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索名称 / 描述"
            style="max-width: 320px"
            @keyup.enter="reload"
            @clear="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="kbName" label="名称" min-width="180" />
          <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
          <el-table-column prop="embeddingModel" label="Embedding 模型" width="180">
            <template #default="{ row }">
              <code class="code-chip">{{ row.embeddingModel || 'text-embedding-v2' }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="docCount" label="文档" width="90" />
          <el-table-column prop="chunkCount" label="切片" width="90" />
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑知识库' : '新建知识库'" width="520px">
      <el-form label-position="top" :model="dialog.form">
        <el-form-item label="知识库名称" required>
          <el-input v-model="dialog.form.kbName" maxlength="128" placeholder="例如：售后FAQ" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="Embedding 模型">
          <el-input v-model="dialog.form.embeddingModel" placeholder="text-embedding-v2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from 'lucide-vue-next'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  pageKnowledgeBases,
  updateKnowledgeBase
} from '@/api/knowledge'
import type { KnowledgeBase } from '@/api/knowledge'

const list = ref<KnowledgeBase[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = ref({ current: 1, size: 10, total: 0 })
const dialog = reactive({
  visible: false,
  saving: false,
  form: {
    id: 0,
    kbName: '',
    description: '',
    embeddingModel: 'text-embedding-v2'
  }
})

async function fetchList() {
  loading.value = true
  try {
    const res = await pageKnowledgeBases({
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

async function reload() {
  pagination.value.current = 1
  await fetchList()
}

function openCreate() {
  dialog.form = { id: 0, kbName: '', description: '', embeddingModel: 'text-embedding-v2' }
  dialog.visible = true
}

function openEdit(row: KnowledgeBase) {
  dialog.form = {
    id: row.id,
    kbName: row.kbName,
    description: row.description || '',
    embeddingModel: row.embeddingModel || 'text-embedding-v2'
  }
  dialog.visible = true
}

async function onSubmit() {
  if (!dialog.form.kbName.trim()) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  dialog.saving = true
  try {
    const payload = {
      kbName: dialog.form.kbName,
      description: dialog.form.description,
      embeddingModel: dialog.form.embeddingModel
    }
    if (dialog.form.id) {
      await updateKnowledgeBase(dialog.form.id, payload)
    } else {
      await createKnowledgeBase(payload)
    }
    dialog.visible = false
    ElMessage.success('保存成功')
    await fetchList()
  } finally {
    dialog.saving = false
  }
}

async function onDelete(row: KnowledgeBase) {
  await ElMessageBox.confirm(`确定删除「${row.kbName}」吗？`, '确认删除', { type: 'warning' })
  await deleteKnowledgeBase(row.id)
  ElMessage.success('删除成功')
  await fetchList()
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
.action-list {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  :deep(.el-button) {
    margin-left: 0;
  }
}
</style>
