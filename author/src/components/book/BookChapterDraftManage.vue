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
      <template #response="{ row }">
        <div class="container">
          <QIcon
            v-if="['pending', 'rejected'].includes(row.status)"
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
                    chapterId: row.id,
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
                  .updateStatusBookChapter(row.book_id, row.id, row.status)
                  .then((res) => {
                    if (res.success) {
                      useMessage.success('提交成功');
                      refresh();
                    } else {
                      useMessage.error(res.message || '提交失败');
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
                  .deleteBookChapter(row.book_id, row.id)
                  .then((res) => {
                    if (res.success) {
                      useMessage.success('撤销成功');
                      refresh();
                    } else {
                      useMessage.error(res.message || '撤销失败');
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
      <template #action="{ row }">
        <span>{{ row.order > 0 ? '创建' : '更新' }}</span>
      </template>
    </QFormTable>
  </div>
</template>
<script lang="ts" setup>
defineOptions({ name: 'BookChapterDraftManage' });
import {
  QFormTable,
  type TableColumn,
  QIcon,
  UseTimeUtils,
  QLoading,
  useMessage,
} from 'qyani-components';
import {
  TranslationStatus,
  type Book,
  type BookChapter,
  type StatusEnum,
} from '@guga-reading/types';
import { useApiAuthor } from '@guga-reading/shares';
import { onBeforeMount, ref } from 'vue';
import { router } from '@/route';
const loading = ref(false);
const bookChapterDrafts = ref<BookChapter[]>([]);
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
    useApiAuthor.getBook().then((res) => {
      for (const book of res.data) {
        books.set(book.id, book);
      }
    }),
    useApiAuthor.getAuthorDraftChapter().then((res) => {
      bookChapterDrafts.value = res.data;
    }),
  ]).finally(() => {
    loading.value = false;
  });
};
onBeforeMount(() => {
  refresh();
});
</script>
