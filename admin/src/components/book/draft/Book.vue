<template>
  <div class="container-column"></div>
</template>
<script lang="ts" setup>
import { useApiAudit } from '@guga-reading/shares';
import type { Book, BookChapter } from '@guga-reading/types';
import { onBeforeMount, ref } from 'vue';
const books = ref<Book[]>([]);
const bookchapters = ref<BookChapter[]>([]);
onBeforeMount(() => {
  useApiAudit.getAuditBookChapter().then((res) => {
    bookchapters.value = res.data;
  });
  useApiAudit.getAuditBook().then((res) => {
    books.value = res.data;
  });
});
</script>
<style lang="css" scoped></style>
