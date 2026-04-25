import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout, getUserInfo } from '@/api/auth'
import type { LoginParams, RegisterParams, UserInfo } from '@/api/auth'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const userInfo = ref<UserInfo | null>(null)
    const perms = ref<string[]>([])

    async function login(params: LoginParams) {
      const res = await apiLogin(params)
      token.value = res.token
      userInfo.value = res.userInfo
      perms.value = res.userInfo.perms || []
      localStorage.setItem('ivr-token', res.token)
      return res
    }

    async function register(params: RegisterParams) {
      const res = await apiRegister(params)
      token.value = res.token
      userInfo.value = res.userInfo
      perms.value = res.userInfo.perms || []
      localStorage.setItem('ivr-token', res.token)
      return res
    }

    async function fetchUserInfo() {
      const info = await getUserInfo()
      userInfo.value = info
      perms.value = info.perms || []
      return info
    }

    async function logout() {
      try {
        await apiLogout()
      } finally {
        token.value = ''
        userInfo.value = null
        perms.value = []
        localStorage.removeItem('ivr-token')
      }
    }

    function hasPerm(code: string): boolean {
      return perms.value.includes(code) || perms.value.includes('*')
    }

    function hasAnyPerm(codes: string[]): boolean {
      return perms.value.includes('*') || codes.some((code) => perms.value.includes(code))
    }

    return { token, userInfo, perms, login, register, fetchUserInfo, logout, hasPerm, hasAnyPerm }
  },
  {
    persist: {
      key: 'ivr-user',
      storage: localStorage,
      paths: ['token', 'userInfo', 'perms']
    }
  }
)
