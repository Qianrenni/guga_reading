<template>
  <div class="container-column container-w100">
    <div class="container-space-between">
      <BackButton>
        <span class="hidden-768">退出编辑</span>
      </BackButton>
      <div class="inner-container">
        <div>
          <QFormSelect
            placeholder="选择章节"
            :options="options"
            v-model="currentIndex"
            style="width: 15rem"
          />
        </div>
        <QIcon icon="Save" size="16px" title="保存" @click="saveChapter" />
      </div>
    </div>
    <EditableTitle v-model="title" />
    <ContentEditor
      v-model="content"
      class="container-flex-1"
      :content-height="'calc( 100vh - 15rem )'"
    />
  </div>
</template>
<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { onBeforeMount } from 'vue';
import { router } from '@/route';
import ContentEditor from '@/components/common/ContentEditor.vue';
import BackButton from '@/components/common/BackButton.vue';
import { QIcon, useMessage, UseTimeUtils } from 'qyani-components';
import { useApiAuthor } from '@guga-reading/shares';
import { TranslationStatus, type BookChapter } from '@guga-reading/types';
import EditableTitle from '@/components/common/EditableTitle.vue';
import { QFormSelect } from 'qyani-components';
const bookId = parseInt(router.currentRoute.value.query.bookId as string);
const chapterIds = Array.isArray(router.currentRoute.value.query.chapterId)
  ? router.currentRoute.value.query.chapterId.map((id) =>
      parseInt(id as string),
    )
  : [parseInt(router.currentRoute.value.query.chapterId as string)];
const bookChapters = ref<BookChapter[]>([]);
const chapterContents = ref<string[]>([]);
const currentIndex = ref<number>(-1);
const content = ref<string>('');
const title = ref<string>('');
const options = computed(() => {
  return bookChapters.value.map((chapter, index) => ({
    label: `${chapter.title}-${TranslationStatus[chapter.status]}-${new UseTimeUtils(chapter.created_at).format('M月D日H时m分')}`,
    value: index,
  }));
});
watch(
  () => currentIndex.value,
  () => {
    content.value = chapterContents.value[currentIndex.value] || '';
    title.value = bookChapters.value[currentIndex.value]?.title || '';
  },
);
const saveChapter = () => {
  const item = bookChapters.value[currentIndex.value]!;
  useApiAuthor
    .updateBookChapter(
      bookId,
      title.value || '',
      content.value || '',
      item.status == 'published' ? -Math.abs(item.order) : item.order,
    )
    .then((res) => {
      if (res.success) {
        useMessage.success('保存成功');
      } else {
        useMessage.error(`${res.message || '保存失败'}`);
      }
    });
};
onBeforeMount(() => {
  Promise.all([
    useApiAuthor.getBookChapter(bookId, chapterIds).then((res) => {
      if (res.success) {
        bookChapters.value = res.data;
      }
    }),
    useApiAuthor.getBookChapterContent(bookId, chapterIds).then((res) => {
      if (res.success) {
        chapterContents.value = res.data;
      }
    }),
  ]).then(() => {
    currentIndex.value = bookChapters.value.length - 1;
  });
});
</script>
<style scoped></style>
