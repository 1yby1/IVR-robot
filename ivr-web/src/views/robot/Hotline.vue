<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>热线管理</h2>
          <p class="panel-sub">将呼入号码绑定到已发布 IVR 流程</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <Plus :size="14" :stroke-width="2" style="margin-right: 4px" />
          新增热线
        </el-button>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索热线号码 / 备注"
            class="search-input"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
          <el-button @click="fetchList">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="hotline" label="热线号码" width="160">
            <template #default="{ row }">
              <code class="code-chip">{{ row.hotline }}</code>
            </template>
          </el-table-column>
          <el-table-column label="绑定流程" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="flow-cell">
                <span>{{ row.flowName }}</span>
                <code v-if="row.flowCode" class="flow-code">{{ row.flowCode }}</code>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="90">
            <template #default="{ row }">
              <span class="num">v{{ row.flowVersion || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.enabled === 1" type="success" effect="plain" size="small">启用</el-tag>
              <el-tag v-else type="info" effect="plain" size="small">停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text @click="openEdit(row)">编辑</el-button>
                <el-button
                  v-if="row.enabled === 1"
                  size="small"
                  text
                  type="warning"
                  @click="toggleEnabled(row, 0)"
                >
                  停用
                </el-button>
                <el-button
                  v-else
                  size="small"
                  text
                  type="success"
                  @click="toggleEnabled(row, 1)"
                >
                  启用
                </el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增热线' : '编辑热线'" width="520px">
      <el-form ref="formRef" :model="dialog.form" :rules="rules" label-position="top">
        <el-form-item label="热线号码" prop="hotline">
          <el-input v-model="dialog.form.hotline" placeholder="例如：4001" />
        </el-form-item>
        <el-form-item label="绑定流程" prop="flowId">
          <el-select
            v-model="dialog.form.flowId"
            filterable
            placeholder="请选择已发布流程"
            style="width: 100%"
          >
            <el-option
              v-for="flow in flowOptions"
              :key="flow.id"
              :label="`${flow.flowName}（${flow.flowCode} · v${flow.currentVersion}）`"
              :value="flow.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from 'lucide-vue-next'
import { listPublishedFlows } from '@/api/flow'
import type { FlowOptionItem } from '@/api/flow'
import {
  createHotline,
  deleteHotline,
  pageHotlines,
  updateHotline,
  updateHotlineEnabled
} from '@/api/hotline'
import type { HotlineItem } from '@/api/hotline'

const list = ref<HotlineItem[]>([])
const flowOptions = ref<FlowOptionItem[]>([])
const loading = ref(false)
const keyword = ref('')
const formRef = ref<FormInstance>()
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

const dialog = reactive({
  visible: false,
  loading: false,
  mode: 'create' as 'create' | 'edit',
  currentId: undefined as number | undefined,
  form: {
    hotline: '',
    flowId: undefined as number | undefined,
    remark: ''
  }
})

const rules: FormRules = {
  hotline: [
    { required: true, message: '请输入热线号码', trigger: 'blur' },
    {
      pattern: /^[0-9A-Za-z_+\-#*]+$/,
      message: '热线号码只能包含数字、字母、+、-、_、#、*',
      trigger: 'blur'
    },
    { max: 32, message: '热线号码不能超过 32 位', trigger: 'blur' }
  ],
  flowId: [{ required: true, message: '请选择绑定流程', trigger: 'change' }],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageHotlines({
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

async function fetchFlowOptions() {
  flowOptions.value = await listPublishedFlows()
}

async function openCreate() {
  await fetchFlowOptions()
  dialog.mode = 'create'
  dialog.currentId = undefined
  dialog.form.hotline = ''
  dialog.form.flowId = undefined
  dialog.form.remark = ''
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function openEdit(row: HotlineItem) {
  await fetchFlowOptions()
  dialog.mode = 'edit'
  dialog.currentId = row.id
  dialog.form.hotline = row.hotline
  dialog.form.flowId = row.flowId
  dialog.form.remark = row.remark || ''
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value || !dialog.form.flowId) return
  await formRef.value.validate()
  dialog.loading = true
  try {
    const payload = {
      hotline: dialog.form.hotline,
      flowId: dialog.form.flowId,
      remark: dialog.form.remark
    }
    if (dialog.mode === 'create') {
      await createHotline(payload)
      ElMessage.success('热线已创建')
    } else if (dialog.currentId) {
      await updateHotline(dialog.currentId, payload)
      ElMessage.success('热线已更新')
    }
    dialog.visible = false
    await fetchList()
  } finally {
    dialog.loading = false
  }
}

async function toggleEnabled(row: HotlineItem, enabled: number) {
  const action = enabled === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}热线「${row.hotline}」吗？`, `确认${action}`, {
    type: 'warning'
  })
  await updateHotlineEnabled(row.id, enabled)
  ElMessage.success(`${action}成功`)
  await fetchList()
}

async function onDelete(row: HotlineItem) {
  await ElMessageBox.confirm(`确定删除热线「${row.hotline}」吗？`, '确认删除', {
    type: 'warning'
  })
  await deleteHotline(row.id)
  ElMessage.success('删除成功')
  await fetchList()
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
.search-input { max-width: 320px; }
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
  min-width: 0;
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
</style>
