const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

function harness(searchCatalog) {
  let page;
  const navigations = [];
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/recipe/recipe.js'), 'utf8'), {
    require: () => ({
      RecipeAPI: { searchCatalog, deleteByRecipeId: async () => {} },
      CompareAPI: { addRecipe: async () => {}, removeRecipe: async () => {} }
    }),
    getApp: () => ({ ensureLogin: async () => {} }),
    Page: value => { page = value; },
    wx: { showToast() {}, showModal() {}, navigateTo(value) { navigations.push(value.url); } },
    setTimeout, clearTimeout, Date, Math
  });
  page.setData = update => Object.assign(page.data, update);
  page.onLoad();
  return { page, navigations };
}

const flush = () => new Promise(resolve => setImmediate(resolve));

test('recipe page keeps content-first controls and removes the oversized hero and floating add button', () => {
  const wxml = fs.readFileSync(path.join(__dirname, '../../pages/recipe/recipe.wxml'), 'utf8');
  assert.match(wxml, /class="recipe-toolbar"/);
  assert.match(wxml, /class="source-filter-bar"/);
  assert.match(wxml, /class="filter-sheet"/);
  assert.doesNotMatch(wxml, /class="hero recipe-hero"/);
  assert.doesNotMatch(wxml, /class="floating-button"/);
});

test('catalog loads a paged response and opens system recipes as copies', async () => {
  const calls = [];
  const h = harness(async (...args) => {
    calls.push(args);
    return { items: [{ recipeId: 'sys_meal', sourceType: 'SYSTEM' }], total: 12, page: 0, hasMore: false };
  });
  h.page.loadRecipes(false);
  await flush();
  assert.deepEqual(calls[0], ['', '', '', '', 0, 20]);
  assert.equal(h.page.data.total, 12);
  h.page.viewRecipe({ currentTarget: { dataset: { recipeid: 'sys_meal', sourcetype: 'SYSTEM' } } });
  assert.equal(h.navigations[0], 'add-recipe/add-recipe?templateId=sys_meal');
});

test('fixed recipe shortcut ignores rapid duplicate navigation', () => {
  const h = harness(async () => ({ items: [], total: 0, page: 0, hasMore: false }));
  h.page.addRecipe();
  h.page.addRecipe();
  assert.equal(h.navigations.length, 1);
});

test('catalog appends the next page instead of rendering the full data set at once', async () => {
  const h = harness(async (_keyword, _goal, _cuisine, _source, page) => ({
    items: [{ recipeId: 'page-' + page, sourceType: 'SYSTEM' }], total: 40, page, hasMore: page < 1
  }));
  h.page.loadRecipes(false);
  await flush();
  h.page.onReachBottom();
  await flush();
  assert.equal(h.page.data.recipes.length, 2);
  assert.equal(h.page.data.recipes[1].recipeId, 'page-1');
});

test('goal chips reload the server-side catalog with the selected classification', async () => {
  const calls = [];
  const h = harness(async (...args) => {
    calls.push(args);
    return { items: [], total: 0, page: 0, hasMore: false };
  });
  h.page.selectGoal({ currentTarget: { dataset: { goal: '减脂友好' } } });
  await flush();
  assert.equal(h.page.data.goal, '减脂友好');
  assert.deepEqual(calls[0], ['', '减脂友好', '', '', 0, 20]);
});

test('cuisine chips reload the catalog with the Chinese home-style filter', async () => {
  const calls = [];
  const h = harness(async (...args) => {
    calls.push(args);
    return { items: [], total: 0, page: 0, hasMore: false };
  });
  h.page.selectCuisine({ currentTarget: { dataset: { cuisine: '中式家常' } } });
  await flush();
  assert.equal(h.page.data.cuisine, '中式家常');
  assert.deepEqual(calls[0], ['', '', '中式家常', '', 0, 20]);
});

test('source chips filter the full server catalog and clear system-only filters for user recipes', async () => {
  const calls = [];
  const h = harness(async (...args) => {
    calls.push(args);
    return { items: [], total: 0, page: 0, hasMore: false };
  });
  h.page.data.goal = '增肌友好';
  h.page.data.cuisine = '中式家常';
  h.page.selectSource({ currentTarget: { dataset: { source: 'USER' } } });
  await flush();
  assert.equal(h.page.data.source, 'USER');
  assert.equal(h.page.data.goal, '');
  assert.equal(h.page.data.cuisine, '');
  assert.deepEqual(calls[0], ['', '', '', 'USER', 0, 20]);
});

test('advanced filters stay in a draft until applied and then select system recipes', async () => {
  const calls = [];
  const h = harness(async (...args) => {
    calls.push(args);
    return { items: [], total: 0, page: 0, hasMore: false };
  });
  h.page.openFilters();
  h.page.selectDraftCuisine({ currentTarget: { dataset: { cuisine: '中式家常' } } });
  h.page.selectDraftGoal({ currentTarget: { dataset: { goal: '减脂友好' } } });
  assert.equal(calls.length, 0);
  h.page.applyFilters();
  await flush();
  assert.equal(h.page.data.filterOpen, false);
  assert.equal(h.page.data.source, 'SYSTEM');
  assert.equal(h.page.data.advancedFilterCount, 2);
  assert.deepEqual(calls[0], ['', '减脂友好', '中式家常', 'SYSTEM', 0, 20]);
});

test('switching back to all recipes removes advanced filters', async () => {
  const calls = [];
  const h = harness(async (...args) => {
    calls.push(args);
    return { items: [], total: 0, page: 0, hasMore: false };
  });
  Object.assign(h.page.data, {
    source: 'SYSTEM', goal: '增肌友好', cuisine: '中式家常', advancedFilterCount: 2
  });
  h.page.selectSource({ currentTarget: { dataset: { source: '' } } });
  await flush();
  assert.equal(h.page.data.goal, '');
  assert.equal(h.page.data.cuisine, '');
  assert.equal(h.page.data.advancedFilterCount, 0);
  assert.deepEqual(calls[0], ['', '', '', '', 0, 20]);
});
