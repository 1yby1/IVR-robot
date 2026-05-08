import { request } from './request'
import type { PageResult } from './knowledge'

export interface LlmLogOverview {
  totalCount: number
  successCount: number
  failedCount: number
  successRate: number
  avgLatencyMs: number
  maxLatencyMs: number
  totalTokens: number
  avgTokens: number
}

export interface LlmCallLog {
  id: number
  traceId: string
  scene: string
  provider: string
  model: string
  status: 'success' | 'failed'
  promptTokens: number
  completionTokens: number
  totalTokens: number
  tokenEstimated: boolean
  promptChars: number
  responseChars: number
  latencyMs: number
  errorMessage?: string
  promptPreview?: string
  responsePreview?: string
  createdAt: string
}

export function getLlmLogOverview() {
  return request<LlmLogOverview>({
    url: '/ai/llm/logs/overview',
    method: 'GET'
  })
}

export function pageLlmLogs(params: {
  current?: number
  size?: number
  scene?: string
  status?: string
  keyword?: string
}) {
  return request<PageResult<LlmCallLog>>({
    url: '/ai/llm/logs/page',
    method: 'GET',
    params
  })
}

export function listLlmLogScenes() {
  return request<string[]>({
    url: '/ai/llm/logs/scenes',
    method: 'GET'
  })
}
