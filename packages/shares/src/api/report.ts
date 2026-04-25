import { post } from '../utils';

export type EventType = 'enter' | 'heartbeat' | 'exit';
export const useApiReport = {
  prefix: '/statistic',
  /**
   * 上报阅读情况
   * @param bookId  书籍ID
   * @param chapterId  章节ID
   * @param event_type  事件类型
   * @returns
   */
  reportChapterRead: async function (
    bookId: number,
    chapterId: number,
    event_type: EventType,
  ) {
    return await post<null>(`${this.prefix}/book-chapter`, {
      book_id: bookId,
      chapter_id: chapterId,
      event_type: event_type,
    });
  },
};
