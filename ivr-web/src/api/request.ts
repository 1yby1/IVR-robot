import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

service.interceptors.request.use(
  (config) => {
    NProgress.start()
    const token = localStorage.getItem('ivr-token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (err) => {
    NProgress.done()
    return Promise.reject(err)
  }
)

service.interceptors.response.use(
  (resp: AxiosResponse<ApiResult>) => {
    NProgress.done()
    const { code, msg, data } = resp.data
    if (code === 0 || code === 200) return data as any
    if (code === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('ivr-token')
      window.location.href = '/login'
      return Promise.reject(new Error(msg))
    }
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg))
  },
  (err) => {
    NProgress.done()
    const status = err.response?.status
    const msg = err.response?.data?.msg || err.message
    if (status === 401) {
      localStorage.removeItem('ivr-token')
      window.location.href = '/login'
    }
    ElMessage.error(msg || '网络错误')
    return Promise.reject(err)
  }
)

export function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return service(config) as unknown as Promise<T>
}

export default service
