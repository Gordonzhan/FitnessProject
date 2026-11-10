const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function read(relative) {
  return fs.readFileSync(path.join(__dirname, '../..', relative), 'utf8');
}

test('A5 exposes draft create, replace, remove and explicit confirmation APIs', () => {
  const api = read('utils/api.js');
  assert.match(api, /createWorkoutDraft/);
  assert.match(api, /replaceWorkoutExercise/);
  assert.match(api, /removeWorkoutExercise/);
  assert.match(api, /\/ai\/workout-draft\/remove/);
  assert.match(api, /confirmWorkoutDraft/);
  assert.match(api, /\/ai\/workout-draft\/confirm/);
});

test('AI workout draft remains unpersisted until the user confirms', () => {
  const source = read('pages/workout/ai-draft/ai-draft.js');
  const template = read('pages/workout/ai-draft/ai-draft.wxml');
  assert.doesNotMatch(source, /WorkoutAPI\.save/);
  assert.match(source, /showModal/);
  assert.match(source, /confirmWorkoutDraft/);
  assert.match(source, /replaceWorkoutExercise/);
  assert.match(source, /removeWorkoutExercise/);
  assert.match(source, /至少保留两个动作/);
  assert.match(template, /删除中/);
  assert.match(template, /训练重量/);
  assert.match(template, /辅助重量/);
  assert.match(template, /待选择/);
  assert.match(template, /自重/);
  assert.match(source, /inputExerciseWeight/);
  assert.match(source, /inputExerciseSets/);
  assert.match(source, /组数请输入1到10之间的整数/);
  assert.match(source, /ensureValidSets/);
  assert.match(source, /invalidSetCount/);
  assert.match(template, /组（可改）/);
  assert.match(source, /missingWeightCount/);
  assert.match(source, /请先填写/);
  assert.match(source, /message: this\.data\.activeRequest/);
  assert.match(source, /currentDraft: this\.data\.draft \|\| null/);
  assert.doesNotMatch(source, /then\(\(\) => this\.generate\(\)\)/);
  assert.match(template, /确认前不会保存/);
  assert.match(template, /饮食计划/);
  assert.match(template, /勾选实际要记录的餐食/);
  assert.match(template, /加入今日饮食/);
  assert.match(source, /nutritionAccepted/);
  assert.match(source, /CompareAPI\.addRecipe/);
  assert.match(source, /servingCount: 1/);
  assert.match(source, /只能在训练当天记入实际饮食/);
  assert.match(source, /_nutritionIntakeRequestIds/);
  assert.match(template, /放弃草稿/);
});

test('training page offers AI generation only through the dedicated draft page', () => {
  const source = read('pages/workout/workout.js');
  const template = read('pages/workout/workout.wxml');
  const app = read('app.json');
  assert.match(source, /openAiDraft/);
  assert.match(template, /根据训练偏好生成计划/);
  assert.match(template, /AI 重新生成/);
  assert.match(template, /wx:if="\{\{canGenerateAi\}\}" class="plan-primary" bindtap="openAiDraft"/);
  assert.match(template, /补录实际训练/);
  assert.match(source, /currentWorkout\.status === 'CANCELLED'/);
  assert.match(source, /replaceCancelled=1/);
  assert.match(source, /历史日期只能补录实际训练/);
  assert.match(app, /pages\/workout\/ai-draft\/ai-draft/);
});

test('AI confirmation explains that a cancelled plan will be overwritten', () => {
  const source = read('pages/workout/ai-draft/ai-draft.js');
  assert.match(source, /options\.replaceCancelled === '1'/);
  assert.match(source, /覆盖当天已取消的计划/);
});

test('selected AI meal suggestions are persisted as actual daily intake', async () => {
  let page;
  const calls = [];
  vm.runInNewContext(read('pages/workout/ai-draft/ai-draft.js'), {
    require: modulePath => modulePath.includes('utils/api')
      ? { AiAPI: {}, CompareAPI: { addRecipe: payload => { calls.push(payload); return Promise.resolve(); } } }
      : { toLocalDateString: () => '2026-10-10', addLocalDays: value => value,
        compareLocalDates: (left, right) => left.localeCompare(right) },
    getApp: () => ({ ensureLogin: () => Promise.resolve() }),
    Page: definition => { page = definition; },
    wx: {
      showModal: options => options.success({ confirm: true }),
      showToast() {}
    },
    console,
    Date,
    Math,
    Promise
  });
  page.setData = update => Object.assign(page.data, update);
  page._nutritionIntakeRequestIds = {};
  page.data.date = '2026-10-10';
  page.data.canRecordNutrition = true;
  page.data.draft = { nutritionPlan: {
    dailyTargetCalories: 2000, consumedCalories: 100, remainingCalories: 1900
  } };
  page.data.nutritionMeals = [
    { recipeId: 'breakfast-1', mealSlot: '早餐', calories: 500, selectedForIntake: true },
    { recipeId: 'lunch-1', mealSlot: '午餐', calories: 700, selectedForIntake: false },
    { recipeId: 'dinner-1', mealSlot: '晚餐', calories: 600, selectedForIntake: true }
  ];

  page.acceptNutritionPlan();
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(calls.length, 2);
  assert.deepEqual(calls.map(item => item.recipeId), ['breakfast-1', 'dinner-1']);
  assert.equal(calls[0].mealSlot, 'BREAKFAST');
  assert.equal(calls[0].servingCount, 1);
  assert.match(calls[0].requestId, /^ai-intake-/);
  assert.equal(page.data.nutritionAccepted, true);
  assert.equal(page.data.draft.nutritionPlan.consumedCalories, 1200);
});
