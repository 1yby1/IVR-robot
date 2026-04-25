import { request } from './request'

export interface UserItem {
  id: number
  username: string
  nickname: string
  email: string
  avatar?: string
  status: number
  roles: string[]
  createdAt: string
  lastLoginAt: string
}

export interface RoleItem {
  id: number
  roleCode: string
  roleName: string
  dataScope: number
  sort: number
  status: number
  remark?: string
  createdAt: string
}

export interface MenuItem {
  id: number
  parentId: number
  menuName: string
  menuType: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  sort: number
  visible: number
  status: number
  children: MenuItem[]
}

export interface UserPageResult {
  records: UserItem[]
  total: number
  current: number
  size: number
}

export interface RolePageResult {
  records: RoleItem[]
  total: number
  current: number
  size: number
}

export function pageUsers(params: { current?: number; size?: number; keyword?: string }) {
  return request<UserPageResult>({
    url: '/system/user/page',
    method: 'GET',
    params
  })
}

export function updateUserStatus(id: number | string, status: number) {
  return request<void>({
    url: `/system/user/${id}/status`,
    method: 'PUT',
    data: { status }
  })
}

export function resetUserPassword(id: number | string, password: string) {
  return request<void>({
    url: `/system/user/${id}/password`,
    method: 'PUT',
    data: { password }
  })
}

export function assignUserRoles(id: number | string, roleId: number) {
  return request<void>({
    url: `/system/user/${id}/roles`,
    method: 'PUT',
    data: { roleIds: [roleId] }
  })
}

export function pageRoles(params: { current?: number; size?: number; keyword?: string }) {
  return request<RolePageResult>({
    url: '/system/role/page',
    method: 'GET',
    params
  })
}

export function listEnabledRoles() {
  return request<RoleItem[]>({
    url: '/system/role/enabled',
    method: 'GET'
  })
}

export function updateRoleStatus(id: number | string, status: number) {
  return request<void>({
    url: `/system/role/${id}/status`,
    method: 'PUT',
    data: { status }
  })
}

export function getRoleMenuIds(id: number | string) {
  return request<number[]>({
    url: `/system/role/${id}/menus`,
    method: 'GET'
  })
}

export function assignRoleMenus(id: number | string, menuIds: number[]) {
  return request<void>({
    url: `/system/role/${id}/menus`,
    method: 'PUT',
    data: { menuIds }
  })
}

export function getMenuTree() {
  return request<MenuItem[]>({
    url: '/system/menu/tree',
    method: 'GET'
  })
}
