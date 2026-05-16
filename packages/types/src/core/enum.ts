export type StatusEnum =
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'reviewing'
  | 'published';

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
