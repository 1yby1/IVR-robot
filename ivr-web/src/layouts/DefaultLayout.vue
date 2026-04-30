<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <Phone :size="18" :stroke-width="2.2" />
        </div>
        <div class="brand-text">
          <div class="brand-name">IVR</div>
          <div class="brand-sub">智能语音机器人</div>
        </div>
      </div>

      <nav class="nav">
        <div class="nav-group">
          <div class="nav-group-title">语音机器人</div>
          <RouterLink
            v-for="item in visibleGroup1"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            active-class="is-active"
          >
            <component :is="item.icon" :size="16" :stroke-width="1.8" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>

        <div class="nav-group">
          <div class="nav-group-title">配置管理</div>
          <RouterLink
            v-for="item in visibleGroup2"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            active-class="is-active"
          >
            <component :is="item.icon" :size="16" :stroke-width="1.8" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </nav>
    </aside>

    <div class="main-wrap">
      <header class="topbar">
        <div class="crumb">
          <span class="crumb-title">{{ route.meta.title }}</span>
        </div>
        <div class="topbar-right">
          <el-dropdown trigger="click" @command="onCommand">
            <button class="user-btn" type="button">
              <span class="user-avatar">
                {{ (userStore.userInfo?.nickname || 'U').slice(0, 1) }}
              </span>
              <span class="user-name">
                {{ userStore.userInfo?.nickname || userStore.userInfo?.username || 'Guest' }}
              </span>
              <ChevronDown :size="14" :stroke-width="1.8" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <LogOut :size="14" :stroke-width="1.8" style="margin-right: 6px" />
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main">
        <RouterView v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'
import {
  Phone, Home, Workflow, Users, ShieldCheck, Menu, PhoneCall, ListChecks,
  ChevronDown, LogOut, BookOpen, FileText
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const group1 = [
  { path: '/robot/home', label: '首页', icon: Home, perm: 'robot:home:view' },
  { path: '/robot/hotline', label: '热线管理', icon: PhoneCall, perm: 'robot:hotline:list' },
  { path: '/robot/callLogs', label: '通话记录', icon: ListChecks, perm: 'robot:call:list' },
  { path: '/flow/list',  label: '流程列表', icon: Workflow, perm: 'flow:list' }
]
const group2 = [
  { path: '/knowledge/base', label: '知识库', icon: BookOpen, perm: 'kb:base:list' },
  { path: '/knowledge/doc', label: '知识文档', icon: FileText, perm: 'kb:doc:list' },
  { path: '/system/user', label: '用户管理', icon: Users, perm: 'system:user:list' },
  { path: '/system/role', label: '角色管理', icon: ShieldCheck, perm: 'system:role:list' },
  { path: '/system/menu', label: '菜单管理', icon: Menu, perm: 'system:menu:list' }
]

const visibleGroup1 = computed(() => group1.filter((item) => userStore.hasPerm(item.perm)))
const visibleGroup2 = computed(() => group2.filter((item) => userStore.hasPerm(item.perm)))

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    await userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.app-layout {
  display: flex;
  height: 100vh;
  background: var(--color-bg-muted);
}

// ---------- Sidebar ----------
.sidebar {
  width: var(--sidebar-width);
  background: var(--color-bg);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-4);
  border-bottom: 1px solid var(--color-border);
  height: var(--topbar-height);
}
.brand-mark {
  width: 28px; height: 28px;
  border-radius: var(--radius-md);
  background: var(--color-secondary);
  color: #fff;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.brand-text { line-height: 1.15; }
.brand-name {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
  letter-spacing: 1px;
}
.brand-sub {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  margin-top: 2px;
}

.nav {
  flex: 1;
  padding: var(--space-3) var(--space-2);
  overflow-y: auto;
}
.nav-group { margin-bottom: var(--space-4); }
.nav-group-title {
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  margin: 2px 0;
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  position: relative;
  transition: background var(--tr-fast), color var(--tr-fast);
  &:hover {
    color: var(--color-text);
    background: var(--color-neutral-100);
  }
  &.is-active {
    color: var(--color-primary);
    background: rgba(15, 118, 110, 0.08);
    font-weight: var(--weight-medium);
    &::before {
      content: '';
      position: absolute;
      left: -8px;
      top: 8px; bottom: 8px;
      width: 3px;
      background: var(--color-primary);
      border-radius: 0 2px 2px 0;
    }
  }
}

// ---------- Main area ----------
.main-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--topbar-height);
  padding: 0 var(--space-6);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.crumb-title {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
}

.user-btn {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  color: var(--color-text);
  transition: background var(--tr-fast), border-color var(--tr-fast);
  &:hover {
    background: var(--color-neutral-50);
    border-color: var(--color-border);
  }
}
.user-avatar {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
}
.user-name {
  font-size: var(--text-sm);
}

.main {
  flex: 1;
  overflow: auto;
}

.fade-enter-active, .fade-leave-active { transition: opacity var(--tr-fast); }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
