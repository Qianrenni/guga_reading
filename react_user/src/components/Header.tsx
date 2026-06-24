import { Link, useNavigate } from 'react-router-dom';
import { Input, Switch } from 'antd';
import {
  HomeOutlined,
  BookOutlined,
  HistoryOutlined,
  UserOutlined,
  MobileOutlined,
  FullscreenOutlined,
} from '@ant-design/icons';
import { useBookSearchStore } from '@/store';
import { useFullScreen } from '@/hooks';

export default function Header() {
  const navigate = useNavigate();
  const bookSearchStore = useBookSearchStore();
  const toggleFullScreen = useFullScreen();

  const handleSearch = (value: string) => {
    bookSearchStore.setSearchKey(value);
    bookSearchStore.searchBook();
  };

  const handleThemeToggle = (checked: boolean) => {
    document.body.classList.toggle('dark-mode', checked);
  };

  return (
    <header
      className="bg-card container header-container"
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        width: '100%',
        borderBottom: '1px solid var(--primary-color, #6366f1)',
        padding: '0 1rem',
        height: '50px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <Link to="/" className="link-primary hidden-768">
          <h3 style={{ margin: 0 }}>咕嘎阅读</h3>
        </Link>
        <a
          href="http://49.235.107.221:8000/static/guga.apk"
          download
          target="_blank"
          rel="noreferrer"
          className="link-primary"
          style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}
        >
          <MobileOutlined />
          <span className="hidden-768" style={{ fontSize: '0.85rem' }}>
            移动端app
          </span>
        </a>
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '1rem',
        }}
      >
        <Input.Search
          placeholder="搜索书籍"
          onSearch={handleSearch}
          onClick={() => navigate('/book-search')}
          style={{ width: 180 }}
        />
        <div
          style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}
        >
          <Link
            to="/"
            className="link-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}
          >
            <HomeOutlined />
            <span className="hidden-768">书城</span>
          </Link>
          <Link
            to="/book-shelf"
            className="link-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}
          >
            <BookOutlined />
            <span className="hidden-768">书架</span>
          </Link>
          <Link
            to="/history"
            className="link-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}
          >
            <HistoryOutlined />
            <span className="hidden-768">历史记录</span>
          </Link>
          <Link
            to="/personal-center"
            className="link-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}
          >
            <UserOutlined />
            <span className="hidden-768">个人中心</span>
          </Link>
          <FullscreenOutlined
            onClick={toggleFullScreen}
            title="全屏模式"
            style={{ cursor: 'pointer' }}
          />
          <Switch
            checkedChildren="暗"
            unCheckedChildren="亮"
            onChange={handleThemeToggle}
            size="small"
          />
        </div>
      </div>
    </header>
  );
}
