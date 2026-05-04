import { get } from '../utils';
import { Book, BookChapter } from '@guga-reading/types';

export const useApiAudit = {
  prefix: '/admin',
  getAuditBookChapter: async function () {
    return await get<BookChapter[]>(`${this.prefix}/chapter`);
  },
  getAuditBook: async function () {
    return await get<Book[]>(`${this.prefix}/book`);
  },
};
