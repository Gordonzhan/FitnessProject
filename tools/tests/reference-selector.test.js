const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

test('selector loads categories and recommended first page without a keyword', async () => {
  const apiModule = require('../../utils/api');
  const calls = [];
  apiModule.DatabaseAPI.getFoodCategories = () => Promise.resolve(['肉禽水产', '蔬菜']);
  apiModule.DatabaseAPI.searchFoods = (...args) => {
    calls.push(args);
    return Promise.resolve({ items: [{ id: 1, foodName: '鸡胸肉' }], page: 0, total: 1, hasMore: false });
  };

  let definition;
  global.Component = (value) => { definition = value; };
  const componentPath = path.resolve(__dirname, '../../components/reference-selector/reference-selector.js');
  delete require.cache[componentPath];
  require(componentPath);

  assert.equal(typeof definition.methods.loadCategories, 'function');
  assert.equal(definition.lifetimes.loadCategories, undefined);

  const instance = {
    properties: { type: 'food' },
    data: { ...definition.data },
    setData(update) { Object.assign(this.data, update); },
    ...definition.methods
  };
  definition.lifetimes.attached.call(instance);
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(instance.data.categories, ['肉禽水产', '蔬菜']);
  assert.equal(instance.data.items[0].foodName, '鸡胸肉');
  assert.deepEqual(calls[0], ['', '', 0, 20]);

  definition.lifetimes.detached.call(instance);
  delete global.Component;
});
