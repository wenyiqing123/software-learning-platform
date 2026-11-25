import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    // Graph renderers are route-level vendor chunks; keep the warning threshold
    // aligned with the largest renderer after the actual split, not the app shell.
    chunkSizeWarningLimit: 1500,
    rolldownOptions: {
      output: {
        manualChunks(id) {
          const normalized = id.replaceAll('\\', '/')
          if (!normalized.includes('/node_modules/')) return undefined
          if (normalized.includes('/three/')) return 'graph-three'
          if (normalized.includes('/@antv/layout/') || normalized.includes('/@antv/hierarchy/')) return 'graph-layout'
          if (normalized.includes('/@antv/g-canvas/') || normalized.includes('/@antv/g/')) return 'graph-renderer'
          if (normalized.includes('/@antv/component/')) return 'graph-ui'
          if (normalized.includes('/@antv/algorithm/') || normalized.includes('/@antv/graphlib/')) return 'graph-algorithm'
          if (normalized.includes('/@antv/g6/')) return 'graph-g6'
          if (normalized.includes('/echarts/')) return 'charts'
          if (normalized.includes('/mermaid/dist/chunks/')) return undefined
          if (normalized.includes('/mermaid/') || normalized.includes('/@mermaid-js/')) return 'mermaid-core'
          if (normalized.includes('/elkjs/')) return 'mermaid-elk'
          if (normalized.includes('/cytoscape') || normalized.includes('/dagre') || normalized.includes('/d3/') || normalized.includes('/d3-') || normalized.includes('/khroma/')) return 'mermaid-layout'
          if (normalized.includes('/katex/')) return 'mermaid-math'
          if (normalized.includes('/element-plus/') || normalized.includes('/@element-plus/')) return 'element-plus'
          if (normalized.includes('/vue/') || normalized.includes('/vue-router/') || normalized.includes('/pinia/')) return 'vue-core'
          if (normalized.includes('/axios/') || normalized.includes('/@lucide/vue/')) return 'ui-vendor'
          return 'vendor'
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
