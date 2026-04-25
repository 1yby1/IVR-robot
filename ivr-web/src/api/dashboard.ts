import { request } from './request'

export interface RecentCall {
  time: string
  caller: string
  flowName: string
  result: string
}

export interface DashboardOverview {
  todayCalls: number
  onlineFlows: number
  draftFlows: number
  activeUsers: number
  aiResolutionRate: number
  transferRate: number
  recentCalls: RecentCall[]
}

export function getDashboardOverview() {
  return request<DashboardOverview>({
    url: '/dashboard/overview',
    method: 'GET'
  })
}
