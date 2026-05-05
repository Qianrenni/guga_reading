<template>
  <div class="container-column container-w100 container-h100">
    <div class="show-768">
      <QFormSelect v-model="select" :options="options" />
    </div>
    <div class="inner-container">
      <div
        class="inner-container-column container-flex-1"
        v-if="isUpdate && !isMobile"
      >
        <p v-if="isUpdate && !isMobile">修改前</p>
        <EditableTitle v-model="srcChapter.title" :disabled="true" />
        <ContentEditor v-model="srcContent" :disabled="true" />
      </div>
      <div class="inner-container-column container-flex-1">
        <p v-if="isUpdate && !isMobile">修改后</p>
        <EditableTitle v-model="chapter.title" :disabled="true" />
        <ContentEditor v-model="content" :disabled="true" />
      </div>
    </div>
    <div class="inner-container container-flex-end">
      <QFormButton><span>通过</span></QFormButton>
      <QFormButton><span>驳回</span></QFormButton>
    </div>
  </div>
</template>
<script lang="ts" setup>
import router from '@/route';
import { useApiAudit, useApiBooks } from '@guga-reading/shares';
import type { BookChapter } from '@guga-reading/types';
import { onBeforeMount, ref } from 'vue';
import ContentEditor from '@/components/common/ContentEditor.vue';
import EditableTitle from '@/components/common/EditableTitle.vue';
import { QFormButton, useScreenSize, QFormSelect } from 'qyani-components';
const chapter = ref<BookChapter>({} as BookChapter);
const chapterId = parseInt(
  router.currentRoute.value.query.chapter_id as string,
);
const isMobile = useScreenSize.getWidth(768);
const select = ref<string>('after');
const options = [
  {
    label: '修改前',
    value: 'before',
  },
  {
    label: '修改后',
    value: 'after',
  },
];
const content = ref<string>('');
const isUpdate = ref<boolean>(false);
const srcChapter = ref<BookChapter>({} as BookChapter);
const srcContent = ref<string>('');
onBeforeMount(() => {
  useApiAudit
    .getAuditBookChapter([chapterId])
    .then((res) => {
      chapter.value = res.data[0] as BookChapter;
      return chapter.value;
    })
    .then((res) => {
      console.log(res);
      if (res.order < 0) {
        isUpdate.value = true;
        useApiBooks
          .getBookChapterByOrder(res.book_id, -res.order)
          .then((res) => {
            srcChapter.value = res.data;
          });
        useApiBooks
          .getBookChapterContentByOrder(res.book_id, -res.order)
          .then((res) => {
            srcContent.value = res.data;
          });
      }
    });
  useApiAudit.getChapterContent(chapterId).then((res) => {
    content.value = res.data;
  });
});
</script>
