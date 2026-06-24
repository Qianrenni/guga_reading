import { create } from 'zustand';
import type { Book } from '@guga-reading/types';
import { useApiBooks } from '@guga-reading/shares';

interface BookSearchState {
  searchKey: string;
  searchResult: Book[];
  loading: boolean;

  getSearchKey: () => string;
  getSearchResult: () => Book[];
  setSearchKey: (searchKey: string) => void;
  searchBook: () => Promise<void>;
}

export const useBookSearchStore = create<BookSearchState>((set, get) => ({
  searchKey: '',
  searchResult: [],
  loading: false,

  getSearchKey: () => get().searchKey,
  getSearchResult: () => get().searchResult,

  setSearchKey(searchKey: string) {
    set({ searchKey });
  },

  async searchBook() {
    const key = get().searchKey.trim();
    if (key.length < 1 || get().loading) {
      return;
    }
    set({ loading: true });
    const { success, data } = await useApiBooks.searchBook(key);
    if (success && data) {
      set({ searchResult: data });
    }
    set({ loading: false });
  },
}));
