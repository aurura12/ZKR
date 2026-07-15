import request from '@/utils/request'

// ── 创建工单 ──
export const createWorkOrder = payload => request.post('/api/work-orders', payload)

// ── 查询项目下所有工单 ──
export const listWorkOrders = projectId => request.get(`/api/projects/${projectId}/work-orders`)

// ── 工单统计 ──
export const getWorkOrderStats = projectId => request.get(`/api/projects/${projectId}/work-orders/stats`)

// ── 接单 PENDING → IN_PROGRESS ──
export const acceptWorkOrder = id => request.patch(`/api/work-orders/${id}/accept`)

// ── 提交完成 IN_PROGRESS → COMPLETED ──
export const submitWorkOrder = id => request.patch(`/api/work-orders/${id}/submit`)

// ── 确认关闭 COMPLETED → CLOSED ──
export const confirmWorkOrder = id => request.patch(`/api/work-orders/${id}/confirm`)

// ── 取消工单 ──
export const cancelWorkOrder = id => request.post(`/api/work-orders/${id}/cancel`)
