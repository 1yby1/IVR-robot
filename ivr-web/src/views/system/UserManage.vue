<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>用户管理</h2>
          <p class="panel-sub">管理系统用户账号与登录状态</p>
        </div>
      </header>

      <div class="panel-body">
        <div class="table-tools">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索账号 / 昵称 / 邮箱"
            class="search-input"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
          <el-button @click="fetchList">查询</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="username" label="账号" min-width="140">
            <template #default="{ row }">
              <code class="code-chip">{{ row.username }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" min-width="120" />
          <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
          <el-table-column label="角色" min-width="140">
            <template #default="{ row }">
              <div class="role-list">
                <el-tag
                  v-for="role in row.roles"
                  :key="role"
                  size="small"
                  effect="plain"
                  :type="role === 'admin' ? 'danger' : 'info'"
                >
                  {{ roleName(role) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" effect="plain" size="small">启用</el-tag>
              <el-tag v-else type="info" effect="plain" size="small">停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="最近登录" width="170">
            <template #default="{ row }">
              <span class="muted">{{ row.lastLoginAt || '未登录' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <div class="action-list">
                <el-button size="small" text @click="openAssignRoles(row)">分配角色</el-button>
                <el-button size="small" text @click="openResetPassword(row)">重置密码</el-button>
                <el-button
                  v-if="row.status === 1"
                  size="small"
                  text
                  type="warning"
                  :disabled="isSelf(row)"
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

    <el-dialog v-model="resetDialog.visible" title="重置密码" width="420px">
      <el-form
        ref="resetFormRef"
        :model="resetDialog.form"
        :rules="resetRules"
        label-position="top"
      >
        <el-form-item label="账号">
          <el-input :model-value="resetDialog.user?.username" disabled />
        </el-form-item>
        <el-form-item prop="password" label="新密码">
          <el-input
            v-model="resetDialog.form.password"
            type="password"
            placeholder="请输入 6-32 位新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="resetDialog.loading" @click="submitResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog.visible" title="分配角色" width="460px">
      <el-form label-position="top">
        <el-form-item label="账号">
          <el-input :model-value="roleDialog.user?.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="roleDialog.roleId" class="role-options">
            <el-radio
              v-for="role in roleDialog.options"
              :key="role.id"
              :label="role.id"
            >
              {{ role.roleName }}（{{ role.roleCode }}）
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="roleDialog.loading" @click="submitAssignRoles">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  assignUserRoles,
  listEnabledRoles,
  pageUsers,
  resetUserPassword,
  updateUserStatus
} from '@/api/system'
import type { RoleItem, UserItem } from '@/api/system'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const list = ref<UserItem[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

const resetFormRef = ref<FormInstance>()
const resetDialog = reactive({
  visible: false,
  loading: false,
  user: null as UserItem | null,
  form: {
    password: ''
  }
})

const roleDialog = reactive({
  visible: false,
  loading: false,
  user: null as UserItem | null,
  options: [] as RoleItem[],
  roleId: undefined as number | undefined
})

const resetRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur' }
  ]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await pageUsers({
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

function roleName(role: string) {
  const names: Record<string, string> = {
    admin: '超级管理员',
    operator: '运营人员',
    viewer: '只读人员'
  }
  return names[role] || role
}

function isSelf(row: UserItem) {
  return row.id === userStore.userInfo?.id
}

async function onToggleStatus(row: UserItem, status: number) {
  const action = status === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}账号「${row.username}」吗？`, `确认${action}`, {
    type: 'warning'
  })
  await updateUserStatus(row.id, status)
  ElMessage.success(`${action}成功`)
  await fetchList()
}

function openResetPassword(row: UserItem) {
  resetDialog.user = row
  resetDialog.form.password = ''
  resetDialog.visible = true
  resetFormRef.value?.clearValidate()
}

async function openAssignRoles(row: UserItem) {
  if (isSelf(row)) {
    ElMessage.warning('不能修改当前登录账号的角色')
    return
  }
  roleDialog.user = row
  roleDialog.roleId = undefined
  roleDialog.loading = false
  roleDialog.visible = true
  roleDialog.options = await listEnabledRoles()
  const selected = roleDialog.options.find((role) => row.roles.includes(role.roleCode))
  roleDialog.roleId = selected?.id
}

async function submitResetPassword() {
  if (!resetDialog.user || !resetFormRef.value) return
  await resetFormRef.value.validate()
  resetDialog.loading = true
  try {
    await resetUserPassword(resetDialog.user.id, resetDialog.form.password)
    ElMessage.success('密码已重置')
    resetDialog.visible = false
  } finally {
    resetDialog.loading = false
  }
}

async function submitAssignRoles() {
  if (!roleDialog.user) return
  if (!roleDialog.roleId) {
    ElMessage.error('请选择一个角色')
    return
  }
  roleDialog.loading = true
  try {
    await assignUserRoles(roleDialog.user.id, roleDialog.roleId)
    ElMessage.success('角色已更新')
    roleDialog.visible = false
    await fetchList()
  } finally {
    roleDialog.loading = false
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
.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
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
.muted {
  color: var(--color-text-muted);
}
.role-options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}
</style>
