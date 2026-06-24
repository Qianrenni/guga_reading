import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Input, Button, message, Image } from 'antd';
import { useApiCaptcha, useApiAuth } from '@guga-reading/shares';
import { useValidate } from '@guga-reading/shares';

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    email: '',
    captcha: '',
    x_captcha_id: '',
  });
  const [image, setImage] = useState('');
  const [isVerifyEmail, setIsVerifyEmail] = useState(false);

  const refreshCaptcha = async () => {
    if (image) URL.revokeObjectURL(image);
    const result = await useApiCaptcha.getCaptcha();
    if (result) {
      setForm((prev) => ({ ...prev, x_captcha_id: result.x_captcha_id }));
      setImage(result.imageUrl);
    }
  };

  useEffect(() => {
    refreshCaptcha();
    return () => {
      if (image) URL.revokeObjectURL(image);
    };
  }, []);

  const verifyEmail = async () => {
    if (isVerifyEmail) return;
    if (!useValidate.email(form.email)) {
      message.error('邮箱格式不正确');
      return;
    }
    setIsVerifyEmail(true);
    const { success, message: msg } = await useApiAuth.verifyEmail(form.email);
    setIsVerifyEmail(false);
    if (success) {
      message.success('发送成功，请到邮箱中验证');
    } else {
      message.error(msg);
    }
  };

  const register = async () => {
    if (!useValidate.email(form.email)) {
      message.error('邮箱格式不正确');
      return;
    }
    if (form.password !== form.confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }
    const { success, message: msg } = await useApiAuth.register(
      form.username,
      form.password,
      form.email,
      form.captcha,
      form.x_captcha_id,
    );
    if (success) {
      message.success('注册成功，请登录');
      navigate('/login', { replace: true });
    } else {
      message.error(msg);
    }
  };

  return (
    <div className="content-container">
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
        <h3 style={{ textAlign: 'center' }}>新用户注册</h3>

        <Input
          placeholder="请输入用户名"
          value={form.username}
          onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))}
        />
        <Input.Password
          placeholder="请输入密码"
          value={form.password}
          onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
        />
        <Input.Password
          placeholder="再次输入密码"
          value={form.confirmPassword}
          onChange={(e) =>
            setForm((p) => ({ ...p, confirmPassword: e.target.value }))
          }
        />

        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Input
            placeholder="请输入邮箱"
            value={form.email}
            onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
          />
          <Button onClick={verifyEmail} loading={isVerifyEmail}>
            {isVerifyEmail ? '验证中' : '验证邮箱'}
          </Button>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <Input
            placeholder="请输入验证码"
            value={form.captcha}
            onChange={(e) =>
              setForm((p) => ({ ...p, captcha: e.target.value }))
            }
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

        <Button type="primary" block onClick={register}>
          注册
        </Button>
      </div>
    </div>
  );
}
