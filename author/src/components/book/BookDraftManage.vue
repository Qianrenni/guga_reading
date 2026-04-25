<template>
  <div class="container-column bg-card shadow-common">
    <div v-show="loading" style="height: calc(100vh - 8rem)">
      <QLoading type="skeleton" />
    </div>
    <QFormTable
      v-show="!loading"
      :columns="columns"
      :data="bookDrafts"
      size="small"
      class="text-one-line"
      style="height: calc(100vh - 9rem)"
    >
      <template #name="{ row }">
        <span>{{ row.name }}</span>
      </template>
      <template #status="{ row }">
        {{ TranslationStatus[row.status as StatusEnum] }}
      </template>
      <template #action="{ row }">
        {{ TranslationAction[row.action as ActionEnum] }}
      </template>
      <template #cover="{ row }">
        <QLazyImage :src="row.cover" :height="128" :width="72" />
      </template>
      <template #description="{ row }">
        <p class="scroll-container book-description">
          {{ row.description }}
        </p>
      </template>
      <template #tags="{ row }">
        <div class="inner-container container-wrap gap-half">
          <span
            v-for="tag in row.tags.split(' ')"
            :key="tag"
            class="tag"
            style="text-wrap-mode: nowrap"
          >
            {{ tag }}
          </span>
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
                  name: 'BookInfoEdit',
                  query: {
                    id: row.id,
                    hasDraft: 1,
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
                useApiAuthor.submitBookDraft(row.id).then((res) => {
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
                useApiAuthor.deleteBookDraft(row.id, row.action).then((res) => {
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
  QLazyImage,
  UseTimeUtils,
  QLoading,
} from 'qyani-components';
import { useApiAuthor } from '@guga-reading/shares';
import { onBeforeMount, ref } from 'vue';
import {
  TranslationAction,
  TranslationStatus,
  type ActionEnum,
  type BookDraft,
  type StatusEnum,
} from '@guga-reading/types';
import { router } from '@/route';
const loading = ref(false);
const bookDrafts = ref<BookDraft[]>([]);
const columns = [
  {
    value: 'name',
    label: '书名',
  },
  {
    value: 'cover',
    label: '封面',
  },
  {
    value: 'description',
    label: '简介',
  },
  {
    value: 'category',
    label: '分类',
  },
  {
    value: 'tags',
    label: '标签',
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
  useApiAuthor
    .getBookDraft()
    .then((res) => {
      letIfNotNull<BookDraft[], void>(res.data, (data) => {
        bookDrafts.value = data;
      });
    })
    .finally(() => {
      loading.value = false;
    });
};
onBeforeMount(() => {
  refresh();
});
</script>
<style lang="css" scoped>
.book-description {
  max-height: 128px;
  min-width: 10rem;
  max-width: 20rem;
  white-space: wrap;
  -webkit-line-clamp: unset;
  line-clamp: unset;
}
</style>
