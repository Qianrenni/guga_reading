import { get, patch } from '../utils';
import { Book, BookChapter } from '@guga-reading/types';

export const useApiAudit = {
  prefix: '/admin',
  getAuditBookChapter: async function (chapterIds?: number[]) {
    return await get<BookChapter[]>(
      `${this.prefix}/chapter?${chapterIds ? `chapterIds=${chapterIds.join('&chapterIds=')}` : ''}`,
    );
  },
  getAuditBook: async function (bookIds?: number[]) {
    return await get<Book[]>(
      `${this.prefix}/book?${bookIds ? `bookIds=${bookIds.join('&bookIds=')}` : ''}`,
    );
  },
  patchAuditBook: async function (bookId: number, isPass: boolean) {
    return await patch(
      `${this.prefix}/book?bookId=${bookId}&is_pass=${isPass}`,
    );
  },
  getChapterContent: async function (chapterId: number) {
    return await get<string>(
      `${this.prefix}/content/chapter?chapterId=${chapterId}`,
    );
  },
  updateChapter: async function (chapterId: number, isPass: boolean) {
    return await patch(
      `${this.prefix}/chapter?chapterId=${chapterId}&is_pass=${isPass}`,
    );
  },
};
