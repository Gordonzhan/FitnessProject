const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const flush = () => new Promise(resolve => setImmediate(resolve));

function activitySummary(points = []) {
  return {
    startDate: '2026-08-08', endDate: '2026-09-07', source: 'WECHAT_WERUN',
    sourceLabel: '微信运动汇总步数', estimationVersion: 'STEP_WEIGHT_V1',
    estimationFormula: '步数 × 同步时体重(kg) × 0.0005',
    lastSyncedAt: points.length ? '2026-09-07T23:30:00' : null,
    recordedDays: points.length, dailyPoints: points,
    notices: ['估算参考', '设备来源不可识别', '不参与阶段分析中的能量差']
  };
}

function harness(overrides = {}) {
  let page;
  const calls = [];
  const toasts = [];
  const api = {
    getWechatSummary: async () => activitySummary(),
    syncWechat: async payload => {
      calls.push(['sync', payload]);
      return activitySummary([{ date: '2026-09-07', steps: 8131, estimatedCalories: 284.59, referenceWeight: 70 }]);
    },
    deleteWechatData: async () => { calls.push(['delete']); },
    ...overrides
  };
  const wx = {
    login(config) { config.success({ code: 'fresh-code' }); },
    getWeRunData(config) {
      config.success({ cloudID: 'cloud-id', encryptedData: 'ciphertext', iv: 'vector' });
    },
    openSetting(config) { config.success({ authSetting: { 'scope.werun': true } }); },
    showToast(config) { toasts.push(config.title); },
    showModal(config) { config.success({ confirm: true }); },
    stopPullDownRefresh() {}
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/profile/activity/activity.js'), 'utf8'), {
    require: () => ({ ActivityAPI: api }),
    getApp: () => ({ ensureLogin: async () => {} }),
    Page: definition => { page = definition; },
    wx, console, Promise, Number, String, Date, Math
  });
  page.setData = update => Object.assign(page.data, update);
  page.onLoad();
  return { page, api, calls, toasts, wx };
}

test('R6 opens empty without changing any energy analysis data', async () => {
  const h = harness();
  await flush();
  await flush();
  assert.equal(h.page.data.summary.recordedDays, 0);
  assert.equal(h.page.data.todaySteps, 0);
  assert.equal(h.page.data.sevenDayAverage, 0);
});

test('user-triggered sync sends short-lived cloudID with encrypted fallback', async () => {
  const h = harness();
  await flush();
  await h.page.syncWechat();
  assert.equal(h.calls[0][0], 'sync');
  assert.deepEqual(JSON.parse(JSON.stringify(h.calls[0][1])), {
    code: 'fresh-code', cloudId: 'cloud-id', encryptedData: 'ciphertext', iv: 'vector'
  });
  assert.equal(h.page.data.todaySteps, 8131);
  assert.equal(h.page.data.todayCalories, '284.6');
  assert.equal(h.page.data.sevenDayAverage, 1162);
  assert.equal(h.toasts.at(-1), '微信步数已同步');
});

test('cloudID-only WeRun response is accepted in cloud hosting', async () => {
  const h = harness();
  await flush();
  h.wx.getWeRunData = config => config.success({ cloudID: 'cloud-only' });
  await h.page.syncWechat();
  assert.equal(h.calls[0][0], 'sync');
  assert.deepEqual(JSON.parse(JSON.stringify(h.calls[0][1])), {
    code: 'fresh-code', cloudId: 'cloud-only', encryptedData: '', iv: ''
  });
});

test('authorization refusal keeps the core page usable and exposes settings recovery', async () => {
  const h = harness();
  await flush();
  h.wx.getWeRunData = config => config.fail({ errMsg: 'getWeRunData:fail auth deny' });
  await h.page.syncWechat();
  assert.equal(h.page.data.permissionDenied, true);
  assert.match(h.page.data.error, /没有授权微信运动/);
  assert.equal(h.page.data.summary.recordedDays, 0);
});

test('confirmed deletion clears only server-side synchronized activity copy', async () => {
  let loadCount = 0;
  const h = harness({
    getWechatSummary: async () => ++loadCount === 1
      ? activitySummary([{ date: '2026-09-07', steps: 1000, estimatedCalories: 35, referenceWeight: 70 }])
      : activitySummary()
  });
  await flush();
  await flush();
  h.page.deleteWechatData();
  await flush();
  await flush();
  assert.deepEqual(h.calls[0], ['delete']);
  assert.equal(h.page.data.summary.recordedDays, 0);
});

test('R6 source and UI explicitly keep estimates outside workouts and R5 energy difference', () => {
  const js = fs.readFileSync(path.join(__dirname, '../../pages/profile/activity/activity.js'), 'utf8');
  const wxml = fs.readFileSync(path.join(__dirname, '../../pages/profile/activity/activity.wxml'), 'utf8');
  const api = fs.readFileSync(path.join(__dirname, '../../utils/api.js'), 'utf8');
  assert.match(wxml, /不会改变你的能量差/);
  assert.match(wxml, /不会写入训练记录/);
  assert.match(wxml, /不能替代 Apple Watch 的实测活动热量/);
  assert.doesNotMatch(js, /AnalysisAPI|WorkoutAPI|actualCalories/);
  assert.match(api, /activity\/wechat\/sync/);
});
