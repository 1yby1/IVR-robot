import { request } from './request'

export interface HotlineItem {
  id: number
  hotline: string
  flowId: number
  flowCode: string
  flowName: string
  flowVersion: number
  flowStatus: number
  flowCurrentVersion: number
  healthStatus: 'ok' | 'disabled' | 'danger'
  healthMessage: string
  enabled: number
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface HotlinePageResult {
  records: HotlineItem[]
  total: number
  current: number
  size: number
}

export interface HotlinePayload {
  hotline: string
  flowId: number
  remark?: string
}

export interface HotlineImpactItem {
  id: number
  hotline: string
  enabled: number
  remark?: string
}

export interface HotlineImpactResponse {
  flowId: number
  flowCode: string
  flowName: string
  flowStatus: number
  currentVersion: number
  nextVersion: number
  hotlineCount: number
  enabledHotlineCount: number
  hotlines: HotlineImpactItem[]
}

export function pageHotlines(params: { current?: number; size?: number; keyword?: string }) {
  return request<HotlinePageResult>({
    url: '/robot/hotline/page',
    method: 'GET',
    params
  })
}

export function createHotline(data: HotlinePayload) {
  return request<number>({
    url: '/robot/hotline',
    method: 'POST',
    data
  })
}

export function updateHotline(id: number | string, data: HotlinePayload) {
  return request<void>({
    url: `/robot/hotline/${id}`,
    method: 'PUT',
    data
  })
}

export function updateHotlineEnabled(id: number | string, enabled: number) {
  return request<void>({
    url: `/robot/hotline/${id}/enabled`,
    method: 'PUT',
    data: { enabled }
  })
}

export function deleteHotline(id: number | string) {
  return request<void>({
    url: `/robot/hotline/${id}`,
    method: 'DELETE'
  })
}

export function getFlowHotlineImpact(flowId: number | string) {
  return request<HotlineImpactResponse>({
    url: `/robot/hotline/flow/${flowId}/impact`,
    method: 'GET'
  })
}
