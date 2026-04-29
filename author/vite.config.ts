import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';
// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const { QYANI_COMPONENTS_PATH, VITE_BASE_URL } = env;
  console.log(`QYANI_COMPONENTS_PATH=${QYANI_COMPONENTS_PATH}`);
  console.log(`VITE_BASE_URL=${VITE_BASE_URL}`);
  return {
    base: '/author/',
    plugins: [vue()],
    server: {
      port: 8080,
      host: 'localhost',
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        ...(QYANI_COMPONENTS_PATH && {
          'qyani-components': path.resolve(__dirname, QYANI_COMPONENTS_PATH),
        }),
      },
    },
  };
});
