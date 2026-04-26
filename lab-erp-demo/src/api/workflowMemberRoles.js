import request from '@/utils/request'

export const getProjectWorkflowMemberRoles = projectId => request.get(`/api/projects/${projectId}/workflow-member-roles`)

export const getProductWorkflowMemberRoles = projectId => request.get(`/api/products/${projectId}/workflow-member-roles`)

export const getResearchWorkflowMemberRoles = projectId => request.get(`/api/research/${projectId}/workflow-member-roles`)

export const getResearchWorkflowRoleCandidates = () => request.get('/api/research/role-candidates')

export const getProjectWorkflowRoleCandidates = () => request.get('/api/projects/role-candidates')

export const getProductWorkflowRoleCandidates = () => request.get('/api/products/role-candidates')
