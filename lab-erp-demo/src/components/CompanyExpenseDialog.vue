<template>
  <el-dialog v-model="visible" title="个人采购报销" width="560px" custom-class="tech-dialog" @close="resetForm">
    <el-tabs v-model="activeTab" class="company-expense-tabs">
      <el-tab-pane label="合同" name="contract" />
      <el-tab-pane label="采购" name="procurement" />
      <el-tab-pane label="报销" name="reimbursement" />
    </el-tabs>

    <el-form label-position="top" style="margin-top: 16px;">
      <el-form-item v-if="activeTab === 'contract'" label="合同对方名称">
        <el-input v-model="form.counterparty" placeholder="例如：XX科技有限公司" maxlength="200" />
      </el-form-item>

      <el-form-item v-if="activeTab === 'reimbursement'" label="报销类型" required>
        <el-select v-model="form.reimburseType" placeholder="请选择报销类型" style="width: 100%">
          <el-option label="商务餐费" value="BUSINESS_MEAL" />
          <el-option label="正常差旅" value="NORMAL_TRAVEL" />
          <el-option label="补差价" value="PRICE_DIFF" />
        </el-select>
      </el-form-item>

      <el-form-item label="名称" required>
        <el-input v-model="form.itemName" placeholder="例如：3090显卡采购、云服务月度费用" maxlength="200" />
      </el-form-item>

      <el-form-item label="金额 (元)" required>
        <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width: 100%" placeholder="0.00" />
      </el-form-item>

      <el-form-item label="发票/凭证">
        <el-upload v-model:file-list="form.invoiceFileList" :auto-upload="false" list-type="picture"
                   accept=".jpg,.jpeg,.png,.gif,.bmp,.pdf,.ofd,.doc,.docx,.xls,.xlsx,.csv,.txt,.rar,.zip,.7z">
          <el-button size="small" type="primary" plain>选择文件</el-button>
          <template #tip><div class="el-upload__tip">支持常见图片/文档/压缩包格式，可选</div></template>
        </el-upload>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer-row">
        <el-button @click="downloadTemplate">下载报销模板</el-button>
        <div class="footer-spacer"></div>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交审批</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('contract')
const submitting = ref(false)

const form = ref({
  itemName: '',
  amount: null,
  counterparty: '',
  reimburseType: 'BUSINESS_MEAL',
  invoiceFileList: []
})

const expenseType = computed(() => {
  if (activeTab.value === 'contract') return 'EXTERNAL_SERVICE'
  if (activeTab.value === 'procurement') return 'HARDWARE'
  return form.value.reimburseType
})

const resetForm = () => {
  form.value = {
    itemName: '',
    amount: null,
    counterparty: '',
    reimburseType: 'BUSINESS_MEAL',
    invoiceFileList: []
  }
}

watch(activeTab, () => {
  form.value.counterparty = ''
  form.value.reimburseType = 'BUSINESS_MEAL'
})

const submit = async () => {
  if (!form.value.itemName || !form.value.itemName.trim()) return ElMessage.warning('请输入名称')
  if (!form.value.amount || form.value.amount <= 0) return ElMessage.warning('请输入有效金额')

  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('expenseType', expenseType.value)
    fd.append('itemName', form.value.itemName.trim())
    fd.append('amount', String(form.value.amount))
    if (activeTab.value === 'contract' && form.value.counterparty) {
      fd.append('counterparty', form.value.counterparty.trim())
    }
    form.value.invoiceFileList?.forEach(entry => {
      if (entry.raw) fd.append('invoiceFiles', entry.raw)
    })

    await request.post('/api/projects/expenses/company', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('费用已提交，等待焦淼审批')
    visible.value = false
    emit('submitted')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const downloadTemplate = async () => {
  try {
    const response = await request.get('/api/projects/expenses/reimbursement-template', { responseType: 'blob' })
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
.company-expense-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.dialog-footer-row {
  display: flex;
  align-items: center;
  width: 100%;
}
.footer-spacer {
  flex: 1;
}
</style>
