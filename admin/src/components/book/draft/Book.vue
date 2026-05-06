<template>
  <div class="container-column container-wrap">
    <QFormTable :columns="columns" :data="books" size="small">
      <template #status="{ row }">
        {{ TranslationStatus[row.status as StatusEnum] }}
      </template>
      <template #type="{ row }">
        {{ row.parent_id != null ? '更新' : '创建' }}
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
            icon="EyeOpen"
            size="16px"
            title="查看"
            class="hover-color-primary"
            @click="
              () => {
                router.push({
                  path: `/book-audit`,
                  query: {
                    book_id: row.id,
                  },
                });
              }
            "
          />
        </div>
      </template>
    </QFormTable>
  </div>
</template>
<script lang="ts" setup>
import router from '@/route';
import { useApiAudit } from '@guga-reading/shares';
import {
  TranslationStatus,
  type Book,
  type StatusEnum,
} from '@guga-reading/types';
import {
  QFormTable,
  type TableColumn,
  QIcon,
  UseTimeUtils,
} from 'qyani-components';
import { onBeforeMount, ref } from 'vue';
const books = ref<Book[]>([]);
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
onBeforeMount(() => {
  useApiAudit.getAuditBook().then((res) => {
    books.value = res.data.filter((item) => item.status !== 'published');
  });
});
</script>
<style lang="css" scoped></style>
