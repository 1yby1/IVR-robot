<template>
  <div class="page-container audit-page">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>操作审计</h2>
          <p class="panel-sub">追踪后台写操作的操作者、结果、耗时和请求摘要</p>
        </div>
        <el-button @click="reloadAll">刷新</el-button>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-select v-model="filters.moduleName" clearable placeholder="全部模块" style="width: 160px" @change="reload">
            <el-option v-for="item in modules" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="全部结果" style="width: 130px" @change="reload">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索用户 / 接口 / 请求 / 错误 / IP"
            class="search-input"
            @keyup.enter="reload"
            @clear="reload"
          />
          <el-button @click="reload">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'danger'" effect="plain" size="small">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作人" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.nickname || row.username || row.userId || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="moduleName" label="模块" width="110" />
          <el-table-column prop="operationName" label="动作" width="150" show-overflow-tooltip />
          <el-table-column label="接口" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="method">{{ row.requestMethod }}</span>
              <code class="uri">{{ row.requestUri }}</code>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="100">
            <template #default="{ row }">
              <span class="num">{{ row.latencyMs || 0 }}ms</span>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="130" show-overflow-tooltip />
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

    <el-dialog v-model="detailDialog.visible" title="审计详情" width="860px">
      <template v-if="detailDialog.row">
        <div class="detail-meta">
          <div><span>操作人</span><strong>{{ operatorText(detailDialog.row) }}</strong></div>
          <div><span>结果</span><strong>{{ detailDialog.row.status === 'success' ? '成功' : '失败' }}</strong></div>
          <div><span>耗时</span><strong>{{ detailDialog.row.latencyMs || 0 }}ms</strong></div>
          <div><span>IP</span><strong>{{ detailDialog.row.ip || '-' }}</strong></div>
        </div>
        <div class="detail-line">
          <span class="method">{{ detailDialog.row.requestMethod }}</span>
          <code class="uri">{{ detailDialog.row.requestUri }}</code>
        </div>
        <div v-if="detailDialog.row.queryParams" class="detail-block">
          <div class="block-title">查询参数</div>
          <pre>{{ detailDialog.row.queryParams }}</pre>
        </div>
        <div class="detail-block">
          <div class="block-title">请求摘要</div>
          <pre>{{ detailDialog.row.requestBody || '-' }}</pre>
        </div>
        <div v-if="detailDialog.row.errorMessage" class="detail-block">
          <div class="block-title">错误信息</div>
          <pre>{{ detailDialog.row.errorMessage }}</pre>
        </div>
        <div class="detail-block">
          <div class="block-title">User-Agent</div>
          <pre>{{ detailDialog.row.userAgent || '-' }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listAuditModules, pageOperationAudits } from '@/api/audit'
import type { OperationAuditLog } from '@/api/audit'

const modules = ref<string[]>([])
const list = ref<OperationAuditLog[]>([])
const loading = ref(false)
const filters = reactive({ moduleName: '', status: '', keyword: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })
const detailDialog = reactive({
  visible: false,
  row: null as OperationAuditLog | null
})

async function fetchModules() {
  modules.value = await listAuditModules()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageOperationAudits({
      current: pagination.current,
      size: pagination.size,
      moduleName: filters.moduleName,
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
  await Promise.all([fetchModules(), reload()])
}

function openDetail(row: OperationAuditLog) {
  detailDialog.row = row
  detailDialog.visible = true
}

function operatorText(row: OperationAuditLog) {
  return row.nickname || row.username || (row.userId ? String(row.userId) : '-')
}

onMounted(reloadAll)
</script>

<style scoped lang="scss">
.audit-page {
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
}

.panel-sub {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.panel-body {
  padding: var(--space-4);
}

.table-tools {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.search-input {
  max-width: 340px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.method {
  display: inline-flex;
  min-width: 48px;
  margin-right: var(--space-2);
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
}

.uri {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.num {
  font-variant-numeric: tabular-nums;
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

.detail-line {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
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
  max-height: 240px;
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
  .detail-meta {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
