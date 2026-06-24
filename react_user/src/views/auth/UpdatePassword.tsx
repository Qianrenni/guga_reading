import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Input, Button, message } from 'antd';
import { useApiUser } from '@guga-reading/shares';
import { useAuthStore } from '@/store';

export default function UpdatePassword() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    email: '',
    oldPassword: '',
    password: '',
    confirmPassword: '',
  });

  const handleSubmit = () => {
    useApiUser
      .updatePassword(
        form.email,
        form.oldPassword,
        form.password,
        form.confirmPassword,
      )
      .then((res) => {
        if (res.success) {
          message.success('密码修改成功');
          const authStore = useAuthStore.getState();
          authStore.clearUser();
          authStore.clearToken();
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
          <h4 style={{ textAlign: 'center' }}>修改密码</h4>

          <Input
            placeholder="请输入邮箱"
            value={form.email}
            onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
          />

          <Input.Password
            placeholder="请输入旧密码"
            value={form.oldPassword}
            onChange={(e) =>
              setForm((p) => ({ ...p, oldPassword: e.target.value }))
            }
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
            <span style={{ fontSize: '0.8rem' }}>修改密码</span>
          </Button>
        </div>
      </div>
    </main>
  );
}
