import { useNavigate } from 'react-router-dom';
import type { Book } from '@guga-reading/types';
import { Image } from 'antd';

interface BookItemProps {
  book: Book;
  width: number;
  height: number;
}

export default function BookItem({ book, width, height }: BookItemProps) {
  const navigate = useNavigate();

  return (
    <div
      className="inner-container mouse-cursor"
      onClick={() => navigate(`/book-detail/${book.id}`)}
      style={{ cursor: 'pointer' }}
    >
      <Image
        src={book.cover}
        width={width}
        height={height}
        preview={false}
        fallback="/figure.webp"
        style={{ objectFit: 'cover' }}
      />
      <div
        className="container-flex-1 inner-container-column container-h100 overflow-hidden"
        style={{ marginLeft: '0.5rem' }}
      >
        <h4>{book.name}</h4>
        <h6 style={{ color: '#888' }}>作者: {book.author}</h6>
        <p
          className="inner-container-column container-flex-1 text-muted text-08rem text-overflow"
          style={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            fontSize: '0.8rem',
            color: '#999',
          }}
        >
          {book.description}
        </p>
      </div>
    </div>
  );
}
