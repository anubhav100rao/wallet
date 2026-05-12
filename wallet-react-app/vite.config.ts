import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
      '/auth': 'http://localhost:8081',
      '/kyc': 'http://localhost:8081',
      '/.well-known': 'http://localhost:8081',
    },
  },
});
