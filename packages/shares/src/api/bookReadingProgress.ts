import { type BookReadingProgress } from '@guga-reading/types';
import { del, get, patch } from '../utils';

export const useApiBookReadingProgress = {
  /**
   *
   * @param bookId 书籍ID
   * @param chapterId 章节ID
   * @param last_position 最后阅读位置
   * @returns {success:boolean,data:null,message:string|null}
   */
  update: async (
    bookId: number,
    chapterId: number,
    last_position: number = 0,
  ) => {
    return await patch<null>(`/user_reading_progress/add`, {
      book_id: bookId,

      last_chapter_id: chapterId,

      last_position: last_position,
    });
  },
  get: async (): Promise<{
    success: boolean;
    data: BookReadingProgress[] | null;
    message: string | null;
  }> => {
    return await get<BookReadingProgress[]>('/user_reading_progress/get');
  },
  delete: async (bookId: number) => {
    return await del<null>(`/user_reading_progress/delete/${bookId}`);
  },
};
