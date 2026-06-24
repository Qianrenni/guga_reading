import { Spin } from 'antd';
import { useBookSearchStore } from '@/store/useBookSearchStore';
import BookItem from '@/components/BookItem';

export default function BookSearch() {
  const bookSearchStore = useBookSearchStore();
  const width = 90;
  const height = 120;

  return (
    <div style={{ width: '100%' }}>
      {bookSearchStore.loading ? (
        <div
          className="loading"
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            height: '80vh',
          }}
        >
          <Spin size="large" />
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, 350px)',
            gridAutoRows: '120px',
            gap: '1rem',
            padding: '1rem',
            overflow: 'auto',
          }}
        >
          {bookSearchStore.getSearchResult().map((book) => (
            <BookItem
              key={book.id}
              book={book}
              width={width}
              height={height}
            />
          ))}
        </div>
      )}
    </div>
  );
}
