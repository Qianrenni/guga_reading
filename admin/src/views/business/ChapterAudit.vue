<template>
  <div class="container-column container-w100">
    <div class="inner-container-column">
      <EditableTitle v-model="chapter.title" :disabled="true" />
      <ContentEditor
        v-model="content"
        :disabled="true"
        :content-height="'calc( 100vh - 15rem )'"
      />
    </div>
    <div class="inner-container container-flex-end">
      <QFormButton><span>通过</span></QFormButton>
      <QFormButton><span>驳回</span></QFormButton>
    </div>
  </div>
</template>
<script lang="ts" setup>
import router from '@/route';
import { useApiAudit } from '@guga-reading/shares';
import type { BookChapter } from '@guga-reading/types';
import { onBeforeMount, ref } from 'vue';
import ContentEditor from '@/components/common/ContentEditor.vue';
import EditableTitle from '@/components/common/EditableTitle.vue';
import { QFormButton } from 'qyani-components';
const chapter = ref<BookChapter>({} as BookChapter);
const chapterId = parseInt(
  router.currentRoute.value.query.chapter_id as string,
);
const content = ref<string>('');
onBeforeMount(() => {
  useApiAudit.getAuditBookChapter([chapterId]).then((res) => {
    chapter.value = res.data[0] as BookChapter;
    return chapter.value;
  });
  useApiAudit.getChapterContent(chapterId).then((res) => {
    content.value = res.data;
  });
});
</script>
