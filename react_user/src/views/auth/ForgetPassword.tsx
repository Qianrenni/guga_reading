import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Input, Button, message } from 'antd';
import { useApiUser } from '@guga-reading/shares';

export default function ForgetPassword() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    email: '',
    captcha: '',
    password: '',
    confirmPassword: '',
  });

  const verifyEmail = () => {
    useApiUser.getForgotPassword(form.email).then((res) => {
      if (res.success) {
        message.success('验证码已发送');
      } else {
        message.error(res.message);
      }
    });
  };

  const handleSubmit = () => {
    useApiUser
      .patchForgotPassword(
        form.email,
        form.captcha,
        form.password,
        form.confirmPassword,
      )
      .then((res) => {
        if (res.success) {
          message.success('密码重置成功');
          navigate('/login', { replace: true });
        } else {
          message.error(res.message);
        }
      });
  };

  return (
    <main>
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
          <h4 style={{ textAlign: 'center' }}>忘记密码</h4>

          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <Input
              placeholder="请输入邮箱"
              value={form.email}
              onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
            />
            <Button onClick={verifyEmail} style={{ fontSize: '0.8rem' }}>
              验证邮箱
            </Button>
          </div>

          <Input
            placeholder="请输入验证码"
            value={form.captcha}
            onChange={(e) => setForm((p) => ({ ...p, captcha: e.target.value }))}
          />

          <Input.Password
            placeholder="请输入新密码"
            value={form.password}
            onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
          />

          <Input.Password
            placeholder="确认新密码"
            value={form.confirmPassword}
            onChange={(e) =>
              setForm((p) => ({ ...p, confirmPassword: e.target.value }))
            }
          />

          <Button type="primary" block onClick={handleSubmit}>
            <span style={{ fontSize: '0.8rem' }}>重置密码</span>
          </Button>
        </div>
      </div>
    </main>
  );
}
