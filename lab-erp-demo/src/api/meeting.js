import request from '@/utils/request'

export const getMeetingList = params => request.get('/api/meetings', { params })

export const getMeetingDetail = id => request.get(`/api/meetings/${id}`)

export const createMeeting = payload => request.post('/api/meetings', payload)

export const cancelMeeting = id => request.put(`/api/meetings/${id}/cancel`)

export const endMeeting = id => request.put(`/api/meetings/${id}/end`)

// 腾讯会议账号绑定
export const getMyMapping = () => request.get('/api/meetings/mapping/my')
export const bindMapping = data => request.post('/api/meetings/mapping/bind', data)
export const unbindMapping = () => request.post('/api/meetings/mapping/unbind')
