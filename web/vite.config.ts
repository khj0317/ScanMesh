import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // API 서버의 CORS 기본 허용 주소(http://localhost:5181)에 맞춰 포트를 고정한다
  server: {
    port: 5181,
    strictPort: true,
  },
  // npm run preview: 배포 빌드를 배포와 같은 구조로 확인한다. Vercel처럼 /api를 API 서버로 넘겨 화면과 같은 출처로 만든다
  preview: {
    port: 4280,
    strictPort: true,
    proxy: {
      '/api': process.env.SCANMESH_API_URL ?? 'http://localhost:8190',
    },
  },
});
