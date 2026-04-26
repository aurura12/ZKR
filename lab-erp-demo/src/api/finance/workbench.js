import request from '@/utils/request'
import {
  normalizeFinanceAdjustmentPayload,
  normalizeFinanceClearingPayload,
  normalizeFinanceDividendPayload,
  normalizeFinanceLedgerMonthPayload
} from '@/utils/financeAdapters'

export const runCostBatch = payload => request.post('/api/batch/run_cost', normalizeFinanceLedgerMonthPayload(payload))

export const getCostBatchPreview = (ventureId, ledgerMonth) =>
  request.get(`/api/batch/preview/${ventureId}`, {
    params: { ledgerMonth }
  })

export const getClearingVentures = params => request.get('/api/clearing/ventures', { params })

export const executeClearing = payload => request.post('/api/clearing/execute', normalizeFinanceClearingPayload(payload))

export const prepareDividendSheet = payload => request.post('/api/dividend/prepare', normalizeFinanceDividendPayload(payload))

export const getDividendSheets = params => request.get('/api/dividend/list', { params })

export const confirmDividendSheet = payload => request.post('/api/dividend/confirm', normalizeFinanceDividendPayload(payload))

export const createAdjustment = payload => request.post('/api/adjustment/create', normalizeFinanceAdjustmentPayload(payload))

export const getAdjustmentLogs = params => request.get('/api/adjustment/list', { params })

export const getBatchJobs = () => request.get('/api/batch/jobs')

export const updateBatchJob = params => request.put('/api/batch/jobs', null, { params })

export const triggerBatchJob = params => request.post('/api/batch/jobs/run', null, { params })

export const getBatchProjectControls = () => request.get('/api/batch/projects')

export const updateBatchProjectControl = params => request.put('/api/batch/projects', null, { params })
