import { defineStore } from 'pinia';
import type { Book, BookReadingProgress } from '@guga-reading/types';
import { useApiBookReadingProgress } from '@guga-reading/shares';
import { useBookStore } from './useBookStore';
import { useMessage } from 'qyani-components';

type HistoryItem = BookReadingProgress & Book;

export const useReadingHistoryStore = defineStore('readingHistory', {
  state: () => ({
    readingHistory: [] as HistoryItem[],
    loading: false,
  }),
  getters: {
    getReadingHistory: (state) => state.readingHistory,
  },
  actions: {
    async get() {
      if (this.readingHistory.length > 0 || this.loading) {
        return;
      }
      this.loading = true;
      const { success, data } = await useApiBookReadingProgress.get();
      if (success) {
        const bookStore = useBookStore();
        const books = await bookStore.getBookByList(
          data!.map((item) => item.book_id),
        );
        const historyItems = data!.map((item) => ({
          ...item,
          ...books.find((book) => book.id === item.book_id),
        }));
        this.readingHistory = historyItems as HistoryItem[];
      }
      this.loading = false;
    },
    async update(bookId: number, chapterId: number, last_position: number = 0) {
      const index = this.readingHistory.findIndex((item) => item.id === bookId);
      if (index !== -1) {
        const [item] = this.readingHistory.splice(index, 1);
        item.last_chapter_id = chapterId;
        item.last_position = last_position;
        this.readingHistory.unshift(item);
      } else {
        const [responseProgress, responseBook] = await Promise.all([
          useApiBookReadingProgress.update(bookId, chapterId, last_position),
          useBookStore().getBookById(bookId),
        ]);
        if (responseProgress.success) {
          this.readingHistory.unshift({
            ...responseBook,
            last_chapter_id: chapterId,
            last_position: last_position,
            last_read_at: new Date().toISOString(),
          } as HistoryItem);
        }
      }
    },
    async getSingle(bookId: number) {
      if (this.readingHistory.length <= 0) {
        await this.get();
      }
      return this.readingHistory.find((item) => item.book_id === bookId);
    },
    async delete(bookId: number) {
      const { success } = await useApiBookReadingProgress.delete(bookId);
      if (success) {
        const index = this.readingHistory.findIndex(
          (item) => item.book_id === bookId,
        );
        if (index !== -1) {
          this.readingHistory.splice(index, 1);
        }
        useMessage.success('删除成功');
      } else {
        useMessage.error('删除失败');
      }
    },
  },
});
