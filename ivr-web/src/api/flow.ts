import { request } from './request'

export interface FlowItem {
  id: number
  flowCode: string
  flowName: string
  description?: string
  status: number
  currentVersion: number
  hasDraftDiff: boolean
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

export interface FlowAiGeneratePayload {
  requirement: string
  flowName?: string
}

export interface FlowAiGenerateResponse {
  graphJson: string
  summary: string
  warnings: string[]
  validationErrors: string[]
}

export interface FlowHealthRuntimeStats {
  sampleCalls: number
  endedCalls: number
  runningCalls: number
  transferCalls: number
  errorCalls: number
  timeoutCalls: number
  avgDurationSeconds: number
}

export interface FlowHealthIssue {
  level: 'error' | 'warning' | 'info'
  category: string
  nodeId?: string
  nodeName?: string
  message: string
  suggestion: string
}

export interface FlowNodeHealthItem {
  nodeId: string
  nodeName: string
  nodeType: string
  incoming: number
  outgoing: number
  enterCount: number
  errorCount: number
  fallbackCount: number
  aiHitCount: number
  transferCount: number
  successRate?: number
  healthLevel: 'success' | 'warning' | 'danger' | 'info'
}

export interface FlowHealthResponse {
  flowId: number
  flowName: string
  score: number
  grade: string
  summary: string
  runtimeStats: FlowHealthRuntimeStats
  issues: FlowHealthIssue[]
  nodes: FlowNodeHealthItem[]
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
  visitedNodeIds: string[]
}

export interface FlowOptionItem {
  id: number
  flowCode: string
  flowName: string
  currentVersion: number
}

export interface FlowVersionItem {
  id: number
  flowId: number
  version: number
  versionLabel: string
  draft: boolean
  published: boolean
  current: boolean
  diffFromPublished?: boolean
  changeNote?: string
  createdBy?: number
  createdAt: string
  graphJson: string
  nodeCount: number
  edgeCount: number
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

export function getFlowHealth(id: number | string) {
  return request<FlowHealthResponse>({
    url: `/flow/${id}/health`,
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

export function generateFlowByAi(data: FlowAiGeneratePayload) {
  return request<FlowAiGenerateResponse>({
    url: '/flow/ai/generate',
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

export function listFlowVersions(id: number | string) {
  return request<FlowVersionItem[]>({
    url: `/flow/${id}/versions`,
    method: 'GET'
  })
}

export function restoreFlowVersionToDraft(id: number | string, version: number) {
  return request<void>({
    url: `/flow/${id}/versions/${version}/restore-draft`,
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
