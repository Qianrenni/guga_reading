import { get } from '../utils';
import type { Book, BookChapter, Catalog } from '@guga-reading/types';

export const useApiBooks = {
  prefix: '/book',
  getBookCount: async function () {
    return await get<{ count: number }>(`${this.prefix}/count`);
  },
  getBooksByList: async function (book_ids: number[]) {
    return await get<Book[]>(
      `${this.prefix}/list?${book_ids.map((id) => `book_ids=${id}`).join('&')}`,
    );
  },
  getTotalBookCount: async function () {
    return await get<{ total: number }>(`${this.prefix}/total`);
  },
  getBookById: async function (id: number) {
    return await get<Book>(`${this.prefix}/${id}`);
  },
  getCatalogById: async function (id: number) {
    return await get<Catalog[]>(`${this.prefix}/toc/${id}`);
  },
  getBookChapterById: async function (bookId: number, chapterId: number) {
    return await get<string>(
      `${this.prefix}/chapter/${chapterId}?book_id=${bookId}`,
    );
  },
  searchBook: async function (key: string) {
    return await get<Book[]>(`${this.prefix}/search?q=${key}`);
  },
  getBookCategory: async function () {
    return await get<string[]>(`${this.prefix}/category`);
  },
  getBookBySelect: async function (
    category: string,
    offset: number,
    limit: number,
  ) {
    return await get<Book[]>(
      `${this.prefix}/select?category=${category}&offset=${offset}&limit=${limit}`,
    );
  },
  getRecommendBook: async function (tags: string) {
    return await get<Book[]>(`${this.prefix}/recommend?query=${tags}`);
  },
  getBookChapterByOrder: async function (bookId: number, order: number) {
    return await get<BookChapter>(
      `${this.prefix}/chapter-order?book_id=${bookId}&order=${order}`,
    );
  },
  getBookChapterContentByOrder: async function (
    bookId: number,
    order?: number,
    chapterId?: number,
  ) {
    return await get<string>(
      `${this.prefix}/content/chapter?book_id=${bookId}${order ? `&order=${order}` : ''}${chapterId ? `&chapter_id=${chapterId}` : ''}`,
    );
  },
};
