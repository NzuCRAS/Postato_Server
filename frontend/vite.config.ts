import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发期把 /api 代理到后端。
// 本机直跑默认 localhost;容器内由 compose 注入 BACKEND_PROXY=http://backend:8080。
const backendProxy = process.env.BACKEND_PROXY ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // 监听 0.0.0.0,容器内可被宿主访问
    port: 5173,
    proxy: {
      '/api': backendProxy,
    },
  },
})
