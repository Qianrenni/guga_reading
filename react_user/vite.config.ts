import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const { VITE_BASE_URL } = env;
  console.log(`VITE_BASE_URL=${VITE_BASE_URL}`);
  return {
    plugins: [react()],
    server: {
      port: 8080,
      host: '0.0.0.0',
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
  };
});
