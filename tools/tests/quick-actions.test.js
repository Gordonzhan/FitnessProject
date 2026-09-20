const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function source(relativePath) {
  return fs.readFileSync(path.join(__dirname, '../..', relativePath), 'utf8');
}

test('tab pages expose safe-area quick actions and hide recipe action for the keyboard', () => {
  const recipe = source('pages/recipe/recipe.wxml');
  const workout = source('pages/workout/workout.wxml');
  const styles = source('app.wxss');

  assert.match(recipe, /class="list-quick-action" bindtap="addRecipe"/);
  assert.match(recipe, /bindfocus="onSearchFocus" bindblur="onSearchBlur"/);
  assert.match(workout, /list-quick-action[^"]*" bindtap="addWorkout"/);
  assert.match(styles, /\.list-quick-action[\s\S]*env\(safe-area-inset-bottom\)/);
});

test('long editors keep add and save actions together and show item counts', () => {
  const recipe = source('pages/recipe/add-recipe/add-recipe.wxml');
  const workout = source('pages/workout/add-workout/add-workout.wxml');

  assert.match(recipe, /form-sticky-action/);
  assert.match(recipe, /bindtap="quickAddIngredient"/);
  assert.match(recipe, /当前 \{\{ingredients\.length\}\} 项/);
  assert.match(recipe, /!keyboardOpen && !selectorVisible/);
  assert.match(workout, /form-sticky-action/);
  assert.match(workout, /bindtap="quickAddExercise"/);
  assert.match(workout, /当前 \{\{exercises\.length\}\} 项/);
  assert.match(workout, /!keyboardOpen && !selectorVisible/);
});

test('quick editor actions append a row, open the matching selector and prevent busy additions', () => {
  const recipe = source('pages/recipe/add-recipe/add-recipe.js');
  const workout = source('pages/workout/add-workout/add-workout.js');

  assert.match(recipe, /quickAddIngredient\(\)[\s\S]*appendIngredient\(true\)/);
  assert.match(recipe, /if \(this\.imageActionsBlocked\(\)\)/);
  assert.match(recipe, /selectingIngredientIndex = index/);
  assert.match(workout, /quickAddExercise\(\)[\s\S]*appendExercise\(true\)/);
  assert.match(workout, /this\.data\.saving \|\| this\.data\.loadingWorkout \|\| this\.data\.loadFailed/);
  assert.match(workout, /selectingExerciseIndex = index/);
});
