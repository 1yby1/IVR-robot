import { request } from './request'

export interface HotlineItem {
  id: number
  hotline: string
  flowId: number
  flowCode: string
  flowName: string
  flowVersion: number
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
