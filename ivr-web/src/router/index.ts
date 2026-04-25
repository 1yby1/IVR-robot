import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    redirect: '/robot/home'
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      {
        path: '/robot/home',
        name: 'RobotHome',
        component: () => import('@/views/robot/Home.vue'),
        meta: { title: '首页', perms: ['robot:home:view'] }
      },
      {
        path: '/robot/hotline',
        name: 'Hotline',
        component: () => import('@/views/robot/Hotline.vue'),
        meta: { title: '热线管理', perms: ['robot:hotline:list'] }
      },
      {
        path: '/robot/callLogs',
        name: 'CallLogs',
        component: () => import('@/views/robot/CallLogs.vue'),
        meta: { title: '通话记录', perms: ['robot:call:list'] }
      },
      {
        path: '/flow/list',
        name: 'FlowList',
        component: () => import('@/views/flow/FlowList.vue'),
        meta: { title: '流程列表', perms: ['flow:list'] }
      },
      {
        path: '/flow/editor/:id',
        name: 'FlowEditor',
        component: () => import('@/views/flow/FlowEditor.vue'),
        meta: { title: '流程编辑器', perms: ['flow:add', 'flow:edit'] }
      },
      {
        path: '/system/user',
        name: 'UserManage',
        component: () => import('@/views/system/UserManage.vue'),
        meta: { title: '用户管理', perms: ['system:user:list'] }
      },
      {
        path: '/system/role',
        name: 'RoleManage',
        component: () => import('@/views/system/RoleManage.vue'),
        meta: { title: '角色管理', perms: ['system:role:list'] }
      },
      {
        path: '/system/menu',
        name: 'MenuManage',
        component: () => import('@/views/system/MenuManage.vue'),
        meta: { title: '菜单管理', perms: ['system:menu:list'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/login/NotFound.vue'),
    meta: { public: true, title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || ''} - IVR 智能语音机器人`

  const userStore = useUserStore()
  if (to.meta.public) return next()

  if (!userStore.token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      await userStore.logout()
      return next('/login')
    }
  }
  const requiredPerms = to.meta.perms as string[] | undefined
  if (requiredPerms?.length && !userStore.hasAnyPerm(requiredPerms)) {
    return next('/robot/home')
  }
  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
