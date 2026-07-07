import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { config } from './src/config.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: true,
    hmr: {
      host: config.domain === 'localhost' ? 'localhost' : config.domain,
      protocol: config.domain === 'localhost' ? 'ws' : 'wss',
      clientPort: config.domain === 'localhost' ? 8050 : 443
    }
  }
})
