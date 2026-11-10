/** 体验版和正式版使用的云托管配置；只允许保存客户端本来就可见的公开标识。 */
module.exports = Object.freeze({
  profile: 'production',
  transport: 'cloud',
  localApiBaseUrl: '',
  cloud: Object.freeze({
    envId: 'prod-d1gwwb5lv3efee196',
    serviceName: 'springboot-rvtc',
    apiPrefix: '/api',
    // 公网域名只用于 multipart 图片上传，末尾不能包含 /api。
    uploadBaseUrl: 'https://springboot-rvtc-312595-6-1486394606.sh.run.tcloudbase.com'
  })
});
