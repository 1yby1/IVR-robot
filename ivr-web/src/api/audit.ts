import { request } from './request'

export interface OperationAuditLog {
  id: number
  userId?: number
  username?: string
  nickname?: string
  moduleName: string
  operationType: string
  operationName: string
  requestMethod: string
  requestUri: string
  queryParams?: string
  requestBody?: string
  ip?: string
  userAgent?: string
  status: 'success' | 'failed'
  resultCode: number
  errorMessage?: string
  latencyMs: number
  createdAt: string
}

export interface OperationAuditPageResult {
  records: OperationAuditLog[]
  total: number
  current: number
  size: number
}

export function pageOperationAudits(params: {
  current?: number
  size?: number
  keyword?: string
  moduleName?: string
  status?: string
}) {
  return request<OperationAuditPageResult>({
    url: '/system/audit/page',
    method: 'GET',
    params
  })
}

export function listAuditModules() {
  return request<string[]>({
    url: '/system/audit/modules',
    method: 'GET'
  })
}
