import { createRouter, createWebHashHistory } from 'vue-router';
const routes = [
  {
    path: '/',
    component: () => import('@/views/Home.vue'),
  },
];
const router = createRouter({
  history: createWebHashHistory('/admin/'),
  routes,
});
export default router;
