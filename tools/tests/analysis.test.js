const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const flush = () => new Promise(resolve => setImmediate(resolve));

function dateApi() {
  function parse(value) {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day, 12);
  }
  function format(date) {
    const pad = value => String(value).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }
  return {
    toLocalDateString: () => '2026-09-07',
    parseLocalDate: parse,
    addLocalDays(value, days) { const date = parse(value); date.setDate(date.getDate() + days); return format(date); },
    compareLocalDates: (left, right) => left.localeCompare(right)
  };
}

function summary() {
  return {
    totalDays: 7,
    dataState: 'PARTIAL',
    dailyPoints: [
      { date: '2026-09-01', intakeCalories: 1800, actualExpenditure: 250, energyDifference: 1550, protein: 120, carb: 200, fat: 60, hasNutrition: true },
      { date: '2026-09-02', intakeCalories: 1900, actualExpenditure: 0, energyDifference: 1900, protein: 125, carb: 210, fat: 62, hasNutrition: true }
    ],
    averages: { nutritionRecordedDays: 2, intakeCalories: 1850, actualExpenditure: 35.71, energyDifference: 1725 },
    trainingCompletion: { completed: 1, planned: 1, cancelled: 0, rate: 50 },
    goalAssessment: { status: 'DATA_INSUFFICIENT', title: '数据不足', detail: '至少记录3天', hasWeightTrend: false },
    macroReference: { referenceWeight: 70, protein: { min: 112, max: 154, unit: 'g/天' }, carb: { min: 140, max: 280, unit: 'g/天' }, fat: { min: 42, max: 70, unit: 'g/天' } },
    weightPoints: [], notices: ['记录可能不完整', '仅供健身参考']
  };
}

function harness(overrides = {}) {
  let page;
  const calls = [];
  const toasts = [];
  const storage = { userProfile: { weight: 70 } };
  const api = {
    getSummary: async (start, end) => { calls.push(['summary', start, end]); return summary(); },
    recordWeight: async (date, weight) => { calls.push(['weight', date, weight]); },
    ...overrides
  };
  const app = { ensureLogin: async () => {}, globalData: {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/profile/analysis/analysis.js'), 'utf8'), {
    require(request) {
      if (request.includes('ec-canvas/echarts')) return { init() {} };
      if (request.includes('utils/api')) return { AnalysisAPI: api };
      return dateApi();
    },
    getApp: () => app,
    Page: value => { page = value; },
    wx: {
      nextTick(callback) { callback(); },
      showToast(value) { toasts.push(value.title); },
      getStorageSync(key) { return storage[key]; },
      setStorageSync(key, value) { storage[key] = value; }
    },
    console, Promise, Number, Date, Math
  });
  page.setData = update => Object.assign(page.data, update);
  page.selectComponent = () => null;
  page.onLoad();
  return { page, calls, toasts, storage, app };
}

test('analysis opens with a local-date seven-day range and bounded presets', async () => {
  const h = harness();
  await flush();
  assert.deepEqual(h.calls[0], ['summary', '2026-09-01', '2026-09-07']);
  assert.equal(h.page.data.summary.dailyPoints, undefined);
  assert.equal(h.page.data.summary.weightPointCount, 0);
  h.page.selectPreset({ currentTarget: { dataset: { days: 90 } } });
  await flush();
  assert.deepEqual(h.calls[1], ['summary', '2026-06-10', '2026-09-07']);
});

test('range changes keep chart instances and lazily update instead of rebuilding canvases', async () => {
  const h = harness();
  await flush();
  let energyUpdates = 0;
  let macroUpdates = 0;
  let disposals = 0;
  h.page._energyChart = {
    setOption(option, settings) {
      energyUpdates++;
      assert.equal(settings.lazyUpdate, true);
      assert.equal(settings.notMerge, true);
    },
    dispose() { disposals++; }
  };
  h.page._macroChart = {
    setOption(option, settings) {
      macroUpdates++;
      assert.equal(settings.lazyUpdate, true);
      assert.equal(settings.notMerge, true);
    },
    dispose() { disposals++; }
  };

  h.page.selectPreset({ currentTarget: { dataset: { days: 30 } } });
  assert.equal(h.page.data.loading, false);
  assert.equal(h.page.data.refreshing, true);
  assert.equal(disposals, 0);
  await flush();

  assert.equal(h.page.data.refreshing, false);
  assert.equal(disposals, 0);
  assert.equal(energyUpdates, 1);
  assert.equal(macroUpdates, 1);
});

test('custom ranges over ninety days are rejected before an API call', async () => {
  const h = harness();
  await flush();
  h.page.setData({ activePreset: 0, startDate: '2026-01-01', endDate: '2026-09-07' });
  h.page.applyCustomRange();
  assert.equal(h.calls.length, 1);
  assert.equal(h.toasts.at(-1), '单次分析最多90天');
});

test('chart options use server aggregates and keep actual expenditure separate', async () => {
  const h = harness();
  await flush();
  const option = h.page.energyOption(['09-01', '09-02'], summary().dailyPoints);
  assert.deepEqual(option.series[0].data, [1800, 1900]);
  assert.deepEqual(option.series[1].data, [250, 0]);
  assert.deepEqual(option.series[2].data, [1550, 1900]);
  assert.equal(option.animation, false);
});

test('recording today weight updates local profile and refreshes the selected range', async () => {
  const h = harness();
  await flush();
  h.page.setData({ weightDate: '2026-09-07', weightValue: '68.5' });
  h.page.saveWeight();
  await flush();
  await flush();
  assert.deepEqual(h.calls[1], ['weight', '2026-09-07', 68.5]);
  assert.equal(h.storage.userProfile.weight, 68.5);
  assert.equal(h.storage.userWeight, 68.5);
  assert.equal(h.calls[2][0], 'summary');
});

test('R5 page declares ECharts and required fitness-reference notices', () => {
  const json = fs.readFileSync(path.join(__dirname, '../../pages/profile/analysis/analysis.json'), 'utf8');
  const wxml = fs.readFileSync(path.join(__dirname, '../../pages/profile/analysis/analysis.wxml'), 'utf8');
  const bundleEntry = fs.readFileSync(path.join(__dirname, '../r5-echarts-entry.js'), 'utf8');
  assert.match(json, /ec-canvas/);
  assert.match(wxml, /仅计已完成训练实际消耗/);
  assert.match(wxml, /仅供健身参考/);
  assert.match(wxml, /loading && !summary/);
  assert.match(wxml, /hidden="\{\{summary\.dataState === 'NO_DATA'\}\}"/);
  assert.match(bundleEntry, /LineChart, BarChart/);
  assert.doesNotMatch(bundleEntry, /PieChart|MapChart/);
});
