import { createRouter, createWebHashHistory } from 'vue-router';
const routes = [
  {
    path: '/',
    component: () => import('@/views/Home.vue'),
    children: [
      {
        path: '/dashboard',
        component: () => import('@/views/Dashboard.vue'),
      },
      {
        path: '/book-manage',
        component: () => import('@/views/business/BookManage.vue'),
      },
      {
        path: '/user-manage',
        component: () => import('@/views/auth/UserManage.vue'),
      },
      {
        path: '/system-setting',
        component: () => import('@/views/system/SystemManage.vue'),
      },
      {
        path: '/permission-manage',
        component: () => import('@/views/auth/RightManage.vue'),
      },
      {
        path: 'draft-manage',
        component: () => import('@/views/business/DraftManage.vue'),
      },
    ],
  },
  {
    path: '/login',
    component: () => import('@/views/auth/LoginView.vue'),
  },
];
const router = createRouter({
  history: createWebHashHistory('/admin/'),
  routes,
});
export default router;
