<template>
  <div class="page-container">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>菜单管理</h2>
          <p class="panel-sub">查看系统菜单、路由与按钮权限编码</p>
        </div>
      </header>

      <div class="panel-body">
        <el-table
          :data="tree"
          v-loading="loading"
          border
          row-key="id"
          :tree-props="{ children: 'children' }"
          default-expand-all
        >
          <el-table-column prop="menuName" label="菜单名称" min-width="180" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.menuType === 1" size="small" effect="plain">目录</el-tag>
              <el-tag v-else-if="row.menuType === 2" size="small" type="success" effect="plain">菜单</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">按钮</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="140">
            <template #default="{ row }">
              <span class="muted">{{ row.path || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="component" label="组件" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="muted">{{ row.component || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="perms" label="权限编码" min-width="180">
            <template #default="{ row }">
              <code v-if="row.perms" class="code-chip">{{ row.perms }}</code>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" size="small" type="success" effect="plain">启用</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">停用</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getMenuTree } from '@/api/system'
import type { MenuItem } from '@/api/system'

const tree = ref<MenuItem[]>([])
const loading = ref(false)

async function fetchTree() {
  loading.value = true
  try {
    tree.value = await getMenuTree()
  } finally {
    loading.value = false
  }
}

onMounted(fetchTree)
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
</style>
