<template>
  <div class="container-column bg-card shadow-common">
    <div v-show="loading" style="height: calc(100vh - 8rem)">
      <QLoading type="skeleton" />
    </div>
    <QFormTable
      v-show="!loading"
      :columns="columns"
      :data="bookChapterDrafts"
      size="small"
      class="text-one-line"
      style="height: calc(100vh - 9rem)"
    >
      <template #book_id="{ row }">
        {{ books.get(row.book_id)?.name }}
      </template>
      <template #status="{ row }">
        {{ TranslationStatus[row.status as StatusEnum] }}
      </template>
      <template #action="{ row }">
        {{ TranslationAction[row.action as ActionEnum] }}
      </template>
      <template #response="{ row }">
        <div class="container">
          <QIcon
            v-if="['update', 'create'].includes(row.action as ActionEnum)"
            icon="Edit"
            size="16px"
            title="编辑"
            class="hover-color-primary"
            @click="
              () => {
                router.push({
                  name: 'BookEdit',
                  query: {
                    bookId: row.book_id,
                    sortOrder: row.sort_order,
                    hasDraft: 1,
                    chapterId: -1,
                  },
                });
              }
            "
          />
          <QIcon
            icon="Upload"
            size="16px"
            title="提交审核"
            class="hover-color-primary"
            @click="
              () => {
                useApiAuthor
                  .submitBookChapter(row.book_id, row.sort_order)
                  .then((res) => {
                    if (res.success) {
                      useMessage.success('提交成功');
                      refresh();
                    }
                  });
              }
            "
          />
          <QIcon
            icon="Trash"
            size="16px"
            title="撤销改动"
            @click="
              () => {
                useApiAuthor
                  .deleteBookChapter(row.book_id, [row.sort_order], true)
                  .then((res) => {
                    if (res.success) {
                      useMessage.success('撤销成功');
                      refresh();
                    }
                  });
              }
            "
          />
        </div>
      </template>
      <template #created_at="{ row }">
        <span>{{
          new UseTimeUtils(row.created_at as string).format('YYYY年M月D日H时')
        }}</span>
      </template>
      <template #updated_at="{ row }">
        <span>{{
          new UseTimeUtils(row.updated_at as string).format('YYYY年M月D日H时')
        }}</span>
      </template>
    </QFormTable>
  </div>
</template>
<script lang="ts" setup>
defineOptions({ name: 'BookChapterDraftManage' });
import {
  letIfNotNull,
  QFormTable,
  type TableColumn,
  QIcon,
  useMessage,
  UseTimeUtils,
  QLoading,
} from 'qyani-components';
import {
  TranslationAction,
  TranslationStatus,
  type ActionEnum,
  type Book,
  type BookChapterDraft,
  type StatusEnum,
} from '@guga-reading/types';
import { useApiAuthor } from '@guga-reading/shares';
import { onBeforeMount, ref } from 'vue';
import { router } from '@/route';
const loading = ref(false);
const bookChapterDrafts = ref<BookChapterDraft[]>([]);
const books: Map<number, Book> = new Map();
const columns = [
  {
    value: 'book_id',
    label: '书名',
  },
  {
    value: 'title',
    label: '标题',
  },
  {
    value: 'word_count',
    label: '字数',
  },
  {
    value: 'created_at',
    label: '创建时间',
  },
  {
    value: 'updated_at',
    label: '更新时间',
  },
  {
    value: 'status',
    label: '状态',
  },
  {
    value: 'action',
    label: '类型',
  },
  {
    value: 'response',
    label: '操作',
  },
] satisfies TableColumn[];

const refresh = () => {
  loading.value = true;
  Promise.all([
    useApiAuthor.getBook(),
    useApiAuthor.getBookChapterDraft(-1, -1),
  ])
    .then(([res1, res2]) => {
      letIfNotNull<Book[], void>(res1.data, (data) => {
        data.forEach((book) => books.set(book.id, book));
      });
      letIfNotNull<BookChapterDraft[], void>(
        res2.data as BookChapterDraft[],
        (data) => {
          bookChapterDrafts.value = data;
        },
      );
    })
    .finally(() => {
      loading.value = false;
    });
};
onBeforeMount(() => {
  refresh();
});
</script>
