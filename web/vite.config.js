import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { existsSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const gitkeepPlugin = {
  name: 'keep-web-dist-gitkeep',
  closeBundle() {
    const gitkeep = resolve(dirname(fileURLToPath(import.meta.url)), '../web-dist/.gitkeep')
    if (!existsSync(gitkeep)) writeFileSync(gitkeep, '')
  },
}

export default defineConfig({
  plugins: [vue(), gitkeepPlugin],
  build: {
    outDir: '../web-dist',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
