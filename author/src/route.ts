import { createRouter, createWebHashHistory } from 'vue-router';

const routs = [
  {
    path: '/',
    name: 'Home',
    component: () => import('./views/book/HomeView.vue'),
    children: [
      // {
      //     path: '/data-statistic',
      //     name: 'DataStatistic',
      //     component: () => import('./views/navigation/DataSumary.vue')
      // },
      {
        path: '/my-book',
        name: 'MyBook',
        component: () => import('./views/book/MyBook.vue'),
      },
      {
        path: '/draft-manage',
        name: 'DraftManage',
        component: () => import('./views/book/DraftManage.vue'),
      },
      {
        path: '/account-setting',
        name: 'AccountSetting',
        component: () => import('./views/navigation/AccountSetting.vue'),
      },
      // {
      //     path: '/system-setting',
      //     name: 'SystemSetting',
      //     component: () => import('./views/navigation/SystemSetting.vue')
      // },
      {
        path: '/book-detail/:id',
        name: 'BookDetail',
        component: () => import('./components/book/BookDetail.vue'),
      },
      {
        path: '/book-edit',
        name: 'BookEdit',
        query: {
          bookId: 'bookId',
          sortOrder: 'sortOrder',
          hasDraft: 'hasDraft',
          chapterId: 'chapterId',
        },
        component: () => import('./views/book/BookEdit.vue'),
      },
      {
        path: '/create-book',
        name: 'CreateBook',
        component: () => import('./views/book/CreateBook.vue'),
      },
      {
        path: '/edit/book-meta',
        name: 'BookInfoEdit',
        component: () => import('./views/book/BookInfoEdit.vue'),
        query: { id: 'id', hasDraft: 'hasDraft' },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('./views/auth/LoginView.vue'),
  },
];
export const router = createRouter({
  history: createWebHashHistory('/author/'),
  routes: routs,
});
