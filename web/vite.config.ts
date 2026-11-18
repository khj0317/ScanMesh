import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// API 서버의 CORS 기본 허용 주소(http://localhost:5181)에 맞춰 포트를 고정한다
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5181,
    strictPort: true,
  },
});
