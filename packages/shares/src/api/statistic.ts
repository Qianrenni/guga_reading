import { get } from '../utils';
import type { ChapterReadStatistic } from '@guga-reading/types';
export const useApiStatistic = {
  prefix: '/author',
  getBookStatistics: async function (bookId: number, chapterId: number = -1) {
    return await get<ChapterReadStatistic[]>(
      `${this.prefix}/book-statistics?book_id=${bookId}&chapter_id=${chapterId}`,
    );
  },
};
