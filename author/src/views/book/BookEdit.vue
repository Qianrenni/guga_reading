<template>
  <div class="container-column container-w100">
    <div class="container-space-between">
      <BackButton>
        <span>退出编辑</span>
      </BackButton>
      <div
        class="container-align-center mouse-cursor hover-color-primary"
        @click="saveChapter()"
      >
        <QIcon icon="Save" size="16px" title="保存" />
        <span>保存</span>
      </div>
    </div>
    <EditableTitle v-model="chapterItem.title" />
    <ContentEditor
      v-model="content"
      class="container-flex-1"
      :content-height="'calc( 100vh - 15rem )'"
    />
  </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { onBeforeMount } from 'vue';
import { router } from '@/route';
import ContentEditor from '@/components/common/ContentEditor.vue';
import BackButton from '@/components/common/BackButton.vue';
import {
  QIcon,
  letIfNotNull,
  useMessage,
  useShowLoading,
} from 'qyani-components';
import { useApiAuthor } from '@guga-reading/shares';
import { type Catalog, type CatalogDraft } from '@guga-reading/types';
import EditableTitle from '@/components/common/EditableTitle.vue';
const content = ref('');
const chapterItem = ref<Catalog>({} as Catalog);
const bookId = parseInt(router.currentRoute.value.query.bookId as string);
const sortOrder = parseFloat(
  router.currentRoute.value.query.sortOrder as string,
);
const hasDraft = Boolean(
  parseInt(router.currentRoute.value.query.hasDraft as string),
);
const chapterId = parseInt(router.currentRoute.value.query.chapterId as string);

const saveChapter = () => {
  useShowLoading.show();
  useApiAuthor
    .saveBookChapter(
      bookId,
      sortOrder,
      hasDraft,
      content.value,
      chapterItem.value.title,
    )
    .then((res) => {
      if (res.success) {
        useMessage.success('保存成功');
      }
    })
    .finally(() => {
      useShowLoading.hide();
    });
};
onBeforeMount(() => {
  useApiAuthor.getBookChapter(bookId, sortOrder, hasDraft).then((res) => {
    letIfNotNull<string, void>(res.data, (data) => (content.value = data));
  });
  if (hasDraft) {
    useApiAuthor.getBookChapterDraftItem(bookId, sortOrder).then((res) => {
      letIfNotNull<CatalogDraft, void>(res.data, (data) => {
        chapterItem.value = data;
      });
    });
  } else {
    useApiAuthor.getBookCatalog(bookId, chapterId).then((res) => {
      letIfNotNull<Catalog[], void>(res.data, (data) => {
        chapterItem.value = data[0]!;
      });
    });
  }
});
</script>
<style scoped></style>
