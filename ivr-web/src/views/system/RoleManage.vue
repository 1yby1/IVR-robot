<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>角色管理</h2>
          <p class="panel-sub">管理后台角色与账号分配状态</p>
        </div>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索角色编码 / 名称 / 备注"
            class="search-input"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
          <el-button @click="fetchList">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="roleCode" label="编码" min-width="150">
            <template #default="{ row }">
              <code class="code-chip">{{ row.roleCode }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="roleName" label="名称" min-width="140" />
          <el-table-column label="数据范围" width="110">
            <template #default="{ row }">
              <span class="muted">{{ dataScopeText(row.dataScope) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" effect="plain" size="small">启用</el-tag>
              <el-tag v-else type="info" effect="plain" size="small">停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openMenuAuth(row)">
                授权
              </el-button>
              <el-button
                v-if="row.status === 1"
                size="small"
                text
                type="warning"
                :disabled="row.roleCode === 'admin'"
                @click="onToggleStatus(row, 0)"
              >
                停用
              </el-button>
              <el-button
                v-else
                size="small"
                text
                type="success"
                @click="onToggleStatus(row, 1)"
              >
                启用
              </el-button>
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

    <el-dialog v-model="menuDialog.visible" title="角色授权" width="520px">
      <div class="auth-summary">
        <span class="muted">角色</span>
        <code class="code-chip">{{ menuDialog.role?.roleCode }}</code>
        <span>{{ menuDialog.role?.roleName }}</span>
      </div>
      <el-tree
        ref="menuTreeRef"
        v-loading="menuDialog.loading"
        :data="menuDialog.tree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'menuName', children: 'children' }"
        class="menu-tree"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.menuName }}</span>
            <code v-if="data.perms" class="perm-chip">{{ data.perms }}</code>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="menuDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="menuDialog.saving"
          :disabled="menuDialog.role?.roleCode === 'admin'"
          @click="submitMenuAuth"
        >
          保存授权
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignRoleMenus, getMenuTree, getRoleMenuIds, pageRoles, updateRoleStatus } from '@/api/system'
import type { MenuItem, RoleItem } from '@/api/system'

const list = ref<RoleItem[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})
const menuTreeRef = ref<any>()
const menuDialog = reactive({
  visible: false,
  loading: false,
  saving: false,
  role: null as RoleItem | null,
  tree: [] as MenuItem[]
})

async function fetchList() {
  loading.value = true
  try {
    const res = await pageRoles({
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

function dataScopeText(scope: number) {
  if (scope === 1) return '全部'
  if (scope === 2) return '本人'
  if (scope === 3) return '自定义'
  return '未知'
}

async function onToggleStatus(row: RoleItem, status: number) {
  const action = status === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}角色「${row.roleName}」吗？`, `确认${action}`, {
    type: 'warning'
  })
  await updateRoleStatus(row.id, status)
  ElMessage.success(`${action}成功`)
  await fetchList()
}

async function openMenuAuth(row: RoleItem) {
  menuDialog.role = row
  menuDialog.visible = true
  menuDialog.loading = true
  try {
    const [tree, checkedIds] = await Promise.all([
      getMenuTree(),
      getRoleMenuIds(row.id)
    ])
    menuDialog.tree = tree
    await nextTick()
    menuTreeRef.value?.setCheckedKeys(checkedIds)
  } finally {
    menuDialog.loading = false
  }
}

async function submitMenuAuth() {
  if (!menuDialog.role || !menuTreeRef.value) return
  if (menuDialog.role.roleCode === 'admin') {
    ElMessage.warning('超级管理员默认拥有所有权限')
    return
  }
  const checked = menuTreeRef.value.getCheckedKeys(false) as number[]
  const halfChecked = menuTreeRef.value.getHalfCheckedKeys() as number[]
  const menuIds = Array.from(new Set([...checked, ...halfChecked]))
  menuDialog.saving = true
  try {
    await assignRoleMenus(menuDialog.role.id, menuIds)
    ElMessage.success('授权已保存')
    menuDialog.visible = false
  } finally {
    menuDialog.saving = false
  }
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
  padding: var(--space-4);
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
.code-chip {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-neutral-100);
  color: var(--color-neutral-700);
}
.muted {
  color: var(--color-text-muted);
}
.auth-summary {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
}
.menu-tree {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-2);
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.perm-chip {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
</style>
