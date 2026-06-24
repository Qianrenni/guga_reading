import { create } from 'zustand';
import type { ShelfItem } from '@guga-reading/types';
import { useReadingHistoryStore } from './useReadingHistoryStore';
import { useBookStore } from './useBookStore';
import { useApiBookShelf } from '@guga-reading/shares';
import { message } from 'antd';

interface BookShelfState {
  bookShelf: ShelfItem[];
  loading: boolean;

  getBookShelf: () => ShelfItem[];
  get: () => Promise<void>;
  add: (bookId: number) => Promise<void>;
  delete: (bookId: number) => Promise<void>;
}

export const useBookShelfStore = create<BookShelfState>((set, get) => ({
  bookShelf: [],
  loading: false,

  getBookShelf: () => get().bookShelf,

  async get() {
    const state = get();
    if (state.bookShelf.length > 0 || state.loading) {
      return;
    }
    set({ loading: true });
    const { success, data } = await useApiBookShelf.get();
    if (success && data && data.length > 0) {
      const bookStore = useBookStore.getState();
      const readingHistoryStore = useReadingHistoryStore.getState();
      const books = await bookStore.getBookByList(
        data.map((item) => item.bookId),
      );
      const historyItems = (
        await Promise.all(
          data.map((item) => readingHistoryStore.getSingle(item.bookId)),
        )
      ).filter((item) => item !== undefined);
      const shelfItems = data.map((item) => ({
        ...item,
        ...books.find((book) => book.id === item.bookId),
        ...(historyItems.find(
          (historyItem) => historyItem.bookId === item.bookId,
        ) ?? {
          lastChapterId: -1,
          lastPosition: 0,
          lastReadAt: '',
        }),
      }));
      set({ bookShelf: shelfItems as ShelfItem[], loading: false });
    } else {
      set({ loading: false });
    }
  },

  async add(bookId: number) {
    const state = get();
    if (state.bookShelf.findIndex((item) => item.bookId === bookId) !== -1) {
      return;
    }
    const bookStore = useBookStore.getState();
    const readingHistoryStore = useReadingHistoryStore.getState();
    const [responseAdd, book, history] = await Promise.all([
      useApiBookShelf.add(bookId),
      bookStore.getBookById(bookId),
      readingHistoryStore.getSingle(bookId),
    ]);
    if (responseAdd.success) {
      const newShelf = [
        {
          ...book,
          ...(history ?? {
            lastChapterId: -1,
            lastPosition: 0,
            lastReadAt: '',
          }),
        } as ShelfItem,
        ...state.bookShelf,
      ];
      set({ bookShelf: newShelf });
    }
    message.success('添加成功');
  },

  async delete(bookId: number) {
    const { success } = await useApiBookShelf.delete(bookId);
    if (success) {
      const newShelf = get().bookShelf.filter((item) => item.bookId !== bookId);
      set({ bookShelf: newShelf });
      message.success('删除成功');
    } else {
      message.error('删除失败');
    }
  },
}));
