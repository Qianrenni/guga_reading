<!-- AuthorWritingEditor.vue -->
<template>
  <div style="width: 1000px">
    <HeatMapChart
      :option="option"
      class="container-w100"
      style="height: 400px"
    />
  </div>
</template>

<script setup lang="ts">
import HeatMapChart from '@/components/chart/HeatMapChart.vue';
import { echarts } from '@/components/chart/composable';
import type { ECOption } from '@/components/chart/composable';

function getVirtualData(year: string) {
  const date = +echarts.time.parse(year + '-01-01');
  const end = +echarts.time.parse(+year + 1 + '-01-01');
  const dayTime = 3600 * 24 * 1000;
  const data = [];
  for (let time = date; time < end; time += dayTime) {
    data.push([
      echarts.time.format(time, '{yyyy}-{MM}-{dd}', false),
      Math.floor(Math.random() * 10000),
    ]);
  }
  return data;
}
const option: ECOption = {
  title: {
    top: 30,
    left: 'center',
    text: 'Daily Step Count',
  },
  tooltip: {},
  visualMap: {
    min: 0,
    max: 10000,
    type: 'piecewise',
    orient: 'horizontal',
    left: 'center',
    top: 65,
  },
  calendar: {
    top: 120,
    left: 30,
    right: 30,
    cellSize: ['auto', 13],
    range: '2016',
    itemStyle: {
      borderWidth: 0.5,
    },
    yearLabel: { show: false },
  },
  series: {
    type: 'heatmap',
    coordinateSystem: 'calendar',
    data: getVirtualData('2016'),
  },
};
console.log(getVirtualData('2016'));
</script>
