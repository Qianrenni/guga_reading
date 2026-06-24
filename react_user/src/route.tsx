import { createBrowserRouter } from 'react-router-dom';
import App from './App';
import Index from './views/Index';
import Home from './views/Home';
import PersonalCenter from './views/user/PersonalCenter';
import Login from './views/auth/Login';
import Register from './views/auth/Register';
import ForgetPassword from './views/auth/ForgetPassword';
import UpdatePassword from './views/auth/UpdatePassword';
import BookInfo from './views/book/BookInfo';
import BookSearch from './views/book/BookSearch';
import BookRead from './views/book/BookRead';
import BookShelf from './views/book/BookShelf';
import ReadingHistory from './views/book/ReadingHistory';

const routes = [
  {
    path: '/',
    element: <App />,
    children: [
      {
        element: <Index />,
        children: [
          { index: true, element: <Home /> },
          { path: 'personal-center', element: <PersonalCenter /> },
          { path: 'history', element: <ReadingHistory /> },
          { path: 'book-shelf', element: <BookShelf /> },
          { path: 'login', element: <Login /> },
          { path: 'register', element: <Register /> },
          { path: 'book-detail/:id', element: <BookInfo /> },
          { path: 'book-search', element: <BookSearch /> },
          { path: 'forget-password', element: <ForgetPassword /> },
          { path: 'update-password', element: <UpdatePassword /> },
        ],
      },
      {
        path: 'book-read/:bookId/:contentId',
        element: <BookRead />,
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
