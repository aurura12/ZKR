import request from '@/utils/request'

export const getMeetingList = params => request.get('/api/meetings', { params })

export const getMeetingDetail = id => request.get(`/api/meetings/${id}`)

export const createMeeting = payload => request.post('/api/meetings', payload)

export const cancelMeeting = id => request.put(`/api/meetings/${id}/cancel`)

export const endMeeting = id => request.put(`/api/meetings/${id}/end`)
