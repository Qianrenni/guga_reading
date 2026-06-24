import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Avatar, Tag, Image, message } from 'antd';
import {
  SettingOutlined,
  EditOutlined,
  LogoutOutlined,
  FileTextOutlined,
  BookOutlined,
  HeartOutlined,
} from '@ant-design/icons';
import { useAuthStore, useReadingHistoryStore } from '@/store';
import { useApiAuthorApplication } from '@guga-reading/shares';

export default function PersonalCenter() {
  const navigate = useNavigate();
  const userStore = useAuthStore();
  const readhistoryStore = useReadingHistoryStore();

  const [authorStatus, setAuthorStatus] = useState<string | null>(null);

  const currentRead = useMemo(
    () => readhistoryStore.getReadingHistory().slice(0, 3),
    [readhistoryStore.readingHistory],
  );

  const width = 64;
  const height = 96;

  const authorBtnText = useMemo(() => {
    if (authorStatus === 'approved') return '已是作者';
    if (authorStatus === 'pending') return '申请审核中';
    if (authorStatus === 'rejected') return '重新申请';
    return '申请成为作者';
  }, [authorStatus]);

  useEffect(() => {
    readhistoryStore.get();
    loadAuthorApplication();
  }, []);

  const loadAuthorApplication = async () => {
    const { success, data } = await useApiAuthorApplication.getMyApplication();
    if (success && data) {
      setAuthorStatus(data.status);
    }
  };

  const openApplyDialog = async () => {
    if (authorStatus === 'approved') {
      message.info('您已经是作者了');
      return;
    }
    const reason = prompt('请输入申请理由：');
    if (!reason || reason.trim() === '') {
      message.warning('请输入申请理由');
      return;
    }
    const { success, message: msg } = await useApiAuthorApplication.apply(
      reason.trim(),
    );
    if (success) {
      message.success('申请已提交，请等待管理员审核');
      setAuthorStatus('pending');
    } else {
      message.error(msg);
    }
  };

  const exitHandler = () => {
    userStore.clearUser();
    userStore.clearToken();
    window.location.reload();
  };

  const user = userStore.getUser();

  return (
    <div
      className="personal-center-container"
      style={{
        maxWidth: 1200,
        margin: '0 auto',
        display: 'flex',
        gap: '1rem',
        padding: '1rem',
      }}
    >
      {/* Left panel */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {/* User card */}
        <div
          className="bg-card"
          style={{
            width: 300,
            padding: '1rem',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <Avatar
            size={100}
            src={user?.avatar || '/figure.webp'}
            icon={!user?.avatar ? <UserOutlinedIcon /> : undefined}
          />
          <h2 style={{ margin: 0 }}>{user?.userName}</h2>
          <p style={{ color: user?.isActive ? 'green' : 'red', margin: 0 }}>
            {user?.isActive ? 'Active' : 'Inactive'}
          </p>
          <div
            className="bg-body"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.5rem',
              borderRadius: '0.5rem',
              width: '100%',
            }}
          >
            <SettingOutlined style={{ color: '#6366f1' }} />
            <div>
              <p style={{ margin: 0 }}>Email</p>
              <p style={{ margin: 0, fontSize: '0.8rem' }}>{user?.email}</p>
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              gap: '0.5rem',
              width: '100%',
            }}
          >
            <Button
              type="primary"
              onClick={() => navigate('/update-password')}
              style={{ flex: 1 }}
            >
              <EditOutlined /> 修改密码
            </Button>
            <Button onClick={exitHandler} style={{ flex: 1 }}>
              <LogoutOutlined /> 退出登录
            </Button>
          </div>

          <div style={{ width: '100%' }}>
            <Button
              type="primary"
              block
              disabled={authorStatus === 'approved'}
              onClick={openApplyDialog}
            >
              <FileTextOutlined /> {authorBtnText}
            </Button>
          </div>
        </div>

        {/* Reading stats */}
        <div
          className="bg-card"
          style={{
            width: 300,
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <BookOutlined style={{ fontSize: '24px', color: '#6366f1' }} />
            <h3 style={{ margin: 0 }}>Reading Statistics</h3>
          </div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              width: '100%',
            }}
          >
            <span>Books Read</span>
            <h4 style={{ margin: 0 }}>111</h4>
          </div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              width: '100%',
            }}
          >
            <span>Pages Read</span>
            <h4 style={{ margin: 0 }}>111</h4>
          </div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              width: '100%',
            }}
          >
            <span>Current Streak</span>
            <h4 style={{ margin: 0, color: '#6366f1' }}>18days</h4>
          </div>
          <hr />
          <p style={{ width: '100%' }}>Favorite Genres</p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
            {['Fantasy', 'Non-fiction', 'Horror', 'Thriller', 'Romance'].map(
              (item) => (
                <Tag key={item}>{item}</Tag>
              ),
            )}
          </div>
        </div>
      </div>

      {/* Right panel */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          gap: '1rem',
        }}
      >
        {/* Currently Reading */}
        <div
          className="bg-card"
          style={{
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
            width: '100%',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <HeartOutlined style={{ fontSize: '24px', color: '#6366f1' }} />
            <h3 style={{ margin: 0 }}>Currently Reading</h3>
          </div>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '0.5rem',
              marginTop: '0.5rem',
            }}
          >
            {currentRead.map((item) => (
              <div
                key={item.id}
                className="bg-body"
                style={{ cursor: 'pointer', padding: '0.25rem' }}
                onClick={() =>
                  navigate(
                    `/book-read/${item.id}/${item.lastChapterId}`,
                  )
                }
              >
                <Image
                  src={item.cover}
                  height={height}
                  width={width}
                  preview={false}
                  fallback="/figure.webp"
                  style={{ objectFit: 'cover' }}
                />
              </div>
            ))}
          </div>
        </div>

        {/* Reading Goals */}
        <div
          className="bg-card"
          style={{
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
            width: '100%',
          }}
        >
          <h3 style={{ margin: 0 }}>Reading Goals</h3>
          <div
            style={{
              display: 'flex',
              gap: '1rem',
              marginTop: '0.5rem',
            }}
          >
            <div
              className="line-gradient-purple"
              style={{
                flex: 1,
                padding: '1rem',
                borderRadius: '0.5rem',
                color: '#fff',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
            >
              <p style={{ margin: 0 }}>This Month</p>
              <h2 style={{ margin: 0 }}>8/12 books</h2>
            </div>
            <div
              className="line-gradient-green"
              style={{
                flex: 1,
                padding: '1rem',
                borderRadius: '0.5rem',
                color: '#fff',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
            >
              <p style={{ margin: 0 }}>This Year</p>
              <h2 style={{ margin: 0 }}>24/50 books</h2>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function UserOutlinedIcon() {
  return (
    <svg viewBox="64 64 896 896" width="1em" height="1em" fill="currentColor">
      <path d="M858.5 763.6a374 374 0 00-80.6-119.5 375.63 375.63 0 00-119.5-80.6c-.4-.2-.8-.3-1.2-.5C719.5 518 760 444.7 760 362c0-137-111-248-248-248S264 225 264 362c0 82.7 40.5 156 102.8 201.1-.4.2-.8.3-1.2.5-44.8 18.9-85 46-119.5 80.6a375.63 375.63 0 00-80.6 119.5A371.7 371.7 0 00136 901.8a8 8 0 008 8.2h60c4.4 0 7.9-3.5 8-7.8 2-77.2 33-149.5 87.8-204.3 56.7-56.7 132-87.9 212.2-87.9s155.5 31.2 212.2 87.9C779 752.7 810 825 812 902.2c.1 4.4 3.6 7.8 8 7.8h60a8 8 0 008-8.2c-1-47.8-10.9-94.3-29.5-138.2zM512 534c-91.8 0-168-76.2-168-168s76.2-168 168-168 168 76.2 168 168-76.2 168-168 168z" />
    </svg>
  );
}
