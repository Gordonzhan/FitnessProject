const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relative) {
  return fs.readFileSync(path.join(__dirname, '../..', relative), 'utf8');
}

test('A4 exposes explicit training preferences without auto-saving a workout', () => {
  const source = read('pages/profile/training-preferences/training-preferences.js');
  assert.match(source, /TrainingPreferenceAPI\.save\(payload\)/);
  assert.doesNotMatch(source, /WorkoutAPI\.save/);
  assert.match(source, /targetRpeMin > targetRpeMax/);
  assert.match(source, /preferredExerciseCodes/);
  assert.match(source, /excludedExerciseCodes/);
});

test('exercise selector keeps server pagination and applies equipment and limitation filters', () => {
  const source = read('components/reference-selector/reference-selector.js');
  const template = read('components/reference-selector/reference-selector.wxml');
  assert.match(source, /PAGE_SIZE = 20/);
  assert.match(source, /equipment: this\.properties\.equipment/);
  assert.match(source, /excludedLimitations: this\.properties\.excludedLimitations/);
  assert.match(source, /exercise-detail\?exerciseCode=/);
  assert.match(source, /selectedCodes/);
  assert.match(source, /selectionSelected/);
  assert.match(template, /selector-done/);
  assert.match(template, /已选 \{\{selectedCodes\.length\}\} 个动作/);
});

test('training preference exercise picker toggles multiple choices without closing', () => {
  let definition;
  global.getApp = () => ({ ensureLogin: () => Promise.resolve() });
  global.Page = value => { definition = value; };
  global.wx = { showToast() {} };
  const modulePath = path.resolve(__dirname, '../../pages/profile/training-preferences/training-preferences.js');
  delete require.cache[modulePath];
  require(modulePath);

  const instance = {
    data: {
      preferredExercises: [], excludedExercises: [], selectionMode: 'preferred',
      selectorVisible: true, selectorSelectedCodes: [], selectorBlockedCodes: []
    },
    setData(update) { Object.assign(this.data, update); }
  };
  const event = { detail: { item: { exerciseCode: 'EX_SMITH', name: '史密斯深蹲' } } };
  definition.selectExercise.call(instance, event);
  assert.equal(instance.data.selectorVisible, true);
  assert.deepEqual(instance.data.selectorSelectedCodes, ['EX_SMITH']);
  assert.equal(instance.data.preferredExercises.length, 1);

  definition.selectExercise.call(instance, event);
  assert.equal(instance.data.selectorVisible, true);
  assert.deepEqual(instance.data.selectorSelectedCodes, []);
  assert.equal(instance.data.preferredExercises.length, 0);

  delete global.getApp;
  delete global.Page;
  delete global.wx;
});

test('professional equipment options include common machine categories and visible purposes', () => {
  const source = read('pages/profile/training-preferences/training-preferences.js');
  const template = read('pages/profile/training-preferences/training-preferences.wxml');
  assert.match(source, /史密斯机/);
  assert.match(source, /辅助引体双杠机/);
  assert.match(source, /髋外展\/内收机/);
  assert.match(source, /哈克深蹲机/);
  assert.match(template, /equipment-options/);
  assert.match(template, /item\.purpose/);
});

test('training preference page keeps long selections in compact summaries and editors', () => {
  const source = read('pages/profile/training-preferences/training-preferences.js');
  const template = read('pages/profile/training-preferences/training-preferences.wxml');
  assert.match(source, /SUMMARY_LIMIT = 3/);
  assert.match(source, /equipmentPreview/);
  assert.match(source, /preferredExercisePreview/);
  assert.match(source, /excludedExercisePreview/);
  assert.match(template, /class="equipment-sheet"/);
  assert.match(template, /equipmentOverflowCount/);
  assert.match(template, /preferredExerciseOverflowCount/);
  assert.match(template, /excludedExerciseOverflowCount/);
});

test('exercise detail shows guidance, safety, defaults and alternatives', () => {
  const template = read('pages/workout/exercise-detail/exercise-detail.wxml');
  assert.match(template, /动作说明/);
  assert.match(template, /安全提示/);
  assert.match(template, /常见错误/);
  assert.match(template, /替代动作/);
  assert.match(template, /仅供健身参考/);
});
