import { create } from 'zustand';
import type { Book, BookReadingProgress } from '@guga-reading/types';
import { useApiBookReadingProgress } from '@guga-reading/shares';
import { useBookStore } from './useBookStore';
import { message } from 'antd';

type HistoryItem = BookReadingProgress & Book;

interface ReadingHistoryState {
  readingHistory: HistoryItem[];
  loading: boolean;

  getReadingHistory: () => HistoryItem[];
  get: () => Promise<void>;
  update: (
    bookId: number,
    chapterId: number,
    lastPosition?: number,
  ) => Promise<void>;
  getSingle: (bookId: number) => Promise<HistoryItem | undefined>;
  delete: (bookId: number) => Promise<void>;
}

export const useReadingHistoryStore = create<ReadingHistoryState>(
  (set, get) => ({
    readingHistory: [],
    loading: false,

    getReadingHistory: () => get().readingHistory,

    async get() {
      const state = get();
      if (state.readingHistory.length > 0 || state.loading) {
        return;
      }
      set({ loading: true });
      const { success, data } = await useApiBookReadingProgress.get();
      if (success && data) {
        const bookStore = useBookStore.getState();
        const books = await bookStore.getBookByList(
          data.map((item) => item.bookId),
        );
        const historyItems = data.map((item) => ({
          ...item,
          ...books.find((book) => book.id === item.bookId),
        }));
        set({ readingHistory: historyItems as HistoryItem[], loading: false });
      } else {
        set({ loading: false });
      }
    },

    async update(bookId: number, chapterId: number, lastPosition: number = 0) {
      const state = get();
      const index = state.readingHistory.findIndex(
        (item) => item.bookId === bookId,
      );
      if (index !== -1) {
        const newHistory = [...state.readingHistory];
        const [item] = newHistory.splice(index, 1);
        item.lastChapterId = chapterId;
        item.lastPosition = lastPosition;
        newHistory.unshift(item);
        set({ readingHistory: newHistory });
      } else {
        const [responseProgress, responseBook] = await Promise.all([
          useApiBookReadingProgress.update(bookId, chapterId, lastPosition),
          useBookStore.getState().getBookById(bookId),
        ]);
        if (responseProgress.success) {
          const newItem = {
            ...responseBook,
            lastChapterId: chapterId,
            lastPosition,
            lastReadAt: new Date().toISOString(),
          } as HistoryItem;
          set({ readingHistory: [newItem, ...state.readingHistory] });
        }
      }
    },

    async getSingle(bookId: number) {
      const state = get();
      if (state.readingHistory.length <= 0) {
        await get().get();
      }
      return get().readingHistory.find((item) => item.bookId === bookId);
    },

    async delete(bookId: number) {
      const { success } = await useApiBookReadingProgress.delete(bookId);
      if (success) {
        const newHistory = get().readingHistory.filter(
          (item) => item.bookId !== bookId,
        );
        set({ readingHistory: newHistory });
        message.success('删除成功');
      } else {
        message.error('删除失败');
      }
    },
  }),
);
