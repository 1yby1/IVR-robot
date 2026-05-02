import { request } from './request'

export interface CallLogItem {
  id: number
  callUuid: string
  caller: string
  callee: string
  flowId: number
  flowCode: string
  flowName: string
  flowVersion: number
  startTime: string
  answerTime: string
  endTime: string
  duration: number
  endReason: string
  transferTo?: string
  hangupBy?: string
}

export interface CallEventItem {
  id: number
  callUuid: string
  nodeKey: string
  nodeType: string
  eventType: string
  payload: string
  eventTime: string
}

export interface CallReplayPathStep {
  stepNo: number
  nodeKey: string
  nodeName: string
  nodeType: string
  eventTime: string
  level: 'danger' | 'warning' | 'success' | 'info'
  summary: string
}

export interface CallReplayEvent {
  id: number
  nodeKey: string
  nodeType: string
  eventType: string
  eventTime: string
  level: 'danger' | 'warning' | 'success' | 'info'
  summary: string
  payload: string
  payloadPretty: string
}

export interface CallReplayResponse {
  callUuid: string
  caller: string
  callee: string
  flowId: number
  flowCode: string
  flowName: string
  flowVersion: number
  startTime: string
  endTime: string
  duration: number
  endReason: string
  transferTo?: string
  path: CallReplayPathStep[]
  events: CallReplayEvent[]
}

export interface CallLogPageResult {
  records: CallLogItem[]
  total: number
  current: number
  size: number
}

export function pageCallLogs(params: {
  current?: number
  size?: number
  keyword?: string
  flowId?: number | ''
  endReason?: string
  dateFrom?: string
  dateTo?: string
}) {
  return request<CallLogPageResult>({
    url: '/robot/call/page',
    method: 'GET',
    params
  })
}

export function listCallEvents(callUuid: string) {
  return request<CallEventItem[]>({
    url: `/robot/call/${callUuid}/events`,
    method: 'GET'
  })
}

export function getCallReplay(callUuid: string) {
  return request<CallReplayResponse>({
    url: `/robot/call/${callUuid}/replay`,
    method: 'GET'
  })
}
