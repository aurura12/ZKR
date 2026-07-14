export const PROVISION_ADMIN_USERNAMES = ['Zhangqi', 'guojianwen', 'jiaomiao', 'admin', 'leader']

export const canAccessProvisioning = username => {
  const normalized = String(username || '').trim().toLowerCase()
  return PROVISION_ADMIN_USERNAMES.some(item => item.toLowerCase() === normalized)
}
