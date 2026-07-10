<template>
  <section class="audit-hub-view">
    <div class="hero-card">
      <div>
        <p class="eyebrow">审计</p>
        <h1>审计中心</h1>
        <p class="description">手工调账与成本调整日志统一管理，支持借贷调整录入与操作追溯。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="hub-tabs">
      <el-tab-pane label="手工调账" name="adjustment">
        <AdjustmentCenterView />
      </el-tab-pane>
      <el-tab-pane label="成本调整日志" name="log">
        <CostAdjustmentLogView />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import AdjustmentCenterView from '@/views/finance/AdjustmentCenterView.vue'
import CostAdjustmentLogView from '@/views/finance/CostAdjustmentLogView.vue'

const activeTab = ref('adjustment')
</script>

<style scoped>
.audit-hub-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  background: rgba(255,255,255,0.8);
  border: 1px solid rgba(148,163,184,0.24);
  border-radius: 24px;
  box-shadow: 0 20px 40px rgba(15,23,42,0.08);
  background-image: linear-gradient(135deg, rgba(234,88,12,0.08), rgba(217,249,157,0.22));
}
.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #ea580c;
}
h1 {
  margin: 0;
  font-size: clamp(28px, 3vw, 40px);
  color: #0f172a;
}
.description {
  color: #475569;
  line-height: 1.6;
  max-width: 60ch;
  margin: 8px 0 0;
}
.hub-tabs {
  background: rgba(255,255,255,0.8);
  border: 1px solid rgba(148,163,184,0.24);
  border-radius: 24px;
  box-shadow: 0 20px 40px rgba(15,23,42,0.08);
  padding: 8px 20px 20px;
}
</style>
