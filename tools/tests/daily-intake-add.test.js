const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

function harness(addRecipe) {
  let page;
  const calls = [];
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/recipe/recipe.js'), 'utf8'), {
    require: () => ({
      RecipeAPI: { getList: async () => [], deleteByRecipeId: async () => {} },
      CompareAPI: {
        addRecipe: params => { calls.push(params); return addRecipe(params); },
        removeRecipe: async () => {}
      }
    }),
    getApp: () => ({ ensureLogin: async () => {} }),
    Page: definition => { page = definition; },
    wx: { showToast() {}, showModal() {}, navigateTo() {} },
    console,
    Date,
    Math
  });
  page.setData = update => Object.assign(page.data, update);
  page.onLoad();
  const event = { currentTarget: { dataset: { recipeid: 'meal-1', mealtype: '鸡胸饭' } } };
  return { page, calls, event };
}

const flush = () => new Promise(resolve => setImmediate(resolve));

test('double tap starts only one add request', async () => {
  let complete;
  const h = harness(() => new Promise(resolve => { complete = resolve; }));
  h.page.selectForToday(h.event);
  h.page.selectForToday(h.event);
  assert.equal(h.calls.length, 1);
  assert.ok(h.calls[0].requestId);
  complete();
  await flush();
  assert.equal(h.page.data.addingRecipeId, '');
});

test('retry after an uncertain failure reuses the idempotency key', async () => {
  let attempt = 0;
  const h = harness(() => ++attempt === 1 ? Promise.reject(new Error('timeout')) : Promise.resolve());
  h.page.selectForToday(h.event);
  await flush();
  h.page.selectForToday(h.event);
  await flush();
  assert.equal(h.calls.length, 2);
  assert.equal(h.calls[0].requestId, h.calls[1].requestId);
});
