import { create } from 'zustand';
import type { Book, Catalog } from '@guga-reading/types';
import { useApiBooks } from '@guga-reading/shares';

// Simple in-memory cache
const cache = new Map<string, unknown>();

interface BookState {
  books: Map<number, Book>;
  cursors: Map<string, number>;
  categoryOvers: Map<string, boolean>;
  limit: number;
  loading: boolean;
  scrollTo: number;
  categories: string[];
  currentCategory: string;
  booksSplitCategoryMap: Map<string, number[]>;

  // getters
  getBooks: () => Book[];
  getScrollTo: () => number;
  getCategoryBook: () => Book[];

  // actions
  setScrollTo: (scrollTo: number) => void;
  setCurrentCategory: (category: string) => void;
  addBookByCategory: () => Promise<void>;
  getBookById: (id: number) => Promise<Book>;
  getCatalogById: (id: number) => Promise<Catalog[]>;
  getBookByList: (bookIds: number[]) => Promise<Book[]>;
  getBookCategory: () => Promise<void>;
}

export const useBookStore = create<BookState>((set, get) => ({
  books: new Map(),
  cursors: new Map(),
  categoryOvers: new Map(),
  limit: 25,
  loading: false,
  scrollTo: 0,
  categories: [],
  currentCategory: '',
  booksSplitCategoryMap: new Map(),

  getBooks: () => Array.from(get().books.values()),
  getScrollTo: () => get().scrollTo,
  getCategoryBook: () => {
    const state = get();
    if (state.currentCategory === '') {
      return Array.from(state.books.values());
    }
    return (state.booksSplitCategoryMap.get(state.currentCategory) || [])
      .map((id) => state.books.get(id)!)
      .filter(Boolean);
  },

  setScrollTo(scrollTo: number) {
    set({ scrollTo });
  },

  setCurrentCategory(category: string) {
    set({ currentCategory: category });
  },

  async addBookByCategory() {
    const state = get();
    if (
      state.currentCategory === '' ||
      state.loading ||
      state.categoryOvers.get(state.currentCategory) ||
      false
    ) {
      return;
    }
    set({ loading: true });
    const currentCategory = state.currentCategory;
    const { success, data } = await useApiBooks.getBookBySelect(
      currentCategory,
      state.cursors.get(currentCategory) || 0,
      state.limit,
    );
    if (success && data) {
      const newCursors = new Map(state.cursors);
      newCursors.set(
        currentCategory,
        (newCursors.get(currentCategory) || 0) + data.length,
      );
      const newBooks = new Map(state.books);
      const newSplitMap = new Map(state.booksSplitCategoryMap);
      const previewBooks = newSplitMap.get(currentCategory) || [];
      for (let i = 0; i < data.length; i++) {
        newBooks.set(data[i].id, data[i]);
        cache.set(`book_${data[i].id}`, data[i]);
        previewBooks.push(data[i].id);
      }
      newSplitMap.set(currentCategory, previewBooks);
      const newOvers = new Map(state.categoryOvers);
      if (data.length === 0) {
        newOvers.set(currentCategory, true);
      }
      set({
        books: newBooks,
        cursors: newCursors,
        booksSplitCategoryMap: newSplitMap,
        categoryOvers: newOvers,
        loading: false,
      });
    } else {
      set({ loading: false });
    }
  },

  async getBookById(id: number): Promise<Book> {
    const key = `book_${id}`;
    if (cache.has(key)) {
      return cache.get(key) as Book;
    }
    const state = get();
    const isFinded = state.books.has(id);
    if (isFinded) {
      const book = state.books.get(id)!;
      cache.set(key, book);
      return book;
    }
    const { success, data } = await useApiBooks.getBookById(id);
    if (success && data) {
      const newBooks = new Map(state.books);
      newBooks.set(id, data);
      cache.set(key, data);
      set({ books: newBooks });
      return data;
    }
    return { id: 0, name: '', author: '', cover: '', description: '' } as Book;
  },

  async getCatalogById(id: number): Promise<Catalog[]> {
    const key = `catalog_${id}`;
    if (cache.has(key)) {
      return cache.get(key) as Catalog[];
    }
    const { success, data } = await useApiBooks.getCatalogById(id);
    if (success && data) {
      cache.set(key, data);
      return data;
    }
    return [];
  },

  async getBookByList(bookIds: number[]): Promise<Book[]> {
    const state = get();
    const booksFinded: Book[] = [];
    const booksNotFinded: number[] = [];
    for (const bookId of bookIds) {
      if (state.books.has(bookId)) {
        booksFinded.push(state.books.get(bookId)!);
      } else if (cache.has(`book_${bookId}`)) {
        booksFinded.push(cache.get(`book_${bookId}`) as Book);
      } else {
        booksNotFinded.push(bookId);
      }
    }
    if (booksNotFinded.length > 0) {
      const { success, data, message } =
        await useApiBooks.getBooksByList(booksNotFinded);
      if (success && data) {
        const newBooks = new Map(state.books);
        data.forEach((book) => {
          newBooks.set(book.id, book);
          cache.set(`book_${book.id}`, book);
        });
        set({ books: newBooks });
        booksFinded.push(...data);
      } else {
        console.error(message);
      }
    }
    return booksFinded;
  },

  async getBookCategory() {
    const { success, data } = await useApiBooks.getBookCategory();
    if (success && data) {
      set({ categories: data.sort((a, b) => a.length - b.length) });
    }
  },
}));
