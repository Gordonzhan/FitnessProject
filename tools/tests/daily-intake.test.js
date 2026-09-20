const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

function harness() {
  let page;
  let modal;
  let modalCount = 0;
  let failRemove = false;
  let failRefresh = false;
  let rows = [
    { intakeId: '101', recipeId: 'same-recipe', mealType: '鸡胸饭', calorie: 300 },
    { intakeId: '102', recipeId: 'same-recipe', mealType: '鸡胸饭', calorie: 300 }
  ];
  const removals = [];
  const toasts = [];
  const api = {
    removeIntake: async params => {
      removals.push(params);
      if (failRemove) throw new Error('offline');
      rows = rows.filter(row => row.intakeId !== params.intakeId);
    },
    getDailyData: async () => {
      if (failRefresh) throw new Error('offline');
      return { intake: rows.length * 300, expenditure: 100, difference: rows.length * 300 - 100,
        tipText: '最新提示', selectedRecipes: rows.slice() };
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/compare/compare.js'), 'utf8'), {
    getApp: () => ({ ensureLogin: async () => {} }),
    require: () => ({ CompareAPI: api }),
    Page: definition => { page = definition; },
    wx: {
      getStorageSync: () => { throw new Error('must not read stale local food caches'); },
      showModal: options => { modal = options; modalCount++; },
      showToast: options => toasts.push(options), switchTab() {}
    }, console
  });
  page.setData = update => Object.assign(page.data, update);
  page.onLoad();
  Object.assign(page.data, { currentDate: '2026-09-05', userProfile: { goal: 'maintain' },
    selectedRecipes: rows.slice(), todayIntake: '600.0', todayExpenditure: '100.0', calorieDifference: '500.0' });
  page._loadedDate = page.data.currentDate;
  return { page, api, removals, toasts, modal: () => modal, modalCount: () => modalCount,
    failRemove: () => { failRemove = true; }, failRefresh: () => { failRefresh = true; } };
}
const event = id => ({ currentTarget: { dataset: { intakeid: id } } });

test('cancel confirmation does not remove a record or change statistics', async () => {
  const h = harness();
  h.page.removeDailyIntake(event('101'));
  assert.match(h.modal().content, /不会删除食谱库/);
  await h.modal().success({ confirm: false });
  assert.equal(h.removals.length, 0);
  assert.equal(h.page.data.selectedRecipes.length, 2);
  assert.equal(h.page.data.todayIntake, '600.0');
  assert.equal(h.page.data.removingIntakeId, '');
});

test('confirmation removes only one serving and refreshes calories', async () => {
  const h = harness();
  h.page.removeDailyIntake(event('101'));
  await h.modal().success({ confirm: true });
  assert.equal(h.removals.length, 1);
  assert.equal(h.removals[0].date, '2026-09-05');
  assert.equal(h.removals[0].intakeId, '101');
  assert.equal(h.page.data.selectedRecipes.length, 1);
  assert.equal(h.page.data.selectedRecipes[0].intakeId, '102');
  assert.equal(h.page.data.todayIntake, '300.0');
  assert.equal(h.page.data.calorieDifference, '200.0');
});

test('double taps and date navigation are blocked while confirming removal', async () => {
  const h = harness();
  h.page.removeDailyIntake(event('101'));
  h.page.removeDailyIntake(event('102'));
  h.page.prevDay();
  assert.equal(h.modalCount(), 1);
  assert.equal(h.page.data.currentDate, '2026-09-05');
  await h.modal().success({ confirm: true });
  assert.equal(h.removals.length, 1);
});

test('removal failure keeps rows, unlocks controls and requests a fresh read', async () => {
  const h = harness();
  h.failRemove();
  h.page.removeDailyIntake(event('101'));
  await h.modal().success({ confirm: true });
  assert.equal(h.page.data.selectedRecipes.length, 2);
  assert.match(h.page.data.dailyDataError, /移除未确认/);
  assert.equal(h.page.data.removingIntakeId, '');
  assert.equal(await h.page.calculateCalories(), true);
  assert.equal(h.page.data.dailyDataError, '');
});

test('successful removal followed by refresh failure is not reported as removal failure', async () => {
  const h = harness();
  h.failRefresh();
  h.page.removeDailyIntake(event('101'));
  await h.modal().success({ confirm: true });
  assert.equal(h.page.data.selectedRecipes.length, 1);
  assert.match(h.page.data.dailyDataError, /加载失败/);
  assert.match(h.toasts[0].title, /已移除/);
  assert.equal(h.page.data.loadingDailyData, false);
});

test('late response for another day cannot overwrite the selected date', async () => {
  const h = harness();
  const replies = [];
  h.api.getDailyData = () => new Promise(resolve => replies.push(resolve));
  const old = h.page.calculateCalories();
  h.page.setData({ currentDate: '2026-09-06' });
  const current = h.page.calculateCalories();
  replies[1]({ intake: 0, expenditure: 0, difference: 0, selectedRecipes: [], tipText: '' });
  await current;
  replies[0]({ intake: 600, expenditure: 0, difference: 600, selectedRecipes: [{ intakeId: '101' }], tipText: '' });
  await old;
  assert.equal(h.page._loadedDate, '2026-09-06');
  assert.equal(h.page.data.selectedRecipes.length, 0);
  assert.equal(h.page.data.todayIntake, '0.0');
});

test('stale rows and unloaded pages cannot issue removal requests', () => {
  const h = harness();
  h.page.setData({ currentDate: '2026-09-06' });
  h.page.removeDailyIntake(event('101'));
  assert.equal(h.modalCount(), 0);
  h.page.setData({ currentDate: '2026-09-05' });
  h.page.onUnload();
  h.page.removeDailyIntake(event('101'));
  assert.equal(h.modalCount(), 0);
});

test('removing the last serving empties the list and recalculates the balance', async () => {
  const h = harness();
  h.page.removeDailyIntake(event('101'));
  await h.modal().success({ confirm: true });
  h.page.removeDailyIntake(event('102'));
  await h.modal().success({ confirm: true });
  assert.equal(h.page.data.selectedRecipes.length, 0);
  assert.equal(h.page.data.todayIntake, '0.0');
  assert.equal(h.page.data.calorieDifference, '-100.0');
});
