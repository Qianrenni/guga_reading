import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { includePaths } from '@/config';
import { useAuthStore } from '@/store';

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const authStore = useAuthStore.getState();
    const isLogin = authStore.isLogin();

    // Check if current path needs authentication
    if (
      includePaths.some((path) => location.pathname.startsWith(path)) &&
      !isLogin
    ) {
      authStore.setRedictUrl(location.pathname + location.search);
      navigate('/login', { replace: true });
    }
  }, [location.pathname]);

  return <Outlet />;
}
