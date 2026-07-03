<template>
  <div class="expense-form-shell" :class="{ compact }">
    <div v-if="projectContext" class="context-card">
      <p class="context-eyebrow">关联项目</p>
      <strong>{{ projectContext.name || projectContext.projectName || '未命名项目' }}</strong>
      <span>{{ projectFlowLabel }}</span>
    </div>

    <div class="form-grid">
      <div class="field-block full-width">
        <label>{{ itemLabel }}</label>
        <el-input v-model.trim="form.itemName" :placeholder="itemPlaceholder" maxlength="200" show-word-limit />
      </div>

      <div class="field-block">
        <label>分类</label>
        <el-input v-model.trim="form.itemCategory" :placeholder="categoryPlaceholder" maxlength="100" />
      </div>

      <div class="field-block">
        <label>规格 / 说明</label>
        <el-input v-model.trim="form.itemSpecification" :placeholder="specificationPlaceholder" maxlength="200" />
      </div>

      <div class="field-block">
        <label>数量</label>
        <el-input-number v-model="form.quantity" :min="1" :max="9999" style="width: 100%" />
      </div>

      <div class="field-block">
        <label>单价</label>
        <el-input-number v-model="form.unitPrice" :min="0" :precision="2" :step="1" style="width: 100%" />
      </div>

      <div class="field-block">
        <label>总金额</label>
        <el-input-number v-model="form.totalAmount" :min="0" :precision="2" :step="1" style="width: 100%" />
      </div>

      <div class="field-block">
        <label>{{ supplierLabel }}</label>
        <el-input v-model.trim="form.supplierName" :placeholder="supplierPlaceholder" maxlength="150" />
      </div>

      <div class="field-block">
        <label>发票号码</label>
        <el-input v-model.trim="form.invoiceNumber" placeholder="请输入发票号码" maxlength="100" />
      </div>

      <div class="field-block">
        <label>{{ occurredAtLabel }}</label>
        <el-date-picker v-model="form.occurredAt" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
      </div>

      <template v-if="isTravelSubmission">
        <div class="field-block">
          <label>出发地</label>
          <el-input v-model.trim="form.departureLocation" placeholder="例如：北京" maxlength="120" />
        </div>

        <div class="field-block">
          <label>目的地</label>
          <el-input v-model.trim="form.destinationLocation" placeholder="例如：上海" maxlength="120" />
        </div>

        <div class="field-block">
          <label>出差开始日期</label>
          <el-date-picker v-model="form.travelStartAt" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" style="width: 100%" />
        </div>

        <div class="field-block">
          <label>出差结束日期</label>
          <el-date-picker v-model="form.travelEndAt" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" style="width: 100%" />
        </div>
      </template>

      <div class="field-block full-width">
        <label>{{ purposeLabel }}</label>
        <el-input v-model.trim="form.purpose" type="textarea" :rows="3" :placeholder="purposePlaceholder" maxlength="500" show-word-limit />
      </div>

      <div class="field-block full-width">
        <label>补充备注</label>
        <el-input v-model.trim="form.remarks" type="textarea" :rows="3" placeholder="可填写品牌、使用场景、审批补充说明等" maxlength="500" show-word-limit />
      </div>
    </div>

    <div class="invoice-card">
      <div>
        <p class="context-eyebrow">发票附件</p>
        <strong>{{ selectedInvoiceName || '尚未选择发票文件' }}</strong>
        <span>支持图片、PDF、Office 文档等常见格式</span>
      </div>
      <div class="invoice-actions">
        <input ref="invoiceInputRef" class="hidden-input" type="file" accept=".jpg,.jpeg,.png,.gif,.bmp,.pdf,.ofd,.doc,.docx,.xls,.xlsx,.csv,.txt,.rar,.zip,.7z" @change="handleInvoiceChange" />
        <el-button @click="triggerInvoicePicker">选择发票</el-button>
        <el-button v-if="selectedInvoiceName" text type="danger" @click="clearInvoice">移除</el-button>
      </div>
    </div>
    <div class="zip-hint">
      <p>上传 .zip 压缩包时，请按规范命名：<strong>姓名+项目名+金额.zip</strong>，压缩包内包含发票图片和汇总 Excel 模板。</p>
      <p><el-button type="primary" link size="small" @click="downloadTemplate">下载 Excel 模板</el-button></p>
    </div>

    <div class="footer-row">
      <div class="hint-text">提交后会自动同步到财务系统汇总列表。</div>
      <el-button type="primary" :loading="submitting" @click="submitForm">{{ submitButtonLabel }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  submissionType: {
    type: String,
    required: true
  },
  projectContext: {
    type: Object,
    default: null
  },
  compact: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['submitted'])

const invoiceInputRef = ref(null)
const submitting = ref(false)
const form = ref(createInitialForm())

const isTravelSubmission = computed(() => props.submissionType === 'PROJECT_TRAVEL_REIMBURSEMENT')
const isProjectExpense = computed(() => ['PROJECT_HARDWARE_PROCUREMENT', 'PROJECT_EXTERNAL_TECH_SERVICE', 'PROJECT_REIMBURSEMENT'].includes(props.submissionType))
const itemLabel = computed(() => isTravelSubmission.value ? '费用项目' : '采购物品')
const itemPlaceholder = computed(() => isTravelSubmission.value ? '例如：高铁票 / 酒店 / 市内交通' : '例如：显示器 / 测试板卡 / 办公设备')
const categoryPlaceholder = computed(() => isTravelSubmission.value ? '例如：交通 / 住宿 / 餐补' : '例如：硬件 / 软件 / 办公用品')
const specificationPlaceholder = computed(() => isTravelSubmission.value ? '例如：二等座往返 / 单晚住宿' : '例如：27寸 / 32GB / 单用户授权')
const supplierLabel = computed(() => isTravelSubmission.value ? '商户 / 平台' : '供应商')
const supplierPlaceholder = computed(() => isTravelSubmission.value ? '例如：12306 / 携程 / 酒店名称' : '例如：京东 / 华为 / 供应商名称')
const occurredAtLabel = computed(() => isTravelSubmission.value ? '报销发生日期' : '采购日期')
const purposeLabel = computed(() => isTravelSubmission.value ? '出差事由' : '采购用途')
const purposePlaceholder = computed(() => isTravelSubmission.value ? '请说明出差目的、项目背景和报销原因' : '请说明采购用途、使用人和业务场景')
const submitButtonLabel = computed(() => {
  if (isProjectExpense.value) {
    const labels = {
      PROJECT_HARDWARE_PROCUREMENT: '提交硬件采购',
      PROJECT_EXTERNAL_TECH_SERVICE: '提交技术服务',
      PROJECT_REIMBURSEMENT: '提交报销'
    }
    return labels[props.submissionType] || '提交成本'
  }
  return isTravelSubmission.value ? '提交出差报销' : '提交采购申请'
})
const selectedInvoiceName = computed(() => form.value.invoiceFile?.name || '')
const projectFlowLabel = computed(() => {
  const value = String(props.projectContext?.flowType || props.projectContext?.projectFlowType || '').toUpperCase()
  if (value === 'PROJECT') return '项目交付'
  if (value === 'PRODUCT') return '产品研发'
  if (value === 'RESEARCH') return '科研创新'
  return 'ERP 项目'
})

function createInitialForm() {
  return {
    itemName: '',
    itemCategory: '',
    itemSpecification: '',
    quantity: 1,
    unitPrice: null,
    totalAmount: null,
    supplierName: '',
    invoiceNumber: '',
    occurredAt: '',
    purpose: '',
    remarks: '',
    departureLocation: '',
    destinationLocation: '',
    travelStartAt: '',
    travelEndAt: '',
    invoiceFile: null
  }
}

const triggerInvoicePicker = () => {
  invoiceInputRef.value?.click()
}

const handleInvoiceChange = event => {
  const [file] = Array.from(event.target.files || [])
  form.value.invoiceFile = file || null
}

const clearInvoice = () => {
  form.value.invoiceFile = null
  if (invoiceInputRef.value) {
    invoiceInputRef.value.value = ''
  }
}

const validateForm = () => {
  if (!String(form.value.itemName || '').trim()) {
    return '请填写费用项目'
  }
  if (!String(form.value.invoiceNumber || '').trim()) {
    return '请填写发票号码'
  }
  if (!form.value.occurredAt) {
    return '请选择发生日期'
  }
  if (!String(form.value.purpose || '').trim()) {
    return isTravelSubmission.value ? '请填写出差事由' : '请填写采购用途'
  }
  if (!form.value.invoiceFile) {
    return '请上传发票'
  }
  if ((!form.value.totalAmount || Number(form.value.totalAmount) <= 0)
      && (!form.value.unitPrice || Number(form.value.unitPrice) <= 0)) {
    return '请填写有效金额'
  }
  if (isTravelSubmission.value) {
    if (!String(form.value.departureLocation || '').trim() || !String(form.value.destinationLocation || '').trim()) {
      return '请填写出发地和目的地'
    }
    if (!form.value.travelStartAt || !form.value.travelEndAt) {
      return '请选择完整的出差日期'
    }
  }
  return ''
}

const buildPayload = () => {
  const payload = new FormData()
  payload.append('itemName', String(form.value.itemName || '').trim())
  payload.append('itemCategory', String(form.value.itemCategory || '').trim())
  payload.append('itemSpecification', String(form.value.itemSpecification || '').trim())
  payload.append('quantity', String(form.value.quantity || 1))
  if (form.value.unitPrice !== null && form.value.unitPrice !== '') {
    payload.append('unitPrice', String(form.value.unitPrice))
  }
  if (form.value.totalAmount !== null && form.value.totalAmount !== '') {
    payload.append('totalAmount', String(form.value.totalAmount))
  }
  payload.append('supplierName', String(form.value.supplierName || '').trim())
  payload.append('invoiceNumber', String(form.value.invoiceNumber || '').trim())
  payload.append('occurredAt', String(form.value.occurredAt || ''))
  payload.append('purpose', String(form.value.purpose || '').trim())
  payload.append('remarks', String(form.value.remarks || '').trim())
  payload.append('departureLocation', String(form.value.departureLocation || '').trim())
  payload.append('destinationLocation', String(form.value.destinationLocation || '').trim())
  payload.append('travelStartAt', String(form.value.travelStartAt || ''))
  payload.append('travelEndAt', String(form.value.travelEndAt || ''))
  payload.append('invoiceFile', form.value.invoiceFile)
  return payload
}

const submitForm = async () => {
  const errorMessage = validateForm()
  if (errorMessage) {
    ElMessage.warning(errorMessage)
    return
  }

  submitting.value = true
  try {
    const url = isProjectExpense.value
      ? `/api/projects/${props.projectContext?.projectId || props.projectContext?.id}/project-expenses?expenseType=${props.submissionType}`
      : isTravelSubmission.value
      ? `/api/projects/${props.projectContext?.projectId || props.projectContext?.id}/travel-reimbursements`
      : '/api/submissions/personal-procurement'
    const response = await request.post(url, buildPayload(), {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 120000
    })
    ElMessage.success(response?.message || '提交成功')
    form.value = createInitialForm()
    clearInvoice()
    emit('submitted', response)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const downloadTemplate = async () => {
  try {
    const response = await request.get('/api/projects/expenses/reimbursement-template', {
      responseType: 'blob'
    })
    const url = window.URL.createObjectURL(new Blob([response]))
    const link = document.createElement('a')
    link.href = url
    link.download = 'reimbursement_template.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载模板失败')
  }
}
</script>

<style scoped>
.expense-form-shell {
  --expense-card-bg: linear-gradient(145deg, rgba(255, 255, 255, 0.88), rgba(239, 246, 255, 0.8));
  --expense-card-border: rgba(148, 163, 184, 0.18);
  --expense-eyebrow: #0f766e;
  display: grid;
  gap: 18px;
}

.expense-form-shell.compact {
  gap: 16px;
}

.context-card,
.invoice-card {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--expense-card-border);
  background: var(--expense-card-bg);
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.context-card strong,
.invoice-card strong {
  display: block;
  color: var(--text-main);
  margin-bottom: 4px;
}

.context-card span,
.invoice-card span {
  color: var(--text-sub);
  font-size: 13px;
}

.context-eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--expense-eyebrow);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field-block {
  display: grid;
  gap: 8px;
}

.field-block label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
}

.full-width {
  grid-column: 1 / -1;
}

.invoice-actions,
.footer-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.hidden-input {
  display: none;
}

.hint-text {
  font-size: 13px;
  color: var(--text-sub);
}

:global(.dark) .expense-form-shell {
  --expense-card-bg: linear-gradient(145deg, rgba(15, 23, 42, 0.94), rgba(30, 41, 59, 0.9));
  --expense-card-border: rgba(148, 163, 184, 0.24);
  --expense-eyebrow: #5eead4;
}

.zip-hint {
  padding: 12px 16px;
  background: rgba(14, 165, 233, 0.06);
  border: 1px solid rgba(14, 165, 233, 0.2);
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.8;
}

.zip-hint p {
  margin: 0;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .context-card,
  .invoice-card,
  .footer-row {
    flex-direction: column;
    align-items: stretch;
  }

  .invoice-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
