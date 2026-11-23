import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    '/auth-service': { target: env.GATEWAY_URL || 'http://127.0.0.1:8088', changeOrigin: true },
    '/swagger': { target: env.GATEWAY_URL || 'http://127.0.0.1:8088', changeOrigin: true },
    '/v3/api-docs': { target: env.GATEWAY_URL || 'http://127.0.0.1:8088', changeOrigin: true },
    '/webjars': { target: env.GATEWAY_URL || 'http://127.0.0.1:8088', changeOrigin: true },
    '/actuator/gateway': { target: env.GATEWAY_URL || 'http://127.0.0.1:8088', changeOrigin: true },
  }
  return {
    plugins: [react()],
    server: {
      port: 5173, strictPort: true,
      proxy,
    },
    preview: { port: 4173, strictPort: true, proxy },
  }
})
