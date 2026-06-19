<template>
  <div class="container-column">
    <h4>日志监控</h4>

    <!-- ===== 日志监控面板 ===== -->
    <div class="inner-container-column">
      <!-- 工具栏：文件选择 + 级别筛选 -->
      <div class="inner-container container-wrap">
        <!-- 文件选择 -->
        <div class="inner-container container-align-center">
          <label class="text-label">日志文件：</label>
          <select
            v-model="selectedFile"
            class="text-input"
            @change="onFileChange"
          >
            <option value="" disabled>选择日志文件</option>
            <option v-for="f in logFiles" :key="f.name" :value="f.name">
              {{ f.name }}（{{ formatBytes(f.size) }}）
            </option>
          </select>
        </div>

        <!-- 级别筛选标签 -->
        <div class="inner-container container-align-center">
          <label class="text-label">级别筛选：</label>
          <button
            v-for="lvl in logLevels"
            class="button"
            :key="lvl"
            :class="{
              'button-primary': selectedLevel === lvl,
              [`level-${lvl.toLowerCase()}`]: true,
            }"
            @click="onLevelChange(lvl)"
          >
            {{ lvl }}
          </button>
          <button
            class="button"
            :class="{ 'button-primary': selectedLevel === '' }"
            @click="onLevelChange('')"
          >
            全部
          </button>
        </div>
      </div>

      <!-- 日志内容展示 -->
      <div
        class="bg-card inner-container-column border-horizontal-gray padding-rem scroll-container"
        style="height: calc(100vh - 10rem)"
      >
        <QSkeleton v-if="loading" />

        <span v-else-if="errorMsg" class="text-danger">{{ errorMsg }}</span>
        <span v-else-if="logEntries.length === 0" class="text-muted"
          >暂无匹配的日志数据</span
        >
        <div v-else class="inner-container-column">
          <div
            v-for="entry in logEntries"
            :key="entry.lineNumber"
            class="inner-container-column"
          >
            <p>
              <span>{{ entry.lineNumber }}</span>
              <span class="margin-half-horizontal">{{ entry.timestamp }}</span>
              <span class="margin-half-horizontal">
                {{ entry.level }}
              </span>
              <span class="margin-half-horizontal" :title="entry.thread">{{
                entry.thread
              }}</span>
              <span class="margin-half-horizontal" :title="entry.logger">{{
                entry.logger
              }}</span>
            </p>
            <p class="padding-rem">{{ entry.message }}</p>
          </div>
        </div>
      </div>

      <!-- 分页 + 统计 -->
      <div
        class="inner-container container-align-center container-space-between"
      >
        <span class="text-description text-085rem">
          共 {{ total }} 条
          <template v-if="selectedFileObj">
            , 文件大小 {{ formatBytes(selectedFileObj?.size ?? 0) }}
          </template>
        </span>
        <div class="inner-container container-align-center">
          <button
            class="button"
            :disabled="page <= 1"
            @click="changePage(page - 1)"
          >
            上一页
          </button>
          <span class="text-085rem">{{ page }} / {{ totalPages }}</span>
          <button
            class="button"
            :disabled="page >= totalPages"
            @click="changePage(page + 1)"
          >
            下一页
          </button>
          <select
            v-model.number="pageSize"
            class="text-input"
            @change="onPageSizeChange"
          >
            <option :value="50">50条/页</option>
            <option :value="100">100条/页</option>
            <option :value="200">200条/页</option>
          </select>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onBeforeMount } from 'vue';
import { QSkeleton } from 'qyani-components';
import { formatBytes, useApiSystem } from '@guga-reading/shares';
import type { LogFileInfo, LogEntry } from '@guga-reading/types';

defineOptions({ name: 'SystemManage' });

// 日志级别
const logLevels = ['DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'] as const;

// 文件列表
const logFiles = ref<LogFileInfo[]>([]);
const selectedFile = ref('');

const selectedFileObj = computed(() =>
  logFiles.value.find((f) => f.name === selectedFile.value),
);

// 日志内容
const logEntries = ref<LogEntry[]>([]);
const selectedLevel = ref('');
const page = ref(1);
const pageSize = ref(100);
const total = ref(0);
const loading = ref(false);
const errorMsg = ref('');

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize.value)),
);

/** 加载日志文件列表 */
async function loadLogFiles() {
  const { success, data } = await useApiSystem.getLogFiles();
  if (success && data) {
    logFiles.value = data;
    if (data.length > 0 && !selectedFile.value) {
      const firstFile = data[0]!;
      selectedFile.value = firstFile.name;
      loadLogContent();
    }
  }
}

/** 加载日志内容 */
async function loadLogContent() {
  if (!selectedFile.value) return;
  loading.value = true;
  errorMsg.value = '';
  try {
    const { success, data } = await useApiSystem.readLog(
      selectedFile.value,
      selectedLevel.value || undefined,
      page.value,
      pageSize.value,
    );
    if (success && data) {
      logEntries.value = data.items || [];
      total.value = data.total || 0;
    } else {
      logEntries.value = [];
      total.value = 0;
    }
  } catch {
    errorMsg.value = '加载日志失败，请检查文件是否存在';
    logEntries.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 切换文件 */
function onFileChange() {
  page.value = 1;
  errorMsg.value = '';
  loadLogContent();
}

/** 切换级别 */
function onLevelChange(level: string) {
  selectedLevel.value = level;
  page.value = 1;
  loadLogContent();
}

/** 翻页 */
function changePage(newPage: number) {
  page.value = newPage;
  loadLogContent();
}

/** 修改每页条数 */
function onPageSizeChange() {
  page.value = 1;
  loadLogContent();
}

onBeforeMount(() => {
  loadLogFiles();
});
</script>
