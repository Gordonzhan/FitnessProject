const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const apiPath = path.resolve(__dirname, '../../utils/api.js');
const cloudRuntimePath = path.resolve(__dirname, '../../utils/cloud-runtime.js');
const runtimePath = path.resolve(__dirname, '../../config/runtime.js');
const originalRuntime = require(runtimePath);

function loadApiWithResponse(response) {
  require.cache[runtimePath].exports = {
    transport: 'local',
    localApiBaseUrl: 'http://local.test/api',
    cloud: {}
  };
  global.wx = {
    getStorageSync: () => 'test-token',
    removeStorageSync: () => {},
    request: (options) => options.success(response)
  };
  global.getApp = () => ({ ensureLogin: () => Promise.resolve() });
  delete require.cache[cloudRuntimePath];
  delete require.cache[apiPath];
  return require(apiPath);
}

async function rejectedValue(promise) {
  try {
    await promise;
    assert.fail('请求应当失败');
  } catch (error) {
    return error;
  }
}

test('API displays the server validation message for a 400 response', async () => {
  const { DatabaseAPI } = loadApiWithResponse({
    statusCode: 400,
    data: { code: 400, message: '每页最多50条' },
    header: {}
  });

  assert.equal(await rejectedValue(DatabaseAPI.searchFoods('', '', 0, 51)), '每页最多50条');
});

test('API appends the request id to server errors for troubleshooting', async () => {
  const { DatabaseAPI } = loadApiWithResponse({
    statusCode: 503,
    data: { code: 503, message: '基础数据暂时无法访问，请稍后重试' },
    header: { 'X-Request-Id': 'req_12345678' }
  });

  assert.equal(
    await rejectedValue(DatabaseAPI.searchFoods('', '', 0, 20)),
    '基础数据暂时无法访问，请稍后重试（请求编号：req_12345678）'
  );
});

test.after(() => {
  require.cache[runtimePath].exports = originalRuntime;
  delete global.wx;
  delete global.getApp;
  delete require.cache[cloudRuntimePath];
  delete require.cache[apiPath];
});
