import { del, get, patch, post } from '../utils';
import type {
  Book,
  BookDraft,
  Catalog,
  CatalogDraft,
} from '@guga-reading/types';
export const useApiAuthor = {
  prefix: '/author',
  getBook: async function (id: number = -1) {
    return await get<Book[]>(
      `${this.prefix}/book?${id != -1 ? `id=${id}` : ''}`,
    );
  },
  /**
   * 获取书籍目录, 如果chapterId为-1则返回所有章节
   * @param bookId   书籍id
   * @param chapterId   章节id
   * @returns
   */
  getBookCatalog: async function (bookId: number, chapterId: number = -1) {
    return await get<Catalog[]>(
      `${this.prefix}/book-catalog?book_id=${bookId}&chapter_id=${chapterId}`,
    );
  },
  /**
   * 删除书籍章节
   * @param bookId  书籍id
   * @returns
   */
  deleteBookChapter: async function (
    bookId: number,
    sort_orders: number[],
    is_draft: boolean,
  ) {
    return await del<null>(
      `${this.prefix}/book-chapter/${bookId}?is_draft=${is_draft}`,
      {
        data: {
          sort_orders,
        },
      },
    );
  },
  /**
   * 获取书籍章节草稿,如果chapterId为-1则返回所有章节
   * @param bookId  书籍id
   * @param chapterId   章节id
   * @returns
   */
  getBookChapterDraft: async function (bookId: number, chapterId: number = -1) {
    return await get<CatalogDraft[]>(
      `${this.prefix}/book-chapter-draft?book_id=${bookId}&chapter_id=${chapterId}`,
    );
  },
  /**
   * 获取书籍章节
   * @param bookId  书籍id
   * @param sortOrder  章节排序
   * @param isDraft  是否草稿
   * @returns
   */
  getBookChapter: async function (
    bookId: number,
    sortOrder: number,
    isDraft: boolean,
  ) {
    return await get<string>(
      `${this.prefix}/book-chapter?book_id=${bookId}&sort_order=${sortOrder}&is_draft=${isDraft}`,
    );
  },
  /**
   * 获取书籍章节草稿
   * @param bookId  书籍id
   * @param sortOrder  章节排序
   * @returns
   */
  getBookChapterDraftItem: async function (bookId: number, sortOrder: number) {
    return await get<CatalogDraft>(
      `${this.prefix}/book-chapter-draft-item?book_id=${bookId}&sort_order=${sortOrder}`,
    );
  },
  /**
   * 保存书籍章节
   * @param bookId  书籍id
   * @param sortOrder  章节排序
   * @param isDraft  是否草稿
   * @param content  章节内容
   * @param title  章节标题
   * @returns
   */
  saveBookChapter: async function (
    bookId: number,
    sortOrder: number,
    isDraft: boolean,
    content: string,
    title: string,
  ) {
    return await patch<null>(`${this.prefix}/chapter`, {
      book_id: bookId,
      sort_order: sortOrder,
      is_draft: isDraft,
      content,
      title,
    });
  },
  /**
   * 创建书籍章节
   * @param bookId  书籍id
   * @param title  章节标题
   * @param content  章节内容
   * @param sortOrder  章节排序
   * @returns
   */
  createBookChapter: async function (
    bookId: number,
    title: string,
    content: string,
    sortOrder: number,
  ) {
    return await post<null>(`${this.prefix}/chapter`, {
      book_id: bookId,
      title,
      content,
      sort_order: sortOrder,
    });
  },
  /**
   * 创建书籍
   * @param name  书籍名称
   * @param author  作者
   * @param cover  封面
   * @param description  描述
   * @param category  类别
   * @param tags  标签
   * @returns
   */
  createBook: async function (
    name: string,
    author: string,
    cover: File,
    description: string,
    category: string,
    tags: string,
  ) {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('author', author);
    formData.append('cover', cover);
    formData.append('description', description);
    formData.append('category', category);
    formData.append('tags', tags);
    return await post<null>(`${this.prefix}/book`, formData);
  },
  /**
   * 提交书籍章节
   * @param bookId  书籍id
   * @param sortOrder  章节排序
   * @returns
   */
  submitBookChapter: async function (bookId: number, sortOrder: number) {
    return await patch<null>(
      `${this.prefix}/status/book-chapter?book_id=${bookId}&sort_order=${sortOrder}`,
    );
  },
  /**
   * 获取书籍草稿
   * @param id  书籍草稿id
   * @returns
   */
  getBookDraft: async function (id: number = -1) {
    return await get<BookDraft[]>(`${this.prefix}/book-draft?id=${id}`);
  },
  /**
   * 提交书籍草稿
   * @returns
   */
  submitBookDraft: async function (bookDraftId: number) {
    return await patch<null>(
      `${this.prefix}/status/book-draft?id=${bookDraftId}`,
    );
  },
  /**
   * 删除书籍草稿
   * @param bookDraftId  书籍草稿id
   * @param action  删除操作
   * @returns
   */
  deleteBookDraft: async function (bookDraftId: number, action: string) {
    return await del<null>(
      `${this.prefix}/book-draft?id=${bookDraftId}&action=${action}`,
    );
  },
  updateBook: async function (
    id: number,
    name: string,
    author: string,
    cover: File,
    description: string,
    category: string,
    tags: string,
    isDraft: boolean,
    action: string,
  ) {
    const formData = new FormData();
    formData.append('id', id.toString());
    formData.append('name', name);
    formData.append('author', author);
    if (cover) {
      formData.append('cover', cover);
    }
    formData.append('description', description);
    formData.append('category', category);
    formData.append('tags', tags);
    formData.append('is_draft', isDraft.toString());
    formData.append('action', action);
    return await patch<null>(`${this.prefix}/book`, formData);
  },
};
