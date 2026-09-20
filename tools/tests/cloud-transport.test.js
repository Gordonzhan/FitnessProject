const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const apiPath = path.resolve(__dirname, '../../utils/api.js');
const cloudRuntimePath = path.resolve(__dirname, '../../utils/cloud-runtime.js');
const runtimePath = path.resolve(__dirname, '../../config/runtime.js');
const originalRuntime = require(runtimePath);
const flush = () => new Promise(resolve => setImmediate(resolve));

function loadCloudApi(callContainer) {
  require.cache[runtimePath].exports = {
    transport: 'cloud',
    localApiBaseUrl: 'http://local.invalid/api',
    cloud: {
      envId: 'test-env',
      serviceName: 'fitness-api',
      apiPrefix: '/api',
      uploadBaseUrl: 'https://fitness-api.example.run.tcloudbase.com'
    }
  };
  delete require.cache[cloudRuntimePath];
  delete require.cache[apiPath];
  global.wx = {
    getStorageSync: key => key === 'authToken' ? 'test-token' : null,
    removeStorageSync: () => {},
    cloud: { init() {}, callContainer }
  };
  global.getApp = () => ({ ensureLogin: () => Promise.resolve() });
  return require(apiPath);
}

afterEach(() => {
  require.cache[runtimePath].exports = originalRuntime;
  delete require.cache[cloudRuntimePath];
  delete require.cache[apiPath];
  delete global.wx;
  delete global.getApp;
});

test('cloud mode routes API requests through callContainer with service identity', async () => {
  let options;
  const { DatabaseAPI } = loadCloudApi(config => {
    options = config;
    config.success({ statusCode: 200, data: { code: 200, data: { content: [] } }, header: {} });
  });

  await DatabaseAPI.searchFoods('鸡胸', '肉类', 0, 20);

  assert.equal(options.config.env, 'test-env');
  assert.equal(options.path, '/api/database/food/search');
  assert.equal(options.method, 'GET');
  assert.equal(options.header['X-WX-SERVICE'], 'fitness-api');
  assert.equal(options.header.Authorization, 'Bearer test-token');
  assert.deepEqual(options.data, { keyword: '鸡胸', category: '肉类', page: 0, size: 20 });
});

test('cloud gateway request id is preserved in server error messages', async () => {
  const { DatabaseAPI } = loadCloudApi(config => config.success({
    statusCode: 503,
    data: { code: 503, message: '数据库暂不可用' },
    header: { 'X-Cloudbase-Request-Id': 'cloud-request-1' }
  }));

  await assert.rejects(
    DatabaseAPI.searchFoods('', '', 0, 20),
    error => error === '数据库暂不可用（请求编号：cloud-request-1）'
  );
});

test('upload ticket and confirmation stay on the private callContainer transport', async () => {
  const calls = [];
  const { ImageAPI } = loadCloudApi(config => {
    calls.push(config);
    config.success({ statusCode: 200, data: { code: 200, data: {} }, header: {} });
  });

  await ImageAPI.issueUploadTicket(1024, 'jpeg', 'upl_attempt_123');
  await ImageAPI.confirmUpload('977b707b-4444-41e3-aef9-9c58d0fa7b28', 'upl_attempt_123');

  assert.deepEqual(calls.map(call => call.path), [
    '/api/image/upload-ticket',
    '/api/image/upload-confirm'
  ]);
  assert.ok(calls.every(call => call.header['X-WX-SERVICE'] === 'fitness-api'));
});

test('cloud image uploads use the configured HTTPS service domain', async () => {
  const { ImageAPI } = loadCloudApi(() => {});
  let uploadOptions;
  global.wx.uploadFile = options => {
    uploadOptions = options;
    return { abort() {} };
  };

  const task = ImageAPI.upload('/tmp/image.jpg');
  await flush();
  assert.equal(uploadOptions.url, 'https://fitness-api.example.run.tcloudbase.com/api/image/upload');
  const rejection = assert.rejects(task, /取消/);
  task.abort();
  await rejection;
});
