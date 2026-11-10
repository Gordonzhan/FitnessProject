const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('A7 page restores a persisted session and sends messages through Java', () => {
  const page = read('pages/profile/fitness-agent/fitness-agent.js');
  const api = read('utils/api.js');
  assert.match(page, /openAgentSession\(\)/);
  assert.match(page, /sendAgentMessage\(this\.data\.sessionId, content\)/);
  assert.match(api, /\/ai\/agent\/sessions/);
});

test('A7 workout action reuses the existing editable A5 confirmation page', () => {
  const page = read('pages/profile/fitness-agent/fitness-agent.js');
  const workout = read('pages/workout/ai-draft/ai-draft.js');
  assert.match(page, /agentWorkoutDraftTransfer/);
  assert.match(page, /pages\/workout\/ai-draft\/ai-draft\?fromAgent=1/);
  assert.match(workout, /confirmWorkoutDraft/);
  assert.match(workout, /wx\.showModal\(\{ title: '确认训练计划'/);
  assert.match(page, /message\.actionPayload\.date \|\| toLocalDateString\(\)/);
});

test('A7 restores failed input without leaving an optimistic duplicate message', () => {
  const page = read('pages/profile/fitness-agent/fitness-agent.js');
  assert.match(page, /inputText: content, messages: previousMessages/);
  assert.match(page, /内容已保留，请稍后重试/);
  assert.doesNotMatch(page, /消息已保存，可刷新后重试/);
});

test('A7 UI has no direct data mutation action', () => {
  const page = read('pages/profile/fitness-agent/fitness-agent.js');
  assert.doesNotMatch(page, /CompareAPI|WorkoutAPI|UserAPI\.update|recordWeight/);
});

test('A7 can explicitly clear only the current persisted conversation', () => {
  const page = read('pages/profile/fitness-agent/fitness-agent.js');
  const view = read('pages/profile/fitness-agent/fitness-agent.wxml');
  const api = read('utils/api.js');
  assert.match(view, /清空对话/);
  assert.match(page, /wx\.showModal/);
  assert.match(page, /clearAgentMessages\(this\.data\.sessionId\)/);
  assert.match(api, /\/ai\/agent\/sessions\/.*\/messages', 'DELETE'/s);
});

test('A8 citations are restored and shown without inventing missing links', () => {
  const page = read('pages/profile/fitness-agent/fitness-agent.js');
  const view = read('pages/profile/fitness-agent/fitness-agent.wxml');
  assert.match(view, /item\.citations\.length/);
  assert.match(view, /citation\.sourceName/);
  assert.match(view, /citation\.version/);
  assert.match(view, /wx:if="\{\{citation\.sourceUrl\}\}"/);
  assert.match(page, /normalizeMessages\(session\.messages\)/);
  assert.match(page, /seen\.has\(key\)/);
  assert.match(page, /\^https:\\\/\\\//);
  assert.doesNotMatch(page, /CompareAPI|WorkoutAPI|UserAPI\.update|recordWeight/);
});
