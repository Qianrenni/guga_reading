<template>
  <div class="container-column container-wrap">
    <QFormTable :columns="columns" :data="bookchapters" size="small">
      <template #name="{ row }">
        {{ bookMap.get(row.book_id)?.name || '--' }}
      </template>
      <template #author="{ row }">
        {{ bookMap.get(row.book_id)?.author || '--' }}
      </template>
      <template #status="{ row }">
        {{ TranslationStatus[row.status as StatusEnum] }}
      </template>
      <template #type="{ row }">
        {{ row.order > 0 ? '章节更新' : '章节修改' }}
      </template>
      <template #created_at="{ row }">
        <span>
          {{
            new UseTimeUtils(row.created_at as string).format('YYYY年M月D日H时')
          }}
        </span>
      </template>
      <template #updated_at="{ row }">
        <span>
          {{
            new UseTimeUtils(row.updated_at as string).format('YYYY年M月D日H时')
          }}
        </span>
      </template>
      <template #operation="{ row }">
        <div class="inner-container">
          <QIcon
            icon="Edit"
            size="16px"
            title="编辑"
            class="hover-color-primary"
            @click=""
          />
          <QIcon icon="Trash" size="16px" title="删除" />
        </div>
      </template>
    </QFormTable>
  </div>
</template>
<script lang="ts" setup>
import { useApiAudit } from '@guga-reading/shares';
import {
  TranslationStatus,
  type Book,
  type BookChapter,
  type StatusEnum,
} from '@guga-reading/types';
import {
  QFormTable,
  type TableColumn,
  QIcon,
  UseTimeUtils,
} from 'qyani-components';
import { onBeforeMount, ref } from 'vue';
const bookchapters = ref<BookChapter[]>([]);
const columns: TableColumn[] = [
  {
    label: '书名',
    value: 'name',
  },
  {
    label: '作者',
    value: 'author',
  },
  {
    label: '章节名',
    value: 'title',
  },
  {
    label: '字数',
    value: 'word_count',
  },
  {
    label: '创建时间',
    value: 'created_at',
  },
  {
    label: '更新时间',
    value: 'updated_at',
  },
  {
    label: '状态',
    value: 'status',
  },
  {
    label: '类型',
    value: 'type',
  },
  {
    label: '操作',
    value: 'operation',
  },
];
const bookMap = new Map<number, Book>();
onBeforeMount(() => {
  useApiAudit.getAuditBookChapter().then((res) => {
    bookchapters.value = res.data;
  });
  useApiAudit.getAuditBook().then((res) => {
    for (const book of res.data) {
      bookMap.set(book.id, book);
    }
  });
});
</script>
<style lang="css" scoped></style>
