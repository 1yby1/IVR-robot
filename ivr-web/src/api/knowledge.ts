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
