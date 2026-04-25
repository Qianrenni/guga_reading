<template>
  <div
    ref="bookReadContainer"
    class="container-w100 scroll-container"
    style="height: calc(100vh - 3rem)"
  >
    <div class="book-read-container bg-card">
      <QLoading
        v-show="loading"
        style="height: calc(100vh - 4rem); width: 100%"
        type="skeleton"
      />
      <div
        v-show="!loading"
        class="book-read-content"
        :style="{
          fontSize: readSettings.fontSize,
          fontFamily: readSettings.fontFamily,
          lineHeight: readSettings.lineHeight,
          letterSpacing: readSettings.letterSpacing,
          color: readSettings.color,
          backgroundColor: readSettings.backgroundColor,
        }"
        @click="shwoBottomSettings = true"
        v-html="content"
      />
      <div class="book-read-sidebar bg-card hidden-768 container-column">
        <div
          class="inner-container-column container-align-center bg-hover-secondary"
          @click="
            currentContentIndex > 0
              ? run(computeCatalog[currentContentIndex - 1].id)
              : void 0
          "
        >
          <QIcon icon="Up" size="24px" />
          <span class="text-08rem">上一章</span>
        </div>
        <div
          class="inner-container-column container-align-center bg-hover-secondary"
          @click="
            currentContentIndex < computeCatalog.length - 1
              ? run(computeCatalog[currentContentIndex + 1].id)
              : void 0
          "
        >
          <QIcon icon="Down" size="24px" />
          <span class="text-08rem">下一章</span>
        </div>
        <div
          class="inner-container-column container-align-center bg-hover-secondary"
          @click="showCatalog = true"
        >
          <QIcon icon="Catalog" size="24px" />
          <span class="text-08rem">目录</span>
        </div>
        <div
          class="inner-container-column container-align-center bg-hover-secondary"
        >
          <QIcon icon="Setting" size="24px" />
          <span class="text-08rem">阅读设置</span>
        </div>
        <div
          class="inner-container-column container-align-center bg-hover-secondary"
          @click="router.push(`/book-detail/${book.id}`)"
        >
          <QIcon icon="Book" size="24px" />
          <span class="text-08rem">书籍详情</span>
        </div>
      </div>
      <QDrawer
        v-model:visible="showCatalog"
        direction="left"
        :close-on-click-overlay="true"
      >
        <div class="book-read-catalog-container">
          <h3 class="text-one-line">
            {{ book.name }}
          </h3>
          <p class="container-space-between">
            <span>目录</span>
            <span
              class="mouse-cursor container gap-half"
              @click="catalogAscOrder = !catalogAscOrder"
            >
              {{ catalogAscOrder ? '升序' : '降序' }}
              <QIcon
                icon="Switch"
                :style="{
                  transform: `rotateZ(90deg) rotateY(${catalogAscOrder ? 0 : 180}deg) `,
                }"
                size="16px"
              />
            </span>
          </p>
          <div class="book-read-catalog scroll-container">
            <p
              v-for="item in computeCatalog"
              :key="item.id"
              class="bg-hover-secondary padding-24rem mouse-cursor radius-third-rem"
              :class="[
                {
                  'active-common': item.id === currentContentId,
                },
              ]"
              @click="
                () => {
                  run(item.id);
                  showCatalog = false;
                }
              "
            >
              {{ item.title }}
            </p>
          </div>
        </div>
      </QDrawer>
      <QDrawer
        :visible="shwoBottomSettings && isCanShowBottomSettings"
        direction="bottom"
        :close-on-click-overlay="true"
        @close="shwoBottomSettings = false"
      >
        <div
          class="container-space-between container-wrap"
          @click="shwoBottomSettings = false"
        >
          <div
            class="container-column container-align-center"
            @click="
              currentContentIndex > 0
                ? run(computeCatalog[currentContentIndex - 1].id)
                : void 0
            "
          >
            <QIcon icon="Up" size="24px" />
            <span class="text-08rem">上一章</span>
          </div>
          <div
            class="container-column container-align-center"
            @click="
              currentContentIndex < computeCatalog.length - 1
                ? run(computeCatalog[currentContentIndex + 1].id)
                : void 0
            "
          >
            <QIcon icon="Down" size="24px" />
            <span class="text-08rem">下一章</span>
          </div>
          <div
            class="container-column container-align-center"
            @click="showCatalog = true"
          >
            <QIcon icon="Catalog" size="24px" />
            <span class="text-08rem">目录</span>
          </div>
          <div class="container-column container-align-center">
            <QIcon icon="Setting" size="24px" />
            <span class="text-08rem">阅读设置</span>
          </div>
          <div
            class="container-column container-align-center"
            @click="router.push(`/book-detail/${book.id}`)"
          >
            <QIcon icon="Book" size="24px" />
            <span class="text-08rem">书籍详情</span>
          </div>
        </div>
      </QDrawer>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeMount,
  onBeforeUnmount,
  ref,
  useTemplateRef,
} from 'vue';
import type { Book, Catalog, ReadSettings } from '@guga-reading/types';
import { useBookStore } from '@/store';
import router from '@/route';
import { applySpacingToHtml, isHtml, useApiBooks } from '@guga-reading/shares';
import { useScreenSize, useThrottle } from 'qyani-components';
import { useReadingHistoryStore } from '@/store';
import { QLoading, QIcon, QDrawer } from 'qyani-components';
import { useApiReport } from '@guga-reading/shares';

// 用于切换时滚动到顶部
const bookReadContainer = useTemplateRef<HTMLDivElement>('bookReadContainer');
// 书籍信息
const book = ref<Book>({} as Book);
// 目录
const catalog = ref<Catalog[]>([] as Catalog[]);
// 书籍存储
const bookStore = useBookStore();
// 是否显示目录
const showCatalog = ref<boolean>(false);
// 当前内容
const content = ref<string>('');
// 目录呈现顺序
const catalogAscOrder = ref(true);
// 当前内容对应的章节ID
const currentContentId = ref<number>(-1);
// 当前内容在目录中的索引
const currentContentIndex = computed(() => {
  return catalog.value.findIndex((item) => item.id === currentContentId.value);
});
// 是否显示底部设置
const shwoBottomSettings = ref<boolean>(false);
// 是否可以显示底部设置
const isCanShowBottomSettings = useScreenSize.getWidth(768);
// 阅读历史存储
const readingHistoryStore = useReadingHistoryStore();
// 目录
const computeCatalog = computed(() => {
  if (catalogAscOrder.value) {
    return catalog.value;
  } else {
    return [...catalog.value].reverse();
  }
});
// 阅读设置
/* Songti SC, SimSun; */
const readSettings = ref<ReadSettings>({
  fontSize: '1rem',
  lineHeight: '3.5rem',
  letterSpacing: '0.1rem',
  fontFamily:
    'Arial, PingFangSC-Regular, Microsoft Yahei, "Source Han Serif SC", SimSun',
  color: 'var(--text-color)',
  backgroundColor: 'var(--card-bg)',
});
const heartBeat = {
  interval: 10000,
  timer: -1,
  task: async function () {
    if (currentContentId.value !== -1) {
      useApiReport.reportChapterRead(
        book.value.id,
        currentContentId.value,
        'heartbeat',
      );
    }
  },
  start: function () {
    this.stop();
    this.timer = setInterval(heartBeat.task, heartBeat.interval);
  },
  stop: function () {
    clearInterval(this.timer);
    this.timer = -1;
  },
};
const updateReadingHistory = useThrottle(
  async (bookId: number, chapterId: number, index: number) => {
    readingHistoryStore.update(bookId, chapterId, index);
  },
);
const loading = ref<boolean>(false);
/**
 * 加载章节内容
 * @param chapterId 章节ID
 */
const run = async (chapterId: number) => {
  // 如果当前内容ID等于要加载的内容ID,则返回,避免重复加载
  if (currentContentId.value === chapterId) {
    return;
  }
  loading.value = true;
  if (currentContentId.value !== -1) {
    // 上报离开当前章节
    useApiReport.reportChapterRead(
      book.value.id,
      currentContentId.value,
      'exit',
    );
    heartBeat.stop();
  }
  // 获取章节内容
  const { data } = await useApiBooks.getBookChapterById(
    book.value.id,
    chapterId,
  );
  const rawContent = data || '';
  // 处理章节内容
  const processedContent = isHtml(rawContent)
    ? rawContent
    : rawContent
        .split('\n')
        .map((item) => `<p>&nbsp;&nbsp;&nbsp;&nbsp;${item}</p>`)
        .join('');
  // 更新实际显示内容
  content.value = applySpacingToHtml(processedContent);
  // 更新当前内容ID
  currentContentId.value = chapterId;
  // 更新阅读历史
  updateReadingHistory(book.value.id, chapterId, currentContentIndex.value + 1);
  // 上报阅读数据,进入新章节
  useApiReport.reportChapterRead(book.value.id, chapterId, 'enter');
  heartBeat.start();
  // 等待DOM更新
  await nextTick();
  // 滚动到顶部
  bookReadContainer.value?.scrollTo({
    top: 0,
    behavior: 'smooth',
  });
  loading.value = false;
};
// 在挂载前执行
onBeforeMount(async () => {
  try {
    // 从路由参数获取书籍ID和内容ID
    const bookId = parseInt(router.currentRoute.value.params.bookId as string);
    const contentId = parseInt(
      router.currentRoute.value.params.contentId as string,
    );
    book.value.id = bookId;
    if (contentId !== -1) {
      //获取书籍信息和目录
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const [rawBook, rawCatalog, _] = await Promise.all([
        bookStore.getBookById(bookId),
        bookStore.getCatalogById(bookId),
        run(contentId),
      ]);
      book.value = rawBook;
      catalog.value = rawCatalog;
    } else {
      //获取书籍信息和目录,然后获取第一个目录的内容
      const [rawBook, rawCatalog] = await Promise.all([
        bookStore.getBookById(bookId),
        bookStore.getCatalogById(bookId),
      ]);
      catalog.value = rawCatalog;
      book.value = rawBook;
      if (catalog.value.length > 0) {
        run(catalog.value[0].id);
      }
    }
  } catch (e) {
    console.log(e);
  }
});
// 在卸载前上报离开当前章节
onBeforeUnmount(() => {
  if (currentContentId.value !== -1) {
    useApiReport.reportChapterRead(
      book.value.id,
      currentContentId.value,
      'exit',
    );
  }
  heartBeat.stop();
});
</script>

<style scoped lang="css">
.book-read-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 1rem 1rem 1rem;
  display: flex;
  justify-content: center;
}

.book-read-content {
  max-width: 700px;
}

.book-read-sidebar {
  position: fixed;
  top: 50%;
  left: calc(50vw + 450px);
  transform: translateY(-50%);
}

.book-read-catalog-container {
  min-width: 480px;
  padding: 0.5rem 1rem;
}

.book-read-catalog {
  max-height: calc(100vh - 4.5rem);
}

@media screen and (max-width: 768px) {
  .book-read-container {
    max-width: unset;
    width: 100%;
  }

  .book-read-content {
    max-width: unset;
    width: 100%;
  }

  .book-read-catalog-container {
    min-width: unset;
    max-width: 70vw;
  }
}
</style>
