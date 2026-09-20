const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

function pageHarness() {
  let page;
  let deletes = 0;
  let saves = 0;
  let deleteFails = false;
  const context = {
    getApp: () => ({ ensureLogin: () => Promise.resolve() }),
    Page: definition => { page = definition; },
    require: name => name.includes('image-upload') ? require('../../utils/image-upload') : {
      RecipeAPI: { save: async () => { saves++; } },
      ImageAPI: { delete: async () => { deletes++; if (deleteFails) throw new Error('OSS unavailable'); } }
    },
    wx: { showToast() {}, navigateBack() {} },
    console,
    setTimeout: () => 1
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../pages/recipe/add-recipe/add-recipe.js'), 'utf8'), context);
  page.setData = update => Object.assign(page.data, update);
  page.onLoad({});
  return { page, counts: () => ({ deletes, saves }), failDelete: () => { deleteFails = true; } };
}

test('removing a saved image without saving never calls remote delete, including on unload', async () => {
  const h = pageHarness();
  h.page.data.images = ['https://old'];
  h.page.data.imageErrors = [false];
  await h.page.removeImage({ currentTarget: { dataset: { index: 0 } } });
  h.page.onUnload();
  assert.equal(h.page.data.images.length, 0);
  assert.equal(h.counts().deletes, 0);
});

test('failed deletion of new upload keeps it visible and unlocks the page', async () => {
  const h = pageHarness();
  h.page.data.images = ['https://new'];
  h.page.data.imageErrors = [false];
  h.page._pendingUploads.add('https://new');
  h.failDelete();
  await h.page.removeImage({ currentTarget: { dataset: { index: 0 } } });
  assert.equal(h.page.data.images.length, 1);
  assert.equal(h.page.data.removingImage, false);
  assert.equal(h.counts().deletes, 1);
});

test('save and removal are blocked during image upload', async () => {
  const h = pageHarness();
  h.page.data.uploading = true;
  h.page.data.images = ['https://image'];
  h.page.saveRecipe();
  await h.page.removeImage({ currentTarget: { dataset: { index: 0 } } });
  assert.equal(h.counts().saves, 0);
  assert.equal(h.counts().deletes, 0);
  assert.equal(h.page.data.images.length, 1);
});

test('uncertain save followed by page unload never deletes possibly committed images', () => {
  const h = pageHarness();
  h.page._saveAttempted = true;
  h.page._pendingUploads.add('https://new');
  h.page.onUnload();
  assert.equal(h.counts().deletes, 0);
});
