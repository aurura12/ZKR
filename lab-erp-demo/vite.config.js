import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 允许局域网/内网穿透访问
    host: '0.0.0.0',

    // ★★★ 新增这行：允许所有公网域名访问 (解决 BLOCKED 问题) ★★★
    allowedHosts: true,

    proxy: {
      '/api': {
        target: 'http://localhost:8101',
        changeOrigin: true
      }
    }
  }
})