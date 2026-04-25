<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-mark">
        <Phone :size="20" :stroke-width="2.2" />
      </div>
      <span class="brand-text">IVR&nbsp;智能语音机器人</span>
    </div>

    <div class="login-card">
      <header class="login-head">
        <h1>{{ isRegister ? '注册账号' : '登录' }}</h1>
        <p>{{ isRegister ? '运营人员账号申请' : '运营管理后台' }}</p>
      </header>

      <div class="auth-tabs" role="tablist" aria-label="登录或注册">
        <button
          type="button"
          class="auth-tab"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          class="auth-tab"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="activeRules"
        size="large"
        label-position="top"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username" label="账号">
          <el-input v-model="form.username" placeholder="请输入账号">
            <template #prefix>
              <User :size="16" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="isRegister" prop="nickname" label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称">
            <template #prefix>
              <User :size="16" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="isRegister" prop="email" label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱">
            <template #prefix>
              <Mail :size="16" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password" label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          >
            <template #prefix>
              <Lock :size="16" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="isRegister" prop="confirmPassword" label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          >
            <template #prefix>
              <LockKeyhole :size="16" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-button
          type="primary"
          :loading="loading"
          class="submit-btn"
          @click="onSubmit"
        >
          {{ isRegister ? '注册并登录' : '登录' }}
        </el-button>
      </el-form>

      <div class="login-tip">
        <template v-if="isRegister">
          注册后默认开通运营人员权限
        </template>
        <template v-else>
          初始账号 <code>admin</code> · 密码 <code>admin123</code>
        </template>
      </div>
    </div>

    <footer class="login-foot">
      © {{ new Date().getFullYear() }} IVR · 智能客服语音应答系统
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Phone, User, Lock, Mail, LockKeyhole } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'

type AuthMode = 'login' | 'register'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const mode = ref<AuthMode>('login')
const form = reactive({
  username: 'admin',
  nickname: '',
  email: '',
  password: 'admin123',
  confirmPassword: ''
})

const isRegister = computed(() => mode.value === 'register')

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 32, message: '账号长度需为 4-32 位', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '账号只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '昵称最多 64 位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }]
}

const activeRules = computed<FormRules>(() => (isRegister.value ? registerRules : loginRules))

function switchMode(nextMode: AuthMode) {
  if (mode.value === nextMode) return
  mode.value = nextMode
  if (nextMode === 'login') {
    form.username = 'admin'
    form.password = 'admin123'
  } else {
    form.username = ''
    form.password = ''
  }
  form.nickname = ''
  form.email = ''
  form.confirmPassword = ''
  nextTick(() => formRef.value?.clearValidate())
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isRegister.value && form.password !== form.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    if (isRegister.value) {
      await userStore.register({
        username: form.username,
        nickname: form.nickname,
        email: form.email,
        password: form.password,
        confirmPassword: form.confirmPassword
      })
      ElMessage.success('注册成功')
    } else {
      await userStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
    }
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: var(--color-bg-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--space-4);
  gap: var(--space-8);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--color-secondary);
  letter-spacing: 0.2px;
}
.brand-mark {
  width: 32px; height: 32px;
  border-radius: var(--radius-md);
  background: var(--color-secondary);
  color: #fff;
  display: grid;
  place-items: center;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: var(--space-8);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.login-head {
  margin-bottom: var(--space-4);
  h1 {
    font-size: var(--text-xl);
    font-weight: var(--weight-semibold);
    color: var(--color-text);
    margin-bottom: var(--space-1);
  }
  p {
    color: var(--color-text-muted);
    font-size: var(--text-sm);
  }
}
.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-1);
  padding: var(--space-1);
  margin-bottom: var(--space-4);
  background: var(--color-bg-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
.auth-tab {
  height: 36px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  cursor: pointer;
}
.auth-tab.active {
  background: var(--color-bg);
  color: var(--color-text);
  box-shadow: var(--shadow-1);
}
.input-icon {
  color: var(--color-text-subtle);
}
.submit-btn {
  width: 100%;
  margin-top: var(--space-2);
  height: 40px;
  font-weight: var(--weight-medium);
}
.login-tip {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  text-align: center;
  code {
    font-family: var(--font-mono);
    padding: 1px 6px;
    margin: 0 2px;
    border-radius: var(--radius-sm);
    background: var(--color-neutral-100);
    color: var(--color-text);
    font-size: var(--text-xs);
  }
}
.login-foot {
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
}
</style>
