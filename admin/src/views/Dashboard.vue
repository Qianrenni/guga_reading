<template>
  <div class="container-column">
    <p class="text-08rem text-right">每3秒更新一次</p>
    <div class="inner-container container-w100 container-space-evenly">
      <div class="inner-container-column bg-card padding-rem radius-half-rem">
        <h4>作者数量</h4>
        <p class="text-center">{{ authorCount }}位</p>
      </div>
      <div class="inner-container-column bg-card padding-rem radius-half-rem">
        <h4>图书数量</h4>
        <p class="text-center">{{ bookCount }}本</p>
      </div>
      <div class="inner-container-column bg-card padding-rem radius-half-rem">
        <h4>用户数量</h4>
        <p class="text-center">{{ userCount }}位</p>
      </div>
      <div class="inner-container-column bg-card padding-rem radius-half-rem">
        <h4>CPU使用率</h4>
        <p class="text-center">{{ systemInfo.cpu_percent }}%</p>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { useApiAuthor, useApiBooks, useApiUser } from '@guga-reading/shares';
import type { SystemInfo } from '@guga-reading/types';
import { onBeforeMount, onBeforeUnmount, ref } from 'vue';
const systemInfo = ref<SystemInfo>({} as SystemInfo);
const authorCount = ref(0);
const bookCount = ref(0);
const userCount = ref(0);
const task = () => {
  useApiAuthor.getAuthorCount().then((res) => {
    authorCount.value = res.data;
  });
  useApiBooks.getBookCount().then((res) => {
    bookCount.value = res.data;
  });
  useApiUser.getUserCount().then((res) => {
    userCount.value = res.data;
  });
  // useApiSystem.getSystemInfo().then((res) => {
  //   systemInfo.value = res.data;
  // });
};
let timer: number | null = null;
onBeforeMount(() => {
  task();
  // timer = setInterval(task, 3000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>
