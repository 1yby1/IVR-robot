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

export interface CallLogPageResult {
  records: CallLogItem[]
  total: number
  current: number
  size: number
}

export function pageCallLogs(params: { current?: number; size?: number; keyword?: string }) {
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
