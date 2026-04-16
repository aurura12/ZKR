import request from '@/utils/request'

/**
 * 获取队长工作台数据
 */
export function getLeaderDashboard(role) {
    return request.get('/api/leader/dashboard', { params: { role } })
}

/**
 * 检查当前用户是否是某角色的队长
 */
export function checkLeaderStatus(role) {
    return request.get('/api/leader/check-leader', { params: { role } })
}

/**
 * 为用户分配角色（管理员）
 */
export function assignRole(data) {
    return request.post('/api/leader/assign-role', data)
}

/**
 * 移除用户角色（管理员）
 */
export function removeRole(data) {
    return request.delete('/api/leader/remove-role', { data })
}
/**
 * 获取所有用户列表
 */
export function getAllUsers() {
    return request.get('/api/users')
}