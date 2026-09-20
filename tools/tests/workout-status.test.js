const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const dateApi = {
  toLocalDateString: () => '2026-09-06',
  addLocalDays(value, days) {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(Date.UTC(year, month - 1, day + days));
    return date.toISOString().slice(0, 10);
  },
  compareLocalDates: (left, right) => left.localeCompare(right)
};

const flush = () => new Promise(resolve => setImmediate(resolve));

function workoutHarness(overrides = {}, options = {}) {
  let page;
  const navigations = [];
  const api = {
    getByDate: async () => null,
    complete: async () => {},
    cancel: async () => {},
    ...overrides
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/workout/workout.js'), 'utf8'), {
    require: request => request.includes('utils/api') ? { WorkoutAPI: api } : dateApi,
    getApp: () => ({ ensureLogin: async () => {} }),
    Page: value => { page = value; },
    wx: {
      showToast() {},
      showModal(value) { if (options.autoConfirm) value.success({ confirm: true }); },
      navigateTo(value) { navigations.push(value.url); }
    },
    console, Promise, Number
  });
  page.setData = update => Object.assign(page.data, update);
  page.onLoad();
  return { page, api, navigations };
}

function addWorkoutHarness(overrides = {}) {
  let page;
  const saves = [];
  const api = {
    getByDate: async () => null,
    save: async value => { saves.push(value); },
    ...overrides
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/workout/add-workout/add-workout.js'), 'utf8'), {
    require: request => request.includes('utils/api')
      ? { WorkoutAPI: api, DatabaseAPI: { resolveExercises: async () => [] } }
      : dateApi,
    getApp: () => ({ ensureLogin: async () => {} }),
    Page: value => { page = value; },
    wx: {
      showToast() {}, navigateBack() {},
      getStorageSync: () => 70
    },
    setTimeout: callback => callback(),
    console, Promise, Number, Map, Set, Date, Math, parseInt
  });
  page.setData = update => Object.assign(page.data, update);
  return { page, saves };
}

test('future dates are presented as plans and limited to ninety days', async () => {
  const h = workoutHarness();
  h.page.nextDay();
  await flush();
  assert.equal(h.page.data.currentDate, '2026-09-07');
  assert.equal(h.page.data.isFuture, true);
  assert.equal(h.page.data.dateContextLabel, '训练计划');
  h.page.addWorkout();
  assert.equal(h.navigations[0], 'add-workout/add-workout?date=2026-09-07');

  h.page.setData({ currentDate: '2026-12-05' });
  h.page.nextDay();
  assert.equal(h.page.data.currentDate, '2026-12-05');
});

test('fixed workout shortcut ignores rapid duplicate navigation', () => {
  const h = workoutHarness();
  h.page.addWorkout();
  h.page.addWorkout();
  assert.equal(h.navigations.length, 1);
});

test('quick action appends one exercise and immediately opens the selector', () => {
  const h = addWorkoutHarness();
  h.page.setData({ loadingWorkout: false });
  h.page.quickAddExercise();
  h.page.quickAddExercise();
  assert.equal(h.page.data.exercises.length, 1);
  assert.equal(h.page.data.selectorVisible, true);
  assert.equal(h.page.data.selectingExerciseIndex, 0);
});

test('planned workout displays estimated calories instead of actual calories', () => {
  const h = workoutHarness();
  const normalized = h.page.normalizeWorkout({
    status: 'PLANNED', totalCalories: 120, estimatedCalories: 135, actualCalories: null
  });
  assert.equal(normalized.statusText, '计划中');
  assert.equal(normalized.displayCalories, '135.0');
});

test('due plan confirmation records the estimate as actual expenditure', async () => {
  const calls = [];
  const h = workoutHarness({ complete: async (...args) => { calls.push(args); } }, { autoConfirm: true });
  h.page.setData({
    currentDate: '2026-09-06', isFuture: false,
    currentWorkout: { workoutId: 'due-plan', status: 'PLANNED', estimatedCalories: '88.5' }
  });
  h.page.loadWorkout = async () => {};
  h.page.completePlan();
  await flush();
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'due-plan');
  assert.equal(calls[0][1].actualCalories, 88.5);
});

test('future editor saves PLANNED while today editor saves COMPLETED', async () => {
  const future = addWorkoutHarness();
  future.page.onLoad({ date: '2026-09-07' });
  await flush();
  future.page.setData({
    exercises: [{ name: '深蹲', weight: '20', sets: '3', reps: '10', calories: '12.0', databaseMatched: true }],
    unknownExercises: [], totalCalories: '12.0', loadingWorkout: false
  });
  future.page.saveWorkout();
  await flush();
  assert.equal(future.saves[0].status, 'PLANNED');

  const today = addWorkoutHarness();
  today.page.onLoad({ date: '2026-09-06' });
  await flush();
  today.page.setData({
    exercises: [{ name: '深蹲', weight: '20', sets: '3', reps: '10', calories: '12.0', databaseMatched: true }],
    unknownExercises: [], totalCalories: '12.0', loadingWorkout: false
  });
  today.page.saveWorkout();
  await flush();
  assert.equal(today.saves[0].status, 'COMPLETED');
});

test('today editor can create a pending plan for immediate status-flow testing', () => {
  const h = addWorkoutHarness();
  h.page.onLoad({ date: '2026-09-06' });
  assert.equal(h.page.data.canChooseStatus, true);
  h.page.selectStatus({ currentTarget: { dataset: { status: 'PLANNED' } } });
  assert.equal(h.page.data.status, 'PLANNED');
  assert.equal(h.page.data.saveText, '保存训练计划');
});

test('workout UI explains plan, completed and cancelled states', () => {
  const wxml = fs.readFileSync(path.join(__dirname, '../../pages/workout/workout.wxml'), 'utf8');
  assert.match(wxml, /计划不会提前计入实际消耗/);
  assert.match(wxml, /确认已完成/);
  assert.match(wxml, /取消计划/);
  assert.match(wxml, /实际消耗已计入能量/);
});
