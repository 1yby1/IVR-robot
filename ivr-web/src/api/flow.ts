import { request } from './request'

export interface FlowItem {
  id: number
  flowCode: string
  flowName: string
  description?: string
  status: number
  currentVersion: number
  updatedAt: string
  graphJson?: string
}

export interface FlowPageResult {
  records: FlowItem[]
  total: number
  current: number
  size: number
}

export interface FlowPayload {
  flowCode: string
  flowName: string
  description?: string
  graphJson: string
}

export interface FlowDebugOption {
  key: string
  label: string
  targetNodeId: string
}

export interface FlowDebugResponse {
  sessionId: string
  flowId: number
  flowName: string
  currentNodeId: string
  currentNodeName: string
  status: 'running' | 'waiting' | 'ended'
  waitingFor?: string
  result?: string
  prompts: string[]
  events: string[]
  options: FlowDebugOption[]
  variables: Record<string, string>
}

export interface FlowOptionItem {
  id: number
  flowCode: string
  flowName: string
  currentVersion: number
}

export function pageFlows(params: { current?: number; size?: number; keyword?: string }) {
  return request<FlowPageResult>({
    url: '/flow/page',
    method: 'GET',
    params
  })
}

export function getFlow(id: number | string) {
  return request<FlowItem>({
    url: `/flow/${id}`,
    method: 'GET'
  })
}

export function createFlow(data: FlowPayload) {
  return request<number>({
    url: '/flow',
    method: 'POST',
    data
  })
}

export function updateFlow(id: number | string, data: FlowPayload) {
  return request<void>({
    url: `/flow/${id}`,
    method: 'PUT',
    data
  })
}

export function publishFlow(id: number | string) {
  return request<void>({
    url: `/flow/${id}/publish`,
    method: 'POST'
  })
}

export function offlineFlow(id: number | string) {
  return request<void>({
    url: `/flow/${id}/offline`,
    method: 'POST'
  })
}

export function deleteFlow(id: number | string) {
  return request<void>({
    url: `/flow/${id}`,
    method: 'DELETE'
  })
}

export function listPublishedFlows() {
  return request<FlowOptionItem[]>({
    url: '/flow/published-options',
    method: 'GET'
  })
}

export function startFlowDebug(id: number | string, data?: { caller?: string; callee?: string }) {
  return request<FlowDebugResponse>({
    url: `/flow/${id}/debug/start`,
    method: 'POST',
    data: data || {}
  })
}

export function sendFlowDebugInput(sessionId: string, input: string) {
  return request<FlowDebugResponse>({
    url: `/flow/debug/${sessionId}/input`,
    method: 'POST',
    data: { input }
  })
}
