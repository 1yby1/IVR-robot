import { request } from './request'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  nickname: string
  email: string
  password: string
  confirmPassword: string
}

export interface LoginResult {
  token: string
  userInfo: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar?: string
  roles: string[]
  perms: string[]
}

export function login(data: LoginParams) {
  return request<LoginResult>({
    url: '/auth/login',
    method: 'POST',
    data
  })
}

export function register(data: RegisterParams) {
  return request<LoginResult>({
    url: '/auth/register',
    method: 'POST',
    data
  })
}

export function logout() {
  return request({ url: '/auth/logout', method: 'POST' })
}

export function getUserInfo() {
  return request<UserInfo>({ url: '/auth/userInfo', method: 'GET' })
}

export function getUserMenus() {
  return request<any[]>({ url: '/auth/menus', method: 'GET' })
}
