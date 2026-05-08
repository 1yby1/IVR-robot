import { request } from './request'

export interface KnowledgeBase {
  id: number
  kbName: string
  description?: string
  embeddingModel?: string
  docCount: number
  chunkCount: number
  createdAt: string
}

export interface KnowledgeBaseOption {
  id: number
  kbName: string
}

export interface KnowledgeDoc {
  id: number
  kbId: number
  kbName: string
  title: string
  content?: string
  contentSnippet?: string
  sourceFile?: string
  fileType?: string
  status: number
  chunkCount: number
  createdAt: string
}

export interface KnowledgeDocParseResponse {
  title: string
  content: string
  sourceFile: string
  fileType: string
  charCount: number
}

export interface KnowledgeChunkPreview {
  index: number
  content: string
  charCount: number
  tokenCount: number
}

export interface KnowledgeChunkPreviewResponse {
  totalCount: number
  totalChars: number
  totalTokens: number
  chunks: KnowledgeChunkPreview[]
}

export const KB_DOC_STATUS = {
  PENDING: 0,
  INDEXING: 1,
  INDEXED: 2,
  FAILED: 3
} as const

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface KnowledgeBasePayload {
  kbName: string
  description?: string
  embeddingModel?: string
}

export interface KnowledgeDocPayload {
  kbId: number
  title: string
  content: string
  sourceFile?: string
  fileType?: string
}

export interface KnowledgeRetrievalDebugPayload {
  kbId?: number
  question: string
  topK: number
  generateAnswer: boolean
}

export interface KnowledgeRetrievalDebugChunk {
  docId: string
  title: string
  content: string
  score: number
}

export interface KnowledgeRetrievalDebugResponse {
  kbId?: number
  question: string
  topK: number
  answerStatus: 'ok' | 'no_hits' | 'retrieve_failed' | 'failed' | 'empty' | 'skipped'
  answer: string
  error?: string
  prompt: string
  chunks: KnowledgeRetrievalDebugChunk[]
}

export interface RagEvalCase {
  id: number
  kbId: number
  kbName: string
  question: string
  expectedDocTitle?: string
  expectedKeywords?: string
  shouldFallback: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface RagEvalCasePayload {
  kbId: number
  question: string
  expectedDocTitle?: string
  expectedKeywords?: string
  shouldFallback: boolean
  enabled: boolean
}

export interface RagEvalRun {
  id: number
  kbId: number
  kbName: string
  topK: number
  generateAnswer: boolean
  totalCount: number
  passedCount: number
  passRate: number
  hitRate: number
  keywordPassRate: number
  fallbackPassRate: number
  createdAt: string
}

export interface RagEvalRunPayload {
  kbId: number
  topK: number
  generateAnswer: boolean
}

export interface RagEvalResult {
  id: number
  runId: number
  caseId: number
  question: string
  retrievedChunks: string
  answer?: string
  hitExpectedDoc: boolean
  keywordPassed: boolean
  fallbackPassed: boolean
  passed: boolean
  failReason?: string
  createdAt: string
}

export function pageKnowledgeBases(params: { current?: number; size?: number; keyword?: string }) {
  return request<PageResult<KnowledgeBase>>({
    url: '/knowledge/bases/page',
    method: 'GET',
    params
  })
}

export function listKnowledgeBaseOptions() {
  return request<KnowledgeBaseOption[]>({
    url: '/knowledge/bases/options',
    method: 'GET'
  })
}

export function createKnowledgeBase(data: KnowledgeBasePayload) {
  return request<number>({
    url: '/knowledge/bases',
    method: 'POST',
    data
  })
}

export function updateKnowledgeBase(id: number | string, data: KnowledgeBasePayload) {
  return request<void>({
    url: `/knowledge/bases/${id}`,
    method: 'PUT',
    data
  })
}

export function deleteKnowledgeBase(id: number | string) {
  return request<void>({
    url: `/knowledge/bases/${id}`,
    method: 'DELETE'
  })
}

export function debugKnowledgeRetrieval(data: KnowledgeRetrievalDebugPayload) {
  return request<KnowledgeRetrievalDebugResponse>({
    url: '/knowledge/debug/retrieval',
    method: 'POST',
    data
  })
}

export function pageKnowledgeDocs(params: { current?: number; size?: number; kbId?: number | ''; keyword?: string }) {
  return request<PageResult<KnowledgeDoc>>({
    url: '/knowledge/docs/page',
    method: 'GET',
    params
  })
}

export function getKnowledgeDoc(id: number | string) {
  return request<KnowledgeDoc>({
    url: `/knowledge/docs/${id}`,
    method: 'GET'
  })
}

export function createKnowledgeDoc(data: KnowledgeDocPayload) {
  return request<number>({
    url: '/knowledge/docs',
    method: 'POST',
    data
  })
}

export function parseKnowledgeDocFile(file: File) {
  const data = new FormData()
  data.append('file', file)
  return request<KnowledgeDocParseResponse>({
    url: '/knowledge/docs/parse-file',
    method: 'POST',
    data
  })
}

export function previewKnowledgeDocChunks(data: { content: string }) {
  return request<KnowledgeChunkPreviewResponse>({
    url: '/knowledge/docs/chunks/preview',
    method: 'POST',
    data
  })
}

export function updateKnowledgeDoc(id: number | string, data: KnowledgeDocPayload) {
  return request<void>({
    url: `/knowledge/docs/${id}`,
    method: 'PUT',
    data
  })
}

export function reindexKnowledgeDoc(id: number | string) {
  return request<void>({
    url: `/knowledge/docs/${id}/reindex`,
    method: 'POST'
  })
}

export function deleteKnowledgeDoc(id: number | string) {
  return request<void>({
    url: `/knowledge/docs/${id}`,
    method: 'DELETE'
  })
}

export function pageRagEvalCases(params: { current?: number; size?: number; kbId?: number | ''; keyword?: string }) {
  return request<PageResult<RagEvalCase>>({
    url: '/knowledge/eval/cases/page',
    method: 'GET',
    params
  })
}

export function createRagEvalCase(data: RagEvalCasePayload) {
  return request<number>({
    url: '/knowledge/eval/cases',
    method: 'POST',
    data
  })
}

export function updateRagEvalCase(id: number | string, data: RagEvalCasePayload) {
  return request<void>({
    url: `/knowledge/eval/cases/${id}`,
    method: 'PUT',
    data
  })
}

export function deleteRagEvalCase(id: number | string) {
  return request<void>({
    url: `/knowledge/eval/cases/${id}`,
    method: 'DELETE'
  })
}

export function pageRagEvalRuns(params: { current?: number; size?: number; kbId?: number | '' }) {
  return request<PageResult<RagEvalRun>>({
    url: '/knowledge/eval/runs/page',
    method: 'GET',
    params
  })
}

export function runRagEval(data: RagEvalRunPayload) {
  return request<number>({
    url: '/knowledge/eval/runs',
    method: 'POST',
    data
  })
}

export function listRagEvalResults(runId: number | string) {
  return request<RagEvalResult[]>({
    url: `/knowledge/eval/runs/${runId}/results`,
    method: 'GET'
  })
}
