<template>
  <div class="container-column shadow-common">
    <div class="container-space-between">
      <QFormButton
        type="button"
        class="button-delete padding-24rem radius-half-rem"
        @click="handelBatchDelete()"
      >
        <span>批量删除</span>
      </QFormButton>
      <QFormButton
        type="button"
        class="button-outline padding-24rem radius-half-rem"
        @click="handleCreateChapter()"
      >
        <span>新增章节</span>
      </QFormButton>
    </div>
    <div v-show="loading" style="height: calc(100vh - 12.5rem)">
      <QLoading type="skeleton" class="container-flex-1" />
    </div>
    <QFormTable
      v-show="!loading"
      v-model:model-value="toBeDeleted"
      style="height: calc(100vh - 12.5rem)"
      :columns="columns"
      :data="catalogList"
      selectable
      size="small"
      class="text-one-line"
    >
      <template #index="{ row }">
        <span>{{ row.index + 1 }}</span>
      </template>
      <template #word_count="{ row }">
        <span>{{ row.word_count }} 字</span>
      </template>
      <template #created_at="{ value }">
        <span>{{
          new UseTimeUtils(value as string).format('YYYY年M月D日H时')
        }}</span>
      </template>
      <template #updated_at="{ value }">
        <span>{{
          new UseTimeUtils(value as string).format('YYYY年M月D日H时')
        }}</span>
      </template>
      <template #action="{ row }">
        <div class="container">
          <QIcon
            icon="Edit"
            size="16px"
            title="编辑"
            class="hover-color-primary"
            @click="
              router.push({
                name: 'BookEdit',
                query: {
                  bookId: props.bookId,
                  sortOrder: row.sort_order,
                  hasDraft: ['update', 'create'].includes(
                    row.action as ActionEnum,
                  )
                    ? 1
                    : 0,
                  chapterId: row.id,
                },
              })
            "
          />
          <QIcon
            icon="Trash"
            size="16px"
            title="删除"
            @click="handleDeleteBookChapter([row.sort_order], row.isDraft)"
          />
        </div>
      </template>
      <template #status="{ row }">
        <span
          :class="[
            {
              'text-success': !row.isDraft,
            },
          ]"
          >{{ row.isDraft ? '草稿' : '已发布' }}</span
        >
      </template>
      <template #dynamic="{ row }">
        <span v-if="row.action != 'publish'"
          >{{ TranslationStatus[row.status as StatusEnum] }}--{{
            TranslationAction[row.action as ActionEnum]
          }}
        </span>
        <span v-else>&nbsp;</span>
      </template>
    </QFormTable>
  </div>
</template>
<script setup lang="ts">
import { onBeforeMount, ref } from 'vue';
import {
  TranslationAction,
  TranslationStatus,
  type ActionEnum,
  type Catalog,
  type CatalogDraft,
  type StatusEnum,
} from '@guga-reading/types';
import { useApiAuthor } from '@guga-reading/shares';
import type { TableColumn } from 'qyani-components';
import {
  QFormTable,
  QIcon,
  QFormButton,
  useMessage,
  UseTimeUtils,
  QLoading,
} from 'qyani-components';
import { router } from '@/route';
defineOptions({
  name: 'BookChapterManage',
});
interface BookchapterItem extends Catalog {
  isDraft: boolean;
  status: StatusEnum;
  action: ActionEnum;
  index: number;
}
const props = defineProps<{
  bookId: number;
}>();
const toBeDeleted = ref<BookchapterItem[]>([]);
const catalogList = ref<BookchapterItem[]>([]);
const loading = ref(false);
const columns = [
  {
    label: '序号',
    value: 'index',
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
    label: '发布时间',
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
    label: '动态',
    value: 'dynamic',
  },
  {
    label: '操作',
    value: 'action',
  },
] satisfies TableColumn[];
const handleDeleteBookChapter = (sort_orders: number[], is_draft: boolean) => {
  useApiAuthor
    .deleteBookChapter(props.bookId, sort_orders, is_draft)
    .then((res) => {
      if (res.success) {
        useMessage.success('已经添加至删除列表');
        run();
      } else {
        useMessage.error(`${res.message}`);
      }
    });
};
const handelBatchDelete = () => {
  let deleteOnDraft = [] as number[];
  let deleteOnFormal = [] as number[];
  for (let i = 0, len = toBeDeleted.value.length; i < len; i++) {
    if (toBeDeleted.value[i]!.isDraft) {
      deleteOnDraft.push(toBeDeleted.value[i]!.sort_order);
    } else {
      deleteOnFormal.push(toBeDeleted.value[i]!.sort_order);
    }
  }
  Promise.all([
    useApiAuthor.deleteBookChapter(props.bookId, deleteOnDraft, true),
    useApiAuthor.deleteBookChapter(props.bookId, deleteOnFormal, false),
  ]).then((res) => {
    if (res.every((item) => item.success)) {
      useMessage.success('已经添加至删除列表');
      run();
    } else {
      useMessage.error('删除失败');
    }
  });
};
const handleCreateChapter = () => {
  const sortOrder =
    (catalogList.value[catalogList.value.length - 1]?.sort_order ?? 0) + 2.0;
  useApiAuthor
    .createBookChapter(props.bookId, '未命名', '', sortOrder)
    .then((res) => {
      if (res.success) {
        useMessage.success('创建成功');
        run();
      } else {
        useMessage.error(`${res.message}`);
      }
    });
};
const run = async () => {
  loading.value = true;
  Promise.all([
    useApiAuthor.getBookCatalog(props.bookId),
    useApiAuthor.getBookChapterDraft(props.bookId),
  ])
    .then((res) => {
      const [online, draft] = res;
      let deleteDraft = [] as CatalogDraft[],
        addDraft = [] as CatalogDraft[],
        updateDraft = [] as CatalogDraft[];
      for (let i = 0, len = draft.data.length; i < len; i++) {
        switch (draft.data[i]!.action) {
          case 'create':
            addDraft.push(draft.data[i]!);
            break;
          case 'update':
            updateDraft.push(draft.data[i]!);
            break;
          case 'delete':
            deleteDraft.push(draft.data[i]!);
            break;
          default:
            console.error('未知操作类型', draft.data[i]);
            break;
        }
      }
      const sortOrderToIndex = new Map<number, number>();
      for (let i = 0; i < online.data.length; i++) {
        sortOrderToIndex.set(online.data[i]!.sort_order, i);
      }
      let result: BookchapterItem[] = online.data.map((item, index) => ({
        ...item,
        action: 'publish',
        isDraft: false,
        status: 'approved',
        index,
      }));
      for (let i = 0, len = deleteDraft.length; i < len; i++) {
        let index = sortOrderToIndex.get(deleteDraft[i]!.sort_order);
        if (index !== undefined) {
          result[index]!.action = deleteDraft[i]!.action;
          result[index]!.status = deleteDraft[i]!.status;
        }
      }
      for (let i = 0, len = updateDraft.length; i < len; i++) {
        let index = sortOrderToIndex.get(updateDraft[i]!.sort_order);
        if (index !== undefined) {
          result[index]!.action = updateDraft[i]!.action;
          result[index]!.status = updateDraft[i]!.status;
        }
      }
      let temp: BookchapterItem[] = Array(addDraft.length);
      for (let i = 0, len = addDraft.length; i < len; i++) {
        temp[i] = {
          ...addDraft[i]!,
          action: addDraft[i]!.action,
          isDraft: true,
          index: i,
          status: addDraft[i]!.status,
        };
      }
      result.push(...temp);
      catalogList.value = result
        .sort((a, b) => a.sort_order - b.sort_order)
        .map((item, index) => ({
          ...item,
          index,
        }));
    })
    .finally(() => {
      loading.value = false;
    });
};

onBeforeMount(() => {
  run();
});
</script>
