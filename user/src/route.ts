import { createRouter, createWebHashHistory } from 'vue-router';

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('./views/Home.vue'),
  },
  {
    path: '/personal-center',
    name: 'PersonalCenter',
    component: () => import('./views/user/PersonalCenter.vue'),
  },
  {
    path: '/history',
    name: 'BookStore',
    component: () => import('./views/book/ReadingHistory.vue'),
  },
  {
    path: '/book-shelf',
    name: 'BookShelf',
    component: () => import('./views/book/BookShelf.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('./views/auth/Login.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('./views/auth/Register.vue'),
  },
  {
    path: '/book-detail/:id',
    name: 'BookDetail',
    component: () => import('./views/book/BookInfo.vue'),
  },
  {
    path: '/book-read/:bookId/:contentId',
    name: 'BookRead',
    component: () => import('./views/book/BookRead.vue'),
  },
  {
    path: '/book-search',
    name: 'BookSearch',
    component: () => import('./views/book/BookSearch.vue'),
  },
  {
    path: '/forget-password',
    name: 'ForgetPassword',
    component: () => import('./views/auth/ForgetPassword.vue'),
  },
  {
    path: '/update-password',
    name: 'UpdatePassword',
    component: () => import('./views/auth/UpdatePassword.vue'),
  },
];
const router = createRouter({
  history: createWebHashHistory(),
  routes,
});
export default router;
