import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@shared': path.resolve(__dirname, '../shared')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/health': 'http://127.0.0.1:8080',
      '/models': 'http://127.0.0.1:8080',
      '/chat': 'http://127.0.0.1:8080',
      '/conversations': 'http://127.0.0.1:8080',
      '/settings': 'http://127.0.0.1:8080',
      '/diagnostics': 'http://127.0.0.1:8080',
      '/logs': 'http://127.0.0.1:8080'
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
});
