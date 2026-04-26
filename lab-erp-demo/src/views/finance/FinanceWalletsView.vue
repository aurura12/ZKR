<template>
  <section class="finance-view wallets-view">
    <header class="view-hero">
      <div>
        <p class="view-eyebrow">资金</p>
        <h1>财务钱包</h1>
        <p class="view-description">
          展示钱包余额汇总与交易审计明细。
        </p>
      </div>
      <div class="hero-note">
        <span class="hero-note-label">交易条数</span>
        <strong>{{ formatFinanceText(transactionCount, '0') }}</strong>
      </div>
    </header>

    <FinanceErrorState
      v-if="showBlockingError"
      title="钱包数据加载失败"
      :message="walletStore.error"
      action-label="重试"
      @action="loadPageData"
    />

    <template v-else>
      <section v-if="hasWalletSummary" class="metric-grid">
        <FinanceMetricCard
          v-for="metric in summaryCards"
          :key="metric.key"
          :title="metric.title"
          :value="metric.value"
          :subtitle="metric.subtitle"
          :caption="metric.caption"
          :tone="metric.tone"
        />
      </section>
      <FinanceEmptyState
        v-else
        title="钱包汇总暂不可用"
        description="当前返回数据中没有汇总指标。"
      />

      <section class="content-grid content-grid--stacked">
        <article class="surface-card">
          <header class="surface-header">
            <div>
              <p class="surface-eyebrow">钱包汇总</p>
              <h2>钱包账户</h2>
            </div>
            <FinanceStatusTag :label="walletRows.length ? `${walletRows.length} 个钱包` : '无钱包'" :tone="walletRows.length ? 'info' : 'neutral'" />
          </header>

          <div v-if="walletRows.length" class="table-shell">
            <table class="finance-table">
              <thead>
                <tr>
                  <th>账户人</th>
                  <th>用户ID</th>
                  <th>角色</th>
                  <th>余额</th>
                  <th>分红收入</th>
                  <th>版权费收入</th>
                  <th>调账净额</th>
                  <th>入职时间</th>
                  <th>更新时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="wallet in walletRows" :key="wallet.walletId || wallet.owner?.userId || wallet.userId">
                  <td>{{ walletOwnerName(wallet) }}</td>
                  <td>{{ walletOwnerId(wallet) }}</td>
                  <td>{{ walletRole(wallet) }}</td>
                  <td :class="amountClass(wallet.balance)">{{ formatFinanceCurrency(wallet.balance) }}</td>
                  <td class="amount amount--positive">{{ formatFinanceCurrency(wallet.totalDividendEarned) }}</td>
                  <td class="amount amount--positive">{{ formatFinanceCurrency(wallet.totalRoyaltyEarned ?? wallet.finTotalRoyaltyEarned) }}</td>
                  <td :class="amountClass(wallet.totalAdjustmentAmount)">{{ formatFinanceDelta(wallet.totalAdjustmentAmount) }}</td>
                  <td>{{ formatFinanceDateTime(wallet.createdAt ?? wallet.created_at) }}</td>
                  <td>{{ formatFinanceDateTime(wallet.updatedAt ?? wallet.updated_at) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <FinanceEmptyState
            v-else
            title="未返回钱包账户"
            description="钱包接口当前未返回账户行。"
            compact
          />
        </article>

        <article class="surface-card">
          <header class="surface-header">
            <div>
              <p class="surface-eyebrow">审计轨迹</p>
              <h2>交易记录</h2>
            </div>
            <FinanceStatusTag :label="transactionLimitLabel" tone="info" />
          </header>

          <div v-if="transactionRows.length" class="table-shell">
            <table class="finance-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>账户人</th>
                  <th>类型</th>
                  <th>方向</th>
                  <th>金额</th>
                  <th>交易后余额</th>
                  <th>来源</th>
                  <th>备注</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="transaction in transactionRows" :key="transaction.id">
                  <td>#{{ transaction.id }}</td>
                  <td>{{ transactionOwnerName(transaction) }}</td>
                  <td>
                    <FinanceStatusTag
                      :label="formatFinanceEnumLabel(transactionTypeMap, transaction.transactionType ?? transaction.transType, '未知')"
                      :tone="transactionTypeTone(transaction.transactionType ?? transaction.transType)"
                    />
                  </td>
                  <td>
                    <FinanceStatusTag
                      :label="formatFinanceEnumLabel(cashFlowDirectionMap, transaction.cashFlowDirection ?? transaction.direction, '未指定')"
                      :tone="cashFlowDirectionTone(transaction.cashFlowDirection ?? transaction.direction)"
                    />
                  </td>
                  <td :class="amountClass(transaction.amount)">{{ formatFinanceDelta(transaction.amount) }}</td>
                  <td :class="amountClass(transaction.balanceAfter)">{{ formatFinanceCurrency(transaction.balanceAfter) }}</td>
                  <td>{{ transactionSource(transaction) }}</td>
                  <td>{{ formatFinanceText(transaction.remark ?? transaction.finRemark) }}</td>
                  <td>{{ formatFinanceDateTime(transaction.createdAt ?? transaction.created_at) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <FinanceEmptyState
            v-else
            title="未返回交易记录"
            description="当前查询下交易接口未返回审计记录。"
            compact
          />
        </article>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import FinanceEmptyState from '@/components/finance/FinanceEmptyState.vue'
import FinanceErrorState from '@/components/finance/FinanceErrorState.vue'
import FinanceMetricCard from '@/components/finance/FinanceMetricCard.vue'
import FinanceStatusTag from '@/components/finance/FinanceStatusTag.vue'
import { useFinanceWalletStore } from '@/stores/financeWalletStore'
import { financeCashFlowDirectionMap, financeWalletTransactionTypeMap } from '@/utils/financeEnums'
import {
  formatFinanceCount,
  formatFinanceCurrency,
  formatFinanceDateTime,
  formatFinanceDelta,
  formatFinanceEnumLabel,
  formatFinanceText
} from '@/utils/financeFormatters'

const walletStore = useFinanceWalletStore()

const walletOverview = computed(() => walletStore.walletEnvelope?.data || {})
const walletRows = computed(() => walletStore.wallets || [])
const transactionRows = computed(() => walletStore.transactions || [])
const transactionCount = computed(() => walletStore.transactionEnvelope?.meta?.total ?? walletOverview.value.totalCount ?? transactionRows.value.length)
const summary = computed(() => walletOverview.value.summary || null)
const hasWalletSummary = computed(() => Boolean(summary.value))

const summaryCards = computed(() => [
  {
    key: 'wallet-count',
    title: '钱包数量',
    value: formatFinanceCount(summary.value?.walletCount),
    subtitle: '钱包概览接口返回的账户数量。',
    caption: '包含当前可见的全部钱包账户。',
    tone: 'info'
  },
  {
    key: 'total-balance',
    title: '总余额',
    value: formatFinanceCurrency(summary.value?.totalBalance),
    subtitle: `Dividends ${formatFinanceCurrency(summary.value?.totalDividendEarned)}`,
    caption: `版权费 ${formatFinanceCurrency(summary.value?.totalRoyaltyEarned)}`,
    tone: 'success'
  },
  {
    key: 'adjustments',
    title: '调账净额',
    value: formatFinanceDelta(summary.value?.totalAdjustmentAmount),
    subtitle: `${formatFinanceCount(transactionRows.value.length)} 条可见交易`,
    caption: '仅统计后端钱包汇总结果。',
    tone: Number(summary.value?.totalAdjustmentAmount ?? 0) >= 0 ? 'warning' : 'danger'
  }
])

const showBlockingError = computed(() => Boolean(walletStore.error) && !walletRows.value.length && !transactionRows.value.length)
const transactionLimitLabel = computed(() => `最近 ${formatFinanceCount(walletStore.transactionFilters?.limit || walletStore.transactionEnvelope?.data?.limit || transactionRows.value.length)} 条`)
const transactionTypeMap = financeWalletTransactionTypeMap
const cashFlowDirectionMap = financeCashFlowDirectionMap

const loadPageData = async () => {
  try {
    await Promise.all([walletStore.loadWallets(), walletStore.loadTransactions({ limit: 50 })])
  } catch {
    return null
  }

  return walletStore.walletEnvelope
}

const walletOwnerName = wallet => formatFinanceText(wallet?.owner?.name, wallet?.realName || wallet?.owner?.username)
const walletOwnerId = wallet => formatFinanceText(wallet?.owner?.userId, wallet?.userId)
const walletRole = wallet => formatFinanceText(wallet?.role, wallet?.owner?.role || wallet?.jobRole)
const transactionOwnerName = transaction => formatFinanceText(transaction?.owner?.name, transaction?.realName || transaction?.owner?.username)
const transactionSource = transaction => {
  const table = transaction?.sourceTable || transaction?.finRefBizTable || transaction?.audit?.sourceTable
  const sourceId = transaction?.sourceBusinessId || transaction?.refBizId || transaction?.audit?.sourceId
  if (!table && !sourceId) {
    return '--'
  }
  if (!sourceId) {
    return table
  }
    return `${table || '来源'} #${sourceId}`
}
const transactionTypeTone = type => {
  if (type === 'DIVIDEND' || type === 'ROYALTY') return 'success'
  if (type === 'WITHDRAWAL') return 'warning'
  if (type === 'ADJUSTMENT') return 'info'
  return 'neutral'
}
const cashFlowDirectionTone = direction => {
  if (direction === 'IN') return 'success'
  if (direction === 'OUT') return 'danger'
  return 'neutral'
}
const amountClass = value => {
  const amount = Number(value ?? 0)
  if (amount > 0) return 'amount amount--positive'
  if (amount < 0) return 'amount amount--negative'
  return 'amount'
}

onMounted(() => {
  loadPageData()
})
</script>

<style scoped>
.finance-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.view-hero,
.surface-card,
.hero-note {
  border-radius: 24px;
}

.view-hero,
.surface-card {
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.08);
}

.view-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  background-image: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(240, 253, 244, 0.92));
}

.view-eyebrow,
.surface-eyebrow,
.hero-note-label {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #0f766e;
}

h1,
h2,
p {
  margin: 0;
}

h1,
h2,
strong {
  color: #0f172a;
}

h1 {
  font-size: clamp(28px, 3vw, 40px);
}

h2 {
  font-size: 22px;
}

.view-description,
.hero-note,
th,
td {
  color: #475569;
}

.view-description {
  max-width: 68ch;
  line-height: 1.6;
}

.hero-note {
  min-width: 180px;
  padding: 18px;
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.72);
}

.metric-grid,
.content-grid {
  display: grid;
  gap: 16px;
}

.metric-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.content-grid--stacked {
  grid-template-columns: 1fr;
}

.surface-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

.surface-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.table-shell {
  overflow-x: auto;
}

.finance-table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px 10px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.92);
  text-align: left;
  white-space: nowrap;
}

th {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.amount {
  font-variant-numeric: tabular-nums;
}

.amount--positive {
  color: #047857;
}

.amount--negative {
  color: #b91c1c;
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .view-hero,
  .surface-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-note {
    min-width: 0;
    width: 100%;
  }

  th,
  td {
    white-space: normal;
  }
}
</style>
