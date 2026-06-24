import { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Drawer, Spin } from 'antd';
import {
  UpOutlined,
  DownOutlined,
  UnorderedListOutlined,
  SettingOutlined,
  BookOutlined,
  FullscreenOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { Book, Catalog } from '@guga-reading/types';
import {
  applySpacingToHtml,
  indexToCN,
  isHtml,
  useTitle,
  useApiBooks,
  useApiReport,
} from '@guga-reading/shares';
import { useBookStore, useReadSettingStore, useReadingHistoryStore } from '@/store';
import { useFullScreen } from '@/hooks';
import ReadSetting from '@/components/ReadSetting';

export default function BookRead() {
  const { bookId, contentId } = useParams<{
    bookId: string;
    contentId: string;
  }>();
  const navigate = useNavigate();
  const bookStore = useBookStore();
  const readingHistoryStore = useReadingHistoryStore();
  const useReadingSetting = useReadSettingStore();
  const fullScreen = useFullScreen();

  const containerRef = useRef<HTMLDivElement>(null);
  const [book, setBook] = useState<Book>({} as Book);
  const [catalog, setCatalog] = useState<Catalog[]>([]);
  const [content, setContent] = useState('');
  const [currentContentId, setCurrentContentId] = useState(-1);
  const [loading, setLoading] = useState(true);
  const [showCatalog, setShowCatalog] = useState(false);
  const [showReadSettings, setShowReadSettings] = useState(false);
  const [catalogAscOrder, setCatalogAscOrder] = useState(true);
  const [showBottomSettings, setShowBottomSettings] = useState(false);
  const [isLessThan768, setIsLessThan768] = useState(window.innerWidth < 768);

  useEffect(() => {
    const handler = () => setIsLessThan768(window.innerWidth < 768);
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  const currentContentIndex = useMemo(
    () => catalog.findIndex((item) => item.id === currentContentId),
    [catalog, currentContentId],
  );

  const computeCatalog = useMemo(() => {
    if (catalogAscOrder) return catalog;
    return [...catalog].reverse();
  }, [catalog, catalogAscOrder]);

  // Heartbeat timer
  const heartBeatRef = useRef<number>(-1);

  const startHeartBeat = useCallback(
    (bId: number, chId: number) => {
      stopHeartBeat();
      heartBeatRef.current = window.setInterval(() => {
        if (chId !== -1) {
          useApiReport.reportChapterRead(bId, chId, 'heartbeat');
        }
      }, 10000);
    },
    [],
  );

  const stopHeartBeat = () => {
    if (heartBeatRef.current !== -1) {
      clearInterval(heartBeatRef.current);
      heartBeatRef.current = -1;
    }
  };

  const run = async (chapterId: number) => {
    if (currentContentId === chapterId) return;
    setLoading(true);

    if (currentContentId !== -1) {
      useApiReport.reportChapterRead(book.id, currentContentId, 'exit');
      stopHeartBeat();
    }

    const { data } = await useApiBooks.getBookChapterById(
      book.id,
      chapterId,
    );
    const rawContent = data || '';
    const processedContent = isHtml(rawContent)
      ? rawContent
      : rawContent
          .split('\n')
          .map((item) => `<p class='text-read-indent'>${item}</p>`)
          .join('');

    setContent(applySpacingToHtml(processedContent));
    setCurrentContentId(chapterId);

    // Update reading history
    readingHistoryStore.update(book.id, chapterId, currentContentIndex + 1);
    useApiReport.reportChapterRead(book.id, chapterId, 'enter');
    startHeartBeat(book.id, chapterId);

    setTimeout(() => {
      containerRef.current?.scrollTo({ top: 0, behavior: 'instant' });
      setLoading(false);
    }, 0);
  };

  useEffect(() => {
    useTitle(catalog[currentContentIndex]?.title || '');
  }, [currentContentIndex]);

  useEffect(() => {
    if (catalog.length > 0) {
      useTitle(catalog[currentContentIndex]?.title || '');
    }
  }, [catalog]);

  useEffect(() => {
    return () => {
      if (currentContentId !== -1) {
        useApiReport.reportChapterRead(book.id, currentContentId, 'exit');
      }
      stopHeartBeat();
    };
  }, []);

  useEffect(() => {
    if (bookId && contentId) {
      const bId = parseInt(bookId);
      const cId = parseInt(contentId);
      setBook((prev) => ({ ...prev, id: bId }));
      useApiBooks.getBookById(bId).then((rawBook) => {
        if (rawBook.success && rawBook.data) setBook(rawBook.data);
      });
      useApiBooks.getCatalogById(bId).then((rawCatalog) => {
        if (rawCatalog.success && rawCatalog.data) {
          const mapped = rawCatalog.data.map((item, index) => ({
            ...item,
            title: `第${indexToCN(index + 1)}章 ${item.title}`,
          }));
          setCatalog(mapped);
          run(cId);
        }
      });
    }
  }, [bookId, contentId]);

  const prevChapter = () => {
    if (currentContentIndex > 0) {
      navigate(
        `/book-read/${book.id}/${computeCatalog[currentContentIndex - 1].id}`,
        { replace: true },
      );
    }
  };

  const nextChapter = () => {
    if (currentContentIndex < computeCatalog.length - 1) {
      navigate(
        `/book-read/${book.id}/${computeCatalog[currentContentIndex + 1].id}`,
        { replace: true },
      );
    }
  };

  const contentStyle: React.CSSProperties = {
    backgroundColor: useReadingSetting.readSettings.backgroundColor,
    color: useReadingSetting.readSettings.color,
    fontSize: `${useReadingSetting.readSettings.fontSize}px`,
    fontFamily: useReadingSetting.readSettings.fontFamily,
    lineHeight: `${useReadingSetting.readSettings.lineHeight}px`,
    letterSpacing: `${useReadingSetting.readSettings.letterSpacing}px`,
  };

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height: '100vh',
        overflow: 'auto',
      }}
    >
      <div
        className="book-read-container"
        style={{
          margin: '0 auto',
          display: 'flex',
          justifyContent: 'center',
          ...contentStyle,
        }}
      >
        {loading ? (
          <div style={{ height: '100vh', width: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <Spin size="large" />
          </div>
        ) : (
          <div
            className="book-read-content"
            style={{
              maxWidth: 700,
              width: '100%',
              padding: '0 1rem',
            }}
            onClick={() => {
              if (isLessThan768) setShowBottomSettings(true);
            }}
            dangerouslySetInnerHTML={{ __html: content }}
          />
        )}

        {/* Desktop sidebar */}
        {!isLessThan768 && (
          <div
            className="bg-card"
            style={{
              position: 'fixed',
              top: '50%',
              left: 'calc(50vw + 450px)',
              transform: 'translateY(-50%)',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.5rem',
              padding: '0.5rem',
              borderRadius: '0.5rem',
              boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
            }}
          >
            <SidebarItem icon={<UpOutlined />} label="上一章" onClick={prevChapter} />
            <SidebarItem icon={<DownOutlined />} label="下一章" onClick={nextChapter} />
            <SidebarItem
              icon={<UnorderedListOutlined />}
              label="目录"
              onClick={() => setShowCatalog(true)}
            />
            <SidebarItem
              icon={<SettingOutlined />}
              label="阅读设置"
              onClick={() => setShowReadSettings(true)}
            />
            <SidebarItem
              icon={<BookOutlined />}
              label="书籍详情"
              onClick={() => navigate(`/book-detail/${book.id}`)}
            />
            <SidebarItem icon={<FullscreenOutlined />} label="全屏" onClick={fullScreen} />
          </div>
        )}
      </div>

      {/* Catalog Drawer */}
      <Drawer
        title={
          <div>
            <h3 style={{ margin: 0 }}>{book.name}</h3>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                cursor: 'pointer',
              }}
              onClick={() => setCatalogAscOrder(!catalogAscOrder)}
            >
              <span>目录</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                {catalogAscOrder ? '升序' : '降序'}
                <SwapOutlined
                  style={{
                    transform: `rotateZ(90deg) rotateY(${catalogAscOrder ? 0 : 180}deg)`,
                  }}
                />
              </span>
            </div>
          </div>
        }
        placement="left"
        open={showCatalog}
        onClose={() => setShowCatalog(false)}
        width={isLessThan768 ? '70vw' : 480}
      >
        <div style={{ maxHeight: 'calc(100vh - 4.5rem)', overflow: 'auto' }}>
          {computeCatalog.map((item) => (
            <p
              key={item.id}
              onClick={() => {
                navigate(`/book-read/${book.id}/${item.id}`, { replace: true });
                setShowCatalog(false);
              }}
              style={{
                cursor: 'pointer',
                padding: '0.25rem 0.5rem',
                margin: 0,
                borderRadius: '0.3rem',
                background:
                  item.id === currentContentId
                    ? 'var(--primary-color, #6366f1)'
                    : undefined,
                color: item.id === currentContentId ? '#fff' : undefined,
              }}
              className="bg-hover-secondary"
            >
              {item.title}
            </p>
          ))}
        </div>
      </Drawer>

      {/* Mobile Bottom Settings Drawer */}
      <Drawer
        placement="bottom"
        open={showBottomSettings && isLessThan768}
        onClose={() => setShowBottomSettings(false)}
        height="auto"
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-evenly',
            flexWrap: 'wrap',
            gap: '1rem',
            padding: '0.5rem',
          }}
          onClick={() => setShowBottomSettings(false)}
        >
          <BottomItem icon={<UpOutlined />} label="上一章" onClick={prevChapter} />
          <BottomItem icon={<DownOutlined />} label="下一章" onClick={nextChapter} />
          <BottomItem
            icon={<UnorderedListOutlined />}
            label="目录"
            onClick={() => setShowCatalog(true)}
          />
          <BottomItem
            icon={<SettingOutlined />}
            label="阅读设置"
            onClick={() => setShowReadSettings(true)}
          />
          <BottomItem
            icon={<BookOutlined />}
            label="书籍详情"
            onClick={() => navigate(`/book-detail/${book.id}`)}
          />
          <BottomItem icon={<FullscreenOutlined />} label="全屏" onClick={fullScreen} />
        </div>
      </Drawer>

      {/* Read Settings Drawer */}
      <Drawer
        title="阅读设置"
        placement="left"
        open={showReadSettings}
        onClose={() => setShowReadSettings(false)}
      >
        <ReadSetting />
      </Drawer>
    </div>
  );
}

function SidebarItem({
  icon,
  label,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        cursor: 'pointer',
        padding: '0.5rem',
        borderRadius: '0.3rem',
      }}
      className="bg-hover-secondary"
    >
      <span style={{ fontSize: '24px' }}>{icon}</span>
      <span style={{ fontSize: '0.8rem' }}>{label}</span>
    </div>
  );
}

function BottomItem({
  icon,
  label,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        cursor: 'pointer',
      }}
    >
      <span style={{ fontSize: '24px' }}>{icon}</span>
      <span style={{ fontSize: '0.8rem' }}>{label}</span>
    </div>
  );
}
