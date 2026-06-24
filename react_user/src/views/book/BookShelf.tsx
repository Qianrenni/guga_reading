import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Image, Tag } from 'antd';
import { CloseOutlined, UserOutlined, HistoryOutlined } from '@ant-design/icons';
import { useBookShelfStore } from '@/store';

export default function BookShelf() {
  const navigate = useNavigate();
  const shelfStore = useBookShelfStore();
  const [showClose, setShowClose] = useState(false);
  const [isLessThan768, setIsLessThan768] = useState(window.innerWidth < 768);

  const width = 96;
  const height = 144;

  useEffect(() => {
    const handler = () => setIsLessThan768(window.innerWidth < 768);
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  useEffect(() => {
    shelfStore.get();
  }, []);

  return (
    <div className="container-column">
      {!isLessThan768 && (
        <div style={{ padding: '0.5rem' }}>
          <Button onClick={() => setShowClose(!showClose)}>
            批量管理
          </Button>
        </div>
      )}

      <div
        style={{
          width: '100%',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
          gap: '0.5rem',
          padding: '0.25rem',
        }}
      >
        {shelfStore.getBookShelf().map((item) => (
          <div
            key={item.id}
            className="bg-card"
            style={{
              display: 'flex',
              gap: '0.5rem',
              padding: '0.5rem',
              borderRadius: '0.5rem',
              position: 'relative',
            }}
          >
            {/* Close button for desktop batch mode */}
            {showClose && !isLessThan768 && (
              <div
                className="inner-close"
                onClick={() => shelfStore.delete(item.id)}
                style={{ cursor: 'pointer' }}
              >
                <CloseOutlined />
              </div>
            )}

            <Image
              src={item.cover}
              height={height}
              width={width}
              preview={false}
              fallback="/figure.webp"
              style={{ objectFit: 'cover' }}
            />

            <div
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                gap: '0.25rem',
              }}
            >
              <h5 style={{ margin: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {item.name}
              </h5>

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <UserOutlined style={{ fontSize: '14px' }} />
                <span style={{ fontSize: '0.8rem' }}>{item.author}</span>
              </div>

              {item.lastReadAt && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                  <HistoryOutlined style={{ fontSize: '14px' }} />
                  <span style={{ fontSize: '0.8rem' }}>
                    上次阅读: {item.lastReadAt.split('T')[0]}
                  </span>
                </div>
              )}

              <div>
                <Button
                  type="primary"
                  size="small"
                  onClick={() =>
                    navigate(
                      `/book-read/${item.id}/${item.lastChapterId}`,
                    )
                  }
                >
                  继续阅读
                </Button>
              </div>

              {/* Mobile delete button */}
              {isLessThan768 && (
                <div
                  className="delete-768"
                  onClick={() => shelfStore.delete(item.id)}
                  style={{
                    padding: '0.5rem',
                    textAlign: 'center',
                    borderRadius: '0.3rem',
                    cursor: 'pointer',
                  }}
                >
                  删除
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
