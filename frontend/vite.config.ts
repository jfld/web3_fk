import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import eslint from 'vite-plugin-eslint'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    eslint({
      cache: false,
      include: ['src/**/*.ts', 'src/**/*.tsx'],
      exclude: ['node_modules']
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@/components': resolve(__dirname, 'src/components'),
      '@/pages': resolve(__dirname, 'src/pages'),
      '@/hooks': resolve(__dirname, 'src/hooks'),
      '@/utils': resolve(__dirname, 'src/utils'),
      '@/services': resolve(__dirname, 'src/services'),
      '@/types': resolve(__dirname, 'src/types'),
      '@/store': resolve(__dirname, 'src/store'),
      '@/assets': resolve(__dirname, 'src/assets')
    }
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api/v1')
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        chunkFileNames: 'js/[name]-[hash].js',
        entryFileNames: 'js/[name]-[hash].js',
        assetFileNames: '[ext]/[name]-[hash].[ext]'
      }
    }
  },
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version)
  }
})