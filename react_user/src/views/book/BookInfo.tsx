import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Image, Tabs, Tag, Spin } from 'antd';
import {
  UserOutlined,
  CalendarOutlined,
  BookOutlined,
  EyeOutlined,
  StarOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import type { Book, Catalog } from '@guga-reading/types';
import { indexToCN, useApiBooks, useTitle } from '@guga-reading/shares';
import { useBookStore } from '@/store';

export default function BookInfo() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const bookStore = useBookStore();

  const [book, setBook] = useState<Book>({} as Book);
  const [catalog, setCatalog] = useState<Catalog[]>([]);
  const [relatedBooks, setRelatedBooks] = useState<Book[]>([]);
  const [tabIndex, setTabIndex] = useState('0');
  const [showFastCatalog, setShowFastCatalog] = useState(true);
  const [loading, setLoading] = useState(true);

  const [imgWidth, setImgWidth] = useState(window.innerWidth < 768 ? 144 : 180);
  const [imgHeight, setImgHeight] = useState(window.innerWidth < 768 ? 192 : 240);

  useEffect(() => {
    const handler = () => {
      const w = window.innerWidth < 768 ? 144 : 180;
      const h = window.innerWidth < 768 ? 192 : 240;
      setImgWidth(w);
      setImgHeight(h);
    };
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  const initial = async (bookId: number) => {
    setLoading(true);
    const [rawBook, rawCatalog] = await Promise.all([
      bookStore.getBookById(bookId),
      bookStore.getCatalogById(bookId),
    ]);
    setBook(rawBook);
    setCatalog(
      rawCatalog.map((item, index) => ({
        ...item,
        title: `第${indexToCN(index + 1)}章 ${item.title}`,
      })),
    );
    useApiBooks
      .getRecommendBook(rawBook.tags?.split(',').join(' '))
      .then((result) => {
        if (result.success && result.data) {
          setRelatedBooks(result.data.filter((item) => item.id !== bookId));
        }
      });
    useTitle(rawBook.name);
    setLoading(false);
  };

  useEffect(() => {
    if (id) {
      initial(parseInt(id));
    }
  }, [id]);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
        <Spin size="large" />
      </div>
    );
  }

  const containerStyle: React.CSSProperties = {
    maxWidth: 1200,
    margin: '0 auto',
    display: 'flex',
    gap: '1rem',
    padding: '1rem',
  };

  const leftStyle: React.CSSProperties = {
    width: 750,
  };

  const rightStyle: React.CSSProperties = {
    width: 450,
  };

  return (
    <div style={containerStyle} className="container-row-768-column">
      {/* Left Section */}
      <div style={{ ...leftStyle }} className="inner-container-column container-flex-1 left">
        {/* Book Info Card */}
        <div
          className="bg-card"
          style={{
            display: 'flex',
            gap: '1rem',
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <Image
            src={book.cover}
            width={imgWidth}
            height={imgHeight}
            preview={false}
            fallback="/figure.webp"
            style={{ objectFit: 'cover', borderRadius: '0.5rem' }}
          />
          <div style={{ color: '#666', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <h3 style={{ margin: 0 }}>{book.name}</h3>
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <UserOutlined />
                <h5 style={{ margin: 0 }}>{book.author}</h5>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <CalendarOutlined />
                <h5 style={{ margin: 0 }}>{book.createdAt?.split('T')[0]}</h5>
              </div>
            </div>
            {book.tags && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
                {book.tags.split(',').map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </div>
            )}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem', fontSize: '0.8rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <BookOutlined />
                <span>{book.totalChapter} 章节</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <EyeOutlined />
                <span>12345阅读</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <StarOutlined />
                <span>12345收藏</span>
              </div>
            </div>
          </div>
        </div>

        {/* Tabs: Description & Catalog */}
        <div
          className="bg-card"
          style={{
            marginTop: '1rem',
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <Tabs
            activeKey={tabIndex}
            onChange={setTabIndex}
            items={[
              {
                key: '0',
                label: '书籍简介',
                children: (
                  <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                    {book.description?.split(/\s+/).map((line, i) => (
                      <p
                        key={i}
                        style={{ textIndent: '1rem', margin: '0.25rem 0' }}
                      >
                        {line}
                      </p>
                    ))}
                  </div>
                ),
              },
              {
                key: '1',
                label: '目录',
                children: (
                  <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                    {catalog.map((item) => (
                      <p
                        key={item.id}
                        onClick={() =>
                          navigate(`/book-read/${book.id}/${item.id}`)
                        }
                        style={{
                          cursor: 'pointer',
                          padding: '0.25rem 0.5rem',
                          margin: 0,
                          fontSize: '0.85rem',
                        }}
                        className="bg-hover-secondary"
                      >
                        {item.title}
                      </p>
                    ))}
                  </div>
                ),
              },
            ]}
          />
        </div>
      </div>

      {/* Right Section */}
      <div style={{ ...rightStyle }} className="right inner-container-column">
        {/* Quick Catalog */}
        <div
          className="bg-card"
          style={{
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
              <UnorderedListOutlined />
              <h4 style={{ margin: 0 }}>快速目录</h4>
            </div>
            <span
              style={{ color: '#6366f1', cursor: 'pointer', fontSize: '0.85rem' }}
              onClick={() => setShowFastCatalog(!showFastCatalog)}
            >
              {showFastCatalog ? '收起' : '展开'}
            </span>
          </div>
          {showFastCatalog && (
            <div style={{ maxHeight: 300, overflowY: 'auto', marginTop: '0.5rem' }}>
              {catalog.map((item) => (
                <p
                  key={item.id}
                  style={{
                    cursor: 'pointer',
                    padding: '0.25rem 0.5rem',
                    margin: 0,
                    fontSize: '0.85rem',
                  }}
                  className="bg-hover-secondary"
                >
                  {item.title}
                </p>
              ))}
            </div>
          )}
        </div>

        {/* Related Books */}
        <div
          className="bg-card"
          style={{
            marginTop: '1rem',
            padding: '1rem',
            borderRadius: '1rem',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <h4 style={{ textAlign: 'left', margin: 0 }}>相关推荐</h4>
          <div
            style={{
              maxHeight: 600,
              overflowY: 'auto',
              display: 'grid',
              gridTemplateColumns: '100%',
              gap: '0.5rem',
              marginTop: '0.5rem',
            }}
          >
            {relatedBooks.map((item) => (
              <div
                key={item.id}
                style={{ display: 'flex', gap: '0.5rem', cursor: 'pointer' }}
                onClick={() => initial(item.id)}
              >
                <Image
                  src={item.cover}
                  height={96}
                  width={72}
                  preview={false}
                  fallback="/figure.webp"
                  style={{ objectFit: 'cover' }}
                />
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                  <p style={{ color: '#666' }}>{item.name}</p>
                  <p
                    title={item.description}
                    style={{
                      fontSize: '0.8rem',
                      color: '#999',
                      textIndent: '1rem',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {item.description}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
