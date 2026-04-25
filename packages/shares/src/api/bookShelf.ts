import { del, get, post } from '../utils';

export const useApiBookShelf = {
  add: async (bookId: number) => {
    return await post(`/shelf/add`, {
      book_id: bookId,
    });
  },
  get: async (): Promise<{
    success: boolean;
    data: { book_id: number; created_at: string }[] | null;
    message: string | null;
  }> => {
    return await get<
      {
        book_id: number;
        created_at: string;
      }[]
    >(`/shelf/get`);
  },
  delete: async (bookId: number) => {
    return await del(`/shelf/delete/${bookId}`);
  },
};
