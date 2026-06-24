import { useEffect, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Input, Button, Checkbox, message, Image } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useApiCaptcha, useApiAuth } from '@guga-reading/shares';
import { useAuthStore } from '@/store';
import axios from 'axios';

export default function Login() {
  const navigate = useNavigate();
  const authStore = useAuthStore();

  const [form, setForm] = useState({
    username: '',
    password: '',
    captcha: '',
    x_captcha_id: '',
    remember: true,
  });
  const [image, setImage] = useState('');
  const [loading, setLoading] = useState(false);

  // If already logged in, redirect
  useEffect(() => {
    if (authStore.isLogin()) {
      navigate('/', { replace: true });
    }
    authStore.initial();
  }, []);

  // Watch login state
  useEffect(() => {
    if (authStore.isLogin()) {
      const url = authStore.getRedictUrl();
      if (url) {
        authStore.setRedictUrl(null);
        navigate(url, { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    }
  }, [authStore.user]);

  const refreshCaptcha = useCallback(async () => {
    if (image) URL.revokeObjectURL(image);
    const result = await useApiCaptcha.getCaptcha();
    if (result) {
      setForm((prev) => ({ ...prev, x_captcha_id: result.x_captcha_id }));
      setImage(result.imageUrl);
    }
  }, [image]);

  useEffect(() => {
    refreshCaptcha();
  }, []);

  const handleLogin = async () => {
    setLoading(true);
    const { success, message: msg, data } = await useApiAuth.login(
      form.username,
      form.password,
      form.captcha,
      form.x_captcha_id,
    );
    if (success && data) {
      message.success('登录成功');
      authStore.setRemeber(form.remember);
      authStore.setToken(data.accessToken, data.refreshToken, data.tokenType);
      authStore.setUser(data.user);
      axios.defaults.headers.common['Authorization'] =
        `${data.tokenType} ${data.accessToken}`;
    } else {
      message.error(msg);
      refreshCaptcha();
    }
    setLoading(false);
  };

  const handleKeyUp = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleLogin();
  };

  return (
    <div className="content-container" onKeyUp={handleKeyUp}>
      <div
        className="bg-card container-column"
        style={{
          padding: '2rem',
          borderRadius: '1rem',
          width: '100%',
          maxWidth: '400px',
          display: 'flex',
          flexDirection: 'column',
          gap: '1rem',
        }}
      >
        <h3 style={{ textAlign: 'center' }}>用户登录</h3>

        <Input
          prefix={<UserOutlined />}
          placeholder="请输入用户名"
          value={form.username}
          onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))}
        />

        <Input.Password
          prefix={<LockOutlined />}
          placeholder="请输入密码"
          value={form.password}
          onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
        />

        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Input
            placeholder="请输入验证码"
            value={form.captcha}
            onChange={(e) => setForm((p) => ({ ...p, captcha: e.target.value }))}
          />
          {image && (
            <Image
              src={image}
              width={80}
              height={30}
              preview={false}
              onClick={refreshCaptcha}
              style={{ cursor: 'pointer' }}
            />
          )}
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            fontSize: '0.8rem',
          }}
        >
          <Checkbox
            checked={form.remember}
            onChange={(e) => setForm((p) => ({ ...p, remember: e.target.checked }))}
          >
            记住我
          </Checkbox>
          <Link to="/forget-password">忘记密码?</Link>
        </div>

        <Button type="primary" block onClick={handleLogin} loading={loading}>
          {loading ? '登录中...' : '登录'}
        </Button>

        <div style={{ textAlign: 'center' }}>
          <Link to="/register" style={{ fontSize: '0.8rem' }}>
            没有账号?立即注册
          </Link>
        </div>
      </div>
    </div>
  );
}
