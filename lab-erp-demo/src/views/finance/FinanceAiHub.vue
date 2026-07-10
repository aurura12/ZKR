<template>
  <section class="ai-hub-view">
    <div class="hero-card">
      <div>
        <p class="eyebrow">AI</p>
        <h1>AI 业务</h1>
        <p class="description">全局业务检索与智能助手，支持自然语言问答和上下文感知。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="hub-tabs">
      <el-tab-pane label="全局检索" name="rag">
        <RagSearchView />
      </el-tab-pane>
      <el-tab-pane label="智能助手" name="ai">
        <FinanceAiChatView />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import RagSearchView from '@/views/finance/RagSearchView.vue'
import FinanceAiChatView from '@/views/finance/FinanceAiChatView.vue'

const activeTab = ref('rag')
</script>

<style scoped>
.ai-hub-view {
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
  background-image: linear-gradient(135deg, rgba(99,102,241,0.08), rgba(217,249,157,0.22));
}
.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #6366f1;
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
