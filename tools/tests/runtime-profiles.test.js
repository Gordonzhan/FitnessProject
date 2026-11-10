const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '../..');
const { validateRuntime } = require('../check-runtime');
const local = require(path.join(projectRoot, 'config/runtime.local.js'));
const production = require(path.join(projectRoot, 'config/runtime.production.js'));

test('local runtime points developer tools at the local Java API', () => {
  assert.deepEqual(validateRuntime(local, 'local'), []);
  assert.equal(local.localApiBaseUrl, 'http://127.0.0.1:8080/api');
});

test('production runtime uses cloud transport and contains no local endpoint', () => {
  assert.deepEqual(validateRuntime(production, 'production'), []);
  assert.equal(production.transport, 'cloud');
  assert.doesNotMatch(JSON.stringify(production), /127\.0\.0\.1|localhost/);
});

test('release check rejects local addresses and secret-looking fields', () => {
  const unsafe = {
    ...production,
    localApiBaseUrl: 'http://127.0.0.1:8080/api',
    agentToken: 'must-not-ship'
  };
  const errors = validateRuntime(unsafe, 'production');
  assert.ok(errors.some(error => error.includes('本机地址')));
  assert.ok(errors.some(error => error.includes('敏感字段')));
});
