const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function harness(login) {
  let application;
  let wxLoginCount = 0;
  const storage = {};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../app.js'), 'utf8'), {
    require: name => name === './utils/api'
      ? { AuthAPI: { login } }
      : { initializeCloud: () => {} },
    App: definition => { application = definition; },
    wx: {
      login(config) { wxLoginCount++; config.success({ code: 'code-' + wxLoginCount }); },
      getStorageSync(key) { return storage[key]; },
      setStorageSync(key, value) { storage[key] = value; },
      removeStorageSync(key) { delete storage[key]; }
    },
    console, Promise, Number, String
  });
  return { application, storage, wxLoginCount: () => wxLoginCount };
}

test('expired WeChat login code is refreshed exactly once', async () => {
  const serverCodes = [];
  const h = harness(async code => {
    serverCodes.push(code);
    if (serverCodes.length === 1) throw '微信登录临时凭证已失效，请重新进入小程序';
    return { token: 'token', user: { id: 1, weight: 70 } };
  });
  const user = await h.application.ensureLogin();
  assert.equal(user.id, 1);
  assert.deepEqual(serverCodes, ['code-1', 'code-2']);
  assert.equal(h.wxLoginCount(), 2);
  assert.equal(h.storage.authToken, 'token');
});

test('AppSecret configuration errors are not retried', async () => {
  const h = harness(async () => { throw '微信小程序 AppID 或 AppSecret 配置不正确'; });
  await assert.rejects(h.application.ensureLogin());
  assert.equal(h.wxLoginCount(), 1);
});

test('concurrent page requests share one login operation', async () => {
  let finishLogin;
  let serverLoginCount = 0;
  const h = harness(() => {
    serverLoginCount++;
    return new Promise(resolve => { finishLogin = resolve; });
  });

  const first = h.application.ensureLogin();
  const second = h.application.ensureLogin();
  assert.equal(h.wxLoginCount(), 1);
  await Promise.resolve();
  assert.equal(serverLoginCount, 1);

  finishLogin({ token: 'shared-token', user: { id: 2, weight: 68 } });
  const [firstUser, secondUser] = await Promise.all([first, second]);

  assert.equal(firstUser.id, 2);
  assert.equal(secondUser.id, 2);
  assert.equal(h.storage.authToken, 'shared-token');
});
