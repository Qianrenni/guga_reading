<script setup lang="ts">
import { QIcon, QThemeToggle, useDebounce, QSearch } from 'qyani-components';
import router from '@/route';
import { useBookSearchStore } from '@/store';

defineOptions({
  name: 'Header',
});
const bookSearchStore = useBookSearchStore();
const debounceSearchBook = useDebounce(bookSearchStore.searchBook, 100);
</script>

<template>
  <header class="bg-card container header-container">
    <router-link to="/" class="link-primary hidden-768">
      <h3>咕嘎阅读</h3>
    </router-link>
    <div class="inner-container container-768-w100">
      <div>
        <QSearch
          @click="router.push('/book-search')"
          @change="(value: string) => bookSearchStore.setSearchKey(value)"
          @search="() => debounceSearchBook()"
        />
      </div>
      <div class="inner-container container-flex-1 container-flex-end">
        <router-link to="/" class="link-primary inner-container">
          <QIcon icon="House" size="16" />
          <h4 class="hidden-768">首页</h4>
        </router-link>
        <router-link to="/book-shelf" class="link-primary inner-container">
          <QIcon icon="Copy" size="16" />
          <h4 class="hidden-768">书架</h4>
        </router-link>
        <router-link to="/history" class="link-primary inner-container">
          <QIcon icon="History" size="16" />
          <h4 class="hidden-768">历史记录</h4>
        </router-link>
        <router-link to="/personal-center" class="link-primary inner-container">
          <QIcon icon="User" size="16" />
          <h4 class="hidden-768">个人中心</h4>
        </router-link>
        <QThemeToggle size="18" />
      </div>
    </div>
  </header>
</template>

<style scoped lang="css">
.header-container {
  justify-content: space-between;
  align-items: center;
  width: 100%;
  border-bottom: 1px solid var(--primary-color);
}
</style>
