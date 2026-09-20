const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const {
  createUploadTask,
  createUploadBatch,
  uploadFailureMessage,
  uploadFailureCategory,
  MAX_IMAGE_BYTES,
  UPLOAD_TIMEOUT_MS
} = require('../../utils/image-upload');
const flush = () => new Promise(resolve => setImmediate(resolve));
afterEach(() => { delete global.wx; });

test('batch limits concurrency to two, preserves partial successes in selection order', async () => {
  let active = 0;
  let peak = 0;
  const batch = createUploadBatch(Array.from({ length: 9 }, (_, i) => ({ path: String(i), size: 10 })), path => {
    active++;
    peak = Math.max(active, peak);
    return new Promise((resolve, reject) => setImmediate(() => {
      active--;
      path === '3' ? reject(new Error('offline')) : resolve('https://image/' + path);
    }));
  });
  const { results } = await batch.promise;
  assert.equal(peak, 2);
  assert.equal(results.filter(item => item.url).length, 8);
  assert.equal(results[3].error, 'offline');
  assert.equal(results[8].url, 'https://image/8');
});

test('oversize image never starts upload; the rest of the batch still succeeds', async () => {
  let calls = 0;
  const batch = createUploadBatch([{ path: 'large', size: MAX_IMAGE_BYTES + 1 }, { path: 'small', size: 1 }], async () => {
    calls++;
    return 'https://ok';
  });
  const { results } = await batch.promise;
  assert.equal(calls, 1);
  assert.match(results[0].error, /20MB/);
  assert.equal(results[1].url, 'https://ok');
});

test('cancel aborts active uploads and does not start queued files', async () => {
  let calls = 0;
  let aborts = 0;
  const batch = createUploadBatch(Array.from({ length: 9 }, () => ({ path: 'x' })), () => {
    calls++;
    let rejectTask;
    const task = new Promise((resolve, reject) => { rejectTask = reject; });
    task.abort = () => { aborts++; rejectTask(new Error('cancelled')); };
    return task;
  });
  batch.cancel();
  assert.equal((await batch.promise).cancelled, true);
  assert.equal(calls, 2);
  assert.equal(aborts, 2);
});

const options = () => ({ url: 'http://localhost/image/upload', getHeader: () => ({}), ensureAuth: () => Promise.resolve(), refreshAuth: () => Promise.resolve() });

test('upload reads the unified envelope and sets a native timeout', async () => {
  const telemetry = [];
  global.wx = { uploadFile: config => {
    assert.equal(config.timeout, UPLOAD_TIMEOUT_MS);
    assert.match(config.header['X-Request-Id'], /^upl_[a-z0-9]+_[a-z0-9]+$/);
    assert.equal(config.header['X-Upload-Attempt-Id'], config.header['X-Request-Id']);
    config.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: { imageUrl: 'https://image/ok' } }) });
    return { abort() {} };
  } };
  const config = options();
  config.onTelemetry = event => telemetry.push(event);
  assert.equal(await createUploadTask({ path: 'local', size: 456, originalSize: 789 }, config), 'https://image/ok');
  assert.equal(telemetry.at(-1).state, 'succeeded');
  assert.equal(telemetry.at(-1).originalBytes, 789);
  assert.equal(telemetry.at(-1).uploadBytes, 456);
});

test('business errors and invalid JSON are surfaced, not silently accepted', async () => {
  global.wx = { uploadFile: config => {
    config.success({ statusCode: 200, data: '{"code":502,"message":"OSS欠费"}' });
    return { abort() {} };
  } };
  await assert.rejects(createUploadTask('local', options()), /OSS欠费/);
  global.wx.uploadFile = config => {
    config.success({ statusCode: 500, data: '<html>error</html>' });
    return { abort() {} };
  };
  await assert.rejects(createUploadTask('local', options()), /解析失败/);
});

test('only one auth retry is allowed', async () => {
  let attempts = 0;
  let refreshes = 0;
  global.wx = { uploadFile: config => {
    attempts++;
    config.success({ statusCode: 401, data: '{"code":401}' });
    return { abort() {} };
  } };
  const config = options();
  config.refreshAuth = async () => { refreshes++; };
  await assert.rejects(createUploadTask('local', config), /登录已失效/);
  assert.equal(attempts, 2);
  assert.equal(refreshes, 1);
});

test('network timeout is not retried', async () => {
  let attempts = 0;
  global.wx = { uploadFile: config => {
    attempts++;
    config.fail({ errMsg: 'uploadFile:fail timeout' });
    return { abort() {} };
  } };
  await assert.rejects(createUploadTask('local', options()), /超时/);
  assert.equal(attempts, 1);
});

test('native upload failures distinguish legal-domain rejection from timeout', () => {
  assert.match(uploadFailureMessage({ errMsg: 'uploadFile:fail url not in domain list' }), /合法域名/);
  assert.match(uploadFailureMessage({ errMsg: 'uploadFile:fail timeout' }), /超时/);
  assert.match(uploadFailureMessage({ errMsg: 'uploadFile:fail abort' }), /取消/);
  assert.equal(uploadFailureCategory({ errMsg: 'uploadFile:fail url not in domain list' }), 'legal_domain_blocked');
  assert.equal(uploadFailureCategory({ errMsg: 'uploadFile:fail timeout' }), 'timeout');
  assert.equal(uploadFailureCategory({ errMsg: 'uploadFile:fail socket closed' }), 'native_network_error');
});

test('watchdog aborts a native task even when no native callback arrives', async () => {
  const oldSetTimeout = global.setTimeout;
  const oldClearTimeout = global.clearTimeout;
  let expire;
  let aborts = 0;
  global.setTimeout = callback => { expire = callback; return 1; };
  global.clearTimeout = () => {};
  global.wx = { uploadFile: () => ({ abort() { aborts++; } }) };
  try {
    const task = createUploadTask('local', options());
    const rejection = assert.rejects(task, /超时/);
    await flush();
    expire();
    await rejection;
    assert.equal(aborts, 1);
  } finally {
    global.setTimeout = oldSetTimeout;
    global.clearTimeout = oldClearTimeout;
  }
});

test('cancel during login never starts a late upload', async () => {
  let completeLogin;
  let attempts = 0;
  global.wx = { uploadFile: () => { attempts++; } };
  const config = options();
  config.ensureAuth = () => new Promise(resolve => { completeLogin = resolve; });
  const task = createUploadTask('local', config);
  await flush();
  const rejection = assert.rejects(task, /取消/);
  task.abort();
  completeLogin();
  await rejection;
  await flush();
  assert.equal(attempts, 0);
});
