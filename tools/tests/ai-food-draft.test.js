const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

function pageHarness(draft, overrides = {}) {
  let page;
  let requestPayload;
  const context = {
    getApp: () => ({ ensureLogin: () => Promise.resolve() }),
    Page: definition => { page = definition; },
    require: name => name.includes('image-upload') ? require('../../utils/image-upload') : {
      AiAPI: { createFoodDraft: async payload => { requestPayload = payload; return draft; } },
      RecipeAPI: overrides.RecipeAPI || {}, DatabaseAPI: {}, ImageAPI: {}
    },
    wx: { showToast() {}, navigateBack() {} },
    console,
    setTimeout
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,
    '../../pages/recipe/add-recipe/add-recipe.js'), 'utf8'), context);
  page.setData = (update, callback) => {
    Object.assign(page.data, update);
    if (callback) callback();
  };
  page.onLoad({});
  return { page, requestPayload: () => requestPayload };
}

test('AI draft uses uploaded image and keeps ambiguous foods editable until user selection', async () => {
  const candidate = {
    id: 9, foodName: '熟鸡胸肉', category: '肉禽水产', servingState: '熟重',
    proteinPer100g: 32.1, carbohydratePer100g: 0, fatPer100g: 3.2,
    caloriesPer100g: 166, genericFallback: true
  };
  const h = pageHarness({
    requestId: 'ai-draft-1', warnings: ['请选择食材'],
    recipeGenerationStatus: 'SUCCEEDED',
    recipeSuggestion: {
      name: '香煎鸡胸肉', steps: ['鸡胸肉调味。', '煎至熟透后静置。'],
      disclaimer: 'AI 推测，请按实际情况修改。'
    },
    items: [{
      recognizedName: '鸡胸', estimatedWeightGrams: 150,
      minimumWeightGrams: 120, maximumWeightGrams: 180,
      confidence: 0.82, matchStatus: 'NEEDS_SELECTION', matchedFood: null,
      candidates: [candidate]
    }]
  });
  h.page.data.images = ['https://bucket.example/users/1/food.jpg'];

  await h.page.recognizeFood();

  assert.deepEqual(h.requestPayload().imageUrls, h.page.data.images);
  assert.equal(h.page.data.aiDraftReady, true);
  assert.equal(h.page.data.ingredients[0].databaseMatched, false);
  assert.equal(h.page.data.totalNutrients.calorie, '0.0');
  assert.equal(h.page.data.recipeName, '香煎鸡胸肉');
  assert.equal(h.page.data.steps, '1. 鸡胸肉调味。\n2. 煎至熟透后静置。');
  assert.equal(h.page.data.aiRecipeSuggested, true);

  h.page.applyAiCandidate({ currentTarget: { dataset: { index: 0, candidateIndex: 0 } } });
  assert.equal(h.page.data.ingredients[0].foodName, '熟鸡胸肉');
  assert.equal(h.page.data.ingredients[0].databaseMatched, true);
  assert.equal(h.page.data.totalNutrients.calorie, '249.0');
  assert.equal(h.page.data.ingredients[0].foodReference.genericFallback, true);
  const payload = JSON.parse(JSON.stringify(
    h.page.ingredientSavePayload(h.page.data.ingredients[0])));
  assert.deepEqual(payload, {
    foodName: '熟鸡胸肉', recognizedName: '鸡胸', foodDatabaseId: 9,
    mappingSource: 'AI_USER_SELECTED', weight: 150,
    protein: '48.1', carb: '0.0', fat: '4.8', calorie: '249.0'
  });
});

test('AI recipe suggestion never overwrites user-entered name or steps', async () => {
  const h = pageHarness({
    requestId: 'ai-draft-2', warnings: [], items: [],
    recipeSuggestion: {
      name: 'AI 菜名', steps: ['AI 步骤'], disclaimer: 'AI 推测'
    }
  });
  h.page.data.images = ['https://bucket.example/users/1/food.jpg'];
  h.page.data.recipeName = '我的菜名';
  h.page.data.steps = '我的真实做法';

  await h.page.recognizeFood();

  assert.equal(h.page.data.recipeName, '我的菜名');
  assert.equal(h.page.data.steps, '我的真实做法');
  assert.equal(h.page.data.aiRecipeSuggested, false);
});

test('confirmed draft saves recipe and daily intake with explicit metadata', async () => {
  let confirmation;
  const h = pageHarness({}, {
    RecipeAPI: { saveAndAddIntake: async payload => { confirmation = payload; } }
  });
  h.page.data.recipeName = '清蒸鱼套餐';
  h.page.data.steps = '蒸熟后装盘';
  h.page.data.intakeMealSlot = 'LUNCH';
  h.page.data.intakeServingIndex = 2;
  h.page.data.intakeTime = '12:30';
  h.page.data.ingredients = [{
    foodName: '鱼肉（熟，通用）', recognizedName: '清蒸鱼', weight: 150,
    databaseMatched: true, aiMatchStatus: 'USER_SELECTED',
    foodReference: { id: 42, protein: 22, carb: 0, fat: 6, calorie: 145 },
    protein: '33.0', carb: '0.0', fat: '9.0', calorie: '217.5'
  }];

  await h.page.saveAndAddToday();

  assert.equal(confirmation.mealSlot, 'LUNCH');
  assert.equal(confirmation.servingCount, 1.5);
  assert.equal(confirmation.consumedTime, '12:30');
  assert.match(confirmation.date, /^\d{4}-\d{2}-\d{2}$/);
  assert.match(confirmation.requestId, /^intake-/);
  assert.equal(confirmation.recipe.ingredients[0].foodDatabaseId, 42);
  assert.equal(confirmation.recipe.ingredients[0].mappingSource, 'AI_USER_SELECTED');
});

test('uncertain confirmation retry reuses its idempotency request id', async () => {
  const requestIds = [];
  const h = pageHarness({}, {
    RecipeAPI: { saveAndAddIntake: async payload => {
      requestIds.push(payload.requestId);
      throw new Error('timeout');
    } }
  });
  h.page.data.recipeName = '测试菜谱';
  h.page.data.ingredients = [{
    foodName: '米饭', weight: 100, databaseMatched: true,
    foodReference: { id: 2, protein: 2.6, carb: 25.6, fat: 0.3, calorie: 116 },
    protein: '2.6', carb: '25.6', fat: '0.3', calorie: '116.0'
  }];

  await h.page.saveAndAddToday();
  await h.page.saveAndAddToday();

  assert.equal(requestIds.length, 2);
  assert.equal(requestIds[0], requestIds[1]);
});
