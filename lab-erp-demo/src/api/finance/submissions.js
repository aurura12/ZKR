import request from '@/utils/request'

export const getFinanceSubmissionCenter = () => request.get('/api/finance/submissions')

export const downloadFinanceSubmissionInvoice = submissionId => request.get(`/api/finance/submissions/${submissionId}/invoice`, {
  responseType: 'blob'
})

export const exportReimbursementsZip = (from, to) => request.get('/api/finance/export/reimbursements', {
  params: { from, to },
  responseType: 'blob'
})
