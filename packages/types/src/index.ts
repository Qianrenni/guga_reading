export type StatusEnum =
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'reviewing'
  | 'published';
export interface ResponseModel<T> {
  code: number;
  data: T;
  message: string;
}
/**
 * 书籍信息
 * @param cover  封面URL
 * @param id  书籍id
 * @param category   分类
 * @param total_chapter   总章节数
 * @param author  作者
 * @param description  书籍描述
 * @param name  书籍名称
 * @param tags   标签
 * @param created_at  创建时间
 * @param is_ended  是否完结
 * @param words_cnt  字数
 * @param updated_at  更新时间
 */
export interface Book {
  cover: string;
  id: number;
  category: string;
  total_chapter: number;
  author: string;
  description: string;
  name: string;
  tags: string;
  created_at: string;
  is_ended: boolean;
  words_cnt: number;
  updated_at: string;
  parent_id: number | null;
  status: StatusEnum;
}

/**
 * 书籍元信息
 * @param name  书籍名称
 * @param cover  封面
 * @param description  描述
 * @param category  分类
 * @param tags  标签
 */
export interface BookMeta {
  name: string;
  cover: string | null;
  description: string;
  category: string;
  tags: string;
}

/**
 * 用户信息
 * @param id  用户id
 * @param username  用户名
 * @param email  邮箱
 * @param avatar  头像URL
 * @param is_active  是否激活
 */
export interface User {
  id: number;
  username: string;
  email: string;
  avatar: string;
  is_active: boolean;
}
/**
 * 目录信息
 * @param id  目录id
 * @param title  目录标题
 * @param word_count  字数
 * @param sort_order  排序
 * @param created_at  创建时间
 * @param updated_at  更新时间
 */
export interface Catalog {
  id: number;
  title: string;
  word_count: number;
  order: number;
  created_at: string;
  updated_at: string;
}
export interface BookChapter extends Catalog {
  status: StatusEnum;
}
/**
 * 阅读设置
 * @param fontSize  字体大小
 * @param lineHeight  行高
 * @param letterSpacing  字母间距
 * @param fontFamily  字体
 * @param color  文字颜色
 * @param backgroundColor  背景颜色
 */
export interface ReadSettings {
  fontSize: string;
  lineHeight: string;
  letterSpacing: string;
  fontFamily: string;
  color: string;
  backgroundColor: string;
}
/**
 * 书籍阅读进度
 * @param book_id  书籍id
 * @param last_chapter_id  最后阅读的章节id
 * @param last_position  最后阅读位置
 * @param last_read_at  最后阅读时间
 */
export interface BookReadingProgress {
  book_id: number;
  last_chapter_id: number;
  last_position: number;
  last_read_at: string;
}

export type ShelfItem = Book & Partial<BookReadingProgress>;

/**
 * 菜单项
 * @param name  名称
 * @param path  路由
 * @param icon  图标
 */
export interface MenuItem {
  name: string;
  path: string;
  icon: string;
}
/**
 * 状态枚举
    - pending: 搁置中
    - approved: 审核通过
    - rejected: 审核拒绝
    - reviewing: 审核中
 */
export const TranslationStatus: Record<StatusEnum, string> = {
  pending: '搁置',
  approved: '审核通过',
  rejected: '审核拒绝',
  reviewing: '审核中',
  published: '已发布',
};
/**
 * 操作枚举
    - create: 创建
    - update: 修改
    - delete: 删除
 */
export type ActionEnum = 'create' | 'update' | 'delete' | 'publish';
export const TranslationAction: Record<ActionEnum, string> = {
  create: '创建',
  update: '修改',
  delete: '删除',
  publish: '发布',
};

export interface ChapterReadStatistic {
  id: number;
  book_id: number;
  chapter_id: number;
  hour_start: string;
  unique_reader_count: number;
  page_view_count: number;
  total_duration: number;
}
