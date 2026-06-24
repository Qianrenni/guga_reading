import { useEffect, useRef, useState } from 'react';
import { Skeleton } from 'antd';
import { useBookStore } from '@/store';
import BookItem from '@/components/BookItem';

export default function Home() {
  const bookStore = useBookStore();
  const [selectedCategory, setSelectedCategory] = useState(
    bookStore.currentCategory || '',
  );
  const scrollRef = useRef<HTMLDivElement>(null);
  const width = 90;
  const height = 120;

  useEffect(() => {
    bookStore.getBookCategory().then(() => {
      if (!selectedCategory && bookStore.categories.length > 0) {
        setSelectedCategory(bookStore.categories[0]);
      }
    });
  }, []);

  useEffect(() => {
    if (selectedCategory) {
      bookStore.setCurrentCategory(selectedCategory);
      bookStore.addBookByCategory();
    }
  }, [selectedCategory]);

  // Restore scroll position
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTo({ top: bookStore.scrollTo, behavior: 'instant' as ScrollBehavior });
    }
  }, []);

  const handleScroll = () => {
    if (scrollRef.current) {
      bookStore.setScrollTo(scrollRef.current.scrollTop);
      const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
      if (scrollHeight - scrollTop - clientHeight < 100) {
        bookStore.addBookByCategory();
      }
    }
  };

  const books = bookStore.getCategoryBook();

  return (
    <div
      className="container-column bg-card container-w100"
      style={{ width: '100%' }}
    >
      <div
        className="container-banner"
        style={{
          display: 'flex',
          gap: '0.5rem',
          padding: '0.5rem',
          overflowX: 'auto',
          margin: '0 auto',
          flexWrap: 'nowrap',
        }}
      >
        {bookStore.categories.map((value) => (
          <span
            key={value}
            onClick={() => setSelectedCategory(value)}
            style={{
              cursor: 'pointer',
              padding: '0.25rem 0.5rem',
              borderRadius: '0.3rem',
              whiteSpace: 'nowrap',
              background:
                selectedCategory === value
                  ? 'var(--primary-color, #6366f1)'
                  : undefined,
              color: selectedCategory === value ? '#fff' : undefined,
            }}
            className="bg-hover-secondary"
          >
            {value}
          </span>
        ))}
      </div>
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        style={{
          height: 'calc(100vh - 6rem)',
          overflowY: 'auto',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, 350px)',
          gridAutoRows: '120px',
          gap: '1rem',
          padding: '1rem',
          borderTop: '1px solid var(--primary-color, #6366f1)',
        }}
      >
        {books.map((book) => (
          <BookItem key={book.id} book={book} width={width} height={height} />
        ))}
        {bookStore.loading &&
          Array.from({ length: 25 }).map((_, i) => (
            <Skeleton key={i} active avatar paragraph={{ rows: 2 }} />
          ))}
      </div>
    </div>
  );
}
