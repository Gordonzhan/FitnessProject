// 云托管最小实例为 0 时，首次图片请求还包含实例冷启动；给 multipart + OSS 留出余量。
const UPLOAD_TIMEOUT_MS = 75000;
const MAX_IMAGE_BYTES = 20 * 1024 * 1024;

/**
 * 创建单张图片上传任务并返回可取消的 Promise。
 * 兜底计时器覆盖登录和上传阶段，防止微信回调缺失时界面一直等待。
 */
function createUploadTask(fileInput, options) {
  const filePath = typeof fileInput === 'string' ? fileInput : fileInput.path;
  const fileSize = typeof fileInput === 'object' && Number.isFinite(fileInput.size) ? fileInput.size : null;
  const originalSize = typeof fileInput === 'object' && Number.isFinite(fileInput.originalSize)
    ? fileInput.originalSize : fileSize;
  const uploadAttemptId = createUploadAttemptId();
  const startedAt = Date.now();
  let task;
  let finished = false;
  let timer;
  let settle;
  const promise = new Promise((resolve, reject) => {
    settle = (error, value) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      error ? reject(error) : resolve(value);
    };
    timer = setTimeout(() => {
      recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'failed', originalSize, fileSize, startedAt, 'timeout');
      settle(new Error('图片上传超时，请检查网络后重试'));
      if (task) task.abort();
    }, UPLOAD_TIMEOUT_MS);

    // 登录失效时最多刷新一次 Token；网络错误不自动重传，避免生成重复 OSS 对象。
    const start = (canRetryAuth) => {
      if (finished) return;
      recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'started', originalSize, fileSize, startedAt);
      task = wx.uploadFile({
        url: options.url,
        filePath,
        name: 'file',
        timeout: UPLOAD_TIMEOUT_MS,
        header: Object.assign({}, options.getHeader(), {
          'X-Request-Id': uploadAttemptId,
          'X-Upload-Attempt-Id': uploadAttemptId
        }),
        success: (res) => {
          if (finished) return;
          let body;
          try { body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data; }
          catch (error) {
            recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'failed', originalSize, fileSize,
              startedAt, 'invalid_response');
            settle(new Error('上传响应解析失败'));
            return;
          }
          if (res.statusCode === 401 || (body && body.code === 401)) {
            if (!canRetryAuth) {
              recordUploadEvent(options, uploadAttemptId, 'authorization', 'failed', originalSize, fileSize,
                startedAt, 'authorization_failed');
              settle(new Error('登录已失效，请重新登录'));
              return;
            }
            // 只有明确未授权才重试，网络超时不自动重传，避免生成重复对象。
            Promise.resolve().then(options.refreshAuth).then(() => start(false)).catch((error) => {
              recordUploadEvent(options, uploadAttemptId, 'authorization', 'failed', originalSize, fileSize,
                startedAt, 'authorization_refresh_failed');
              settle(error);
            });
            return;
          }
          if (res.statusCode !== 200 || !body || body.code !== 200) {
            recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'failed', originalSize, fileSize,
              startedAt, 'server_response');
            settle(new Error((body && body.message) || '上传失败：' + res.statusCode));
            return;
          }
          const url = body.data && body.data.imageUrl;
          if (typeof url !== 'string' || !/^https:\/\//.test(url)) {
            recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'failed', originalSize, fileSize,
              startedAt, 'invalid_response');
            settle(new Error('服务端未返回有效图片地址'));
            return;
          }
          recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'succeeded', originalSize, fileSize, startedAt);
          settle(null, url);
        },
        fail: (error) => {
          if (finished) return;
          recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'failed', originalSize, fileSize, startedAt,
            uploadFailureCategory(error));
          settle(new Error(uploadFailureMessage(error)));
        }
      });
    };
    Promise.resolve().then(options.ensureAuth).then(() => start(true)).catch((error) => {
      recordUploadEvent(options, uploadAttemptId, 'authorization', 'failed', originalSize, fileSize,
        startedAt, 'authorization_failed');
      settle(error);
    });
  });
  promise.abort = () => {
    if (!finished) recordUploadEvent(options, uploadAttemptId, 'legacy_upload', 'cancelled', originalSize,
      fileSize, startedAt, 'user_cancelled');
    settle(new Error('上传已取消'));
    if (task) task.abort();
  };
  return promise;
}

/** 将微信原生上传错误转换为可以直接指导排查的消息，不暴露本地临时路径。 */
function uploadFailureMessage(error) {
  const message = String((error && error.errMsg) || '');
  if (/url not in domain list|domain list/i.test(message)) {
    return '云托管上传域名未加入 uploadFile 合法域名';
  }
  if (/timeout/i.test(message)) return '图片上传超时，请检查网络后重试';
  if (/abort|cancel/i.test(message)) return '图片上传已取消';
  return '图片上传失败，请检查网络后重试';
}

/** 将微信原生错误归入稳定类别，日志不记录临时路径、URL 或响应正文。 */
function uploadFailureCategory(error) {
  const message = String((error && error.errMsg) || '');
  if (/url not in domain list|domain list/i.test(message)) return 'legal_domain_blocked';
  if (/timeout/i.test(message)) return 'timeout';
  if (/abort|cancel/i.test(message)) return 'user_cancelled';
  return 'native_network_error';
}

function createUploadAttemptId() {
  return 'upl_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 14);
}

function recordUploadEvent(options, attemptId, phase, state, originalBytes, uploadBytes, startedAt, errorCategory) {
  const event = {
    uploadAttemptId: attemptId,
    phase,
    state,
    originalBytes,
    uploadBytes,
    elapsedMs: Math.max(0, Date.now() - startedAt),
    errorCategory: errorCategory || null
  };
  if (typeof options.onTelemetry === 'function') options.onTelemetry(event);
  if (typeof console !== 'undefined' && typeof console.info === 'function') {
    console.info('IMAGE_UPLOAD_OBSERVABILITY', event);
  }
}

/**
 * 创建批量上传控制器，固定最多两张并行。
 * 每张图片独立结算，单张失败不会丢掉同批已成功的图片。
 */
function createUploadBatch(files, upload, onSettled) {
  let cursor = 0;
  let completed = 0;
  let cancelled = false;
  const active = new Set();
  const results = new Array(files.length);
  /** 从共享游标领取上传任务，直到队列结束或批次被取消。 */
  async function worker() {
    while (!cancelled && cursor < files.length) {
      const index = cursor++;
      const file = files[index];
      let task;
      try {
        if (file.size > MAX_IMAGE_BYTES) throw new Error('单张图片不能超过20MB');
        task = upload(file.path, file);
        active.add(task);
        results[index] = { url: await task };
      } catch (error) {
        results[index] = { error: error.message || String(error) };
      } finally {
        active.delete(task);
        completed++;
        if (!cancelled && onSettled) onSettled(results[index], completed, files.length);
      }
    }
  }
  const promise = Promise.all(Array.from({ length: Math.min(2, files.length) }, worker))
    .then(() => ({ results, cancelled }));
  return {
    promise,
    /** 停止领取新任务，并取消当前正在上传的任务。 */
    cancel() {
      cancelled = true;
      active.forEach(task => { if (task.abort) task.abort(); });
    }
  };
}

module.exports = {
  createUploadTask,
  createUploadBatch,
  uploadFailureMessage,
  uploadFailureCategory,
  createUploadAttemptId,
  UPLOAD_TIMEOUT_MS,
  MAX_IMAGE_BYTES
};
