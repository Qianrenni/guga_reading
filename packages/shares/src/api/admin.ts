import { get, patch } from '../utils';
import { Book, BookChapter } from '@guga-reading/types';

export const useApiAudit = {
  prefix: '/admin',
  getAuditBookChapter: async function (chapterIds?: number[]) {
    return await get<BookChapter[]>(
      `${this.prefix}/chapter?${chapterIds ? `chapter_ids=${chapterIds.join('&chapter_ids=')}` : ''}`,
    );
  },
  getAuditBook: async function (bookIds?: number[]) {
    return await get<Book[]>(
      `${this.prefix}/book?${bookIds ? `book_id=${bookIds.join('&book_id=')}` : ''}`,
    );
  },
  getChapterContent: async function (chapterId: number) {
    return await get<string>(
      `${this.prefix}/content/chapter?chapter_id=${chapterId}`,
    );
  },
  updateChapter: async function (chapterId: number, isPass: boolean) {
    return await patch(
      `${this.prefix}/chapter?chapter_id=${chapterId}&is_pass=${isPass}`,
    );
  },
};
