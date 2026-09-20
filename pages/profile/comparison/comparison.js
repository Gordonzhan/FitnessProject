const app = getApp();
const { PopulationComparisonAPI } = require('../../../utils/api');

Page({
  data: {
    loading: true,
    error: '',
    summary: null,
    metrics: [],
    leaderboardEntries: [],
    periodText: '',
    statusTitle: '',
    statusDetail: ''
  },

  onLoad() {
    this._unloaded = false;
    app.ensureLogin()
      .then(() => this.loadComparison())
      .catch(error => this.setSafe({ loading: false, error: '登录失败：' + this.message(error) }));
  },

  onUnload() { this._unloaded = true; },

  onPullDownRefresh() {
    this.loadComparison().finally(() => wx.stopPullDownRefresh());
  },

  loadComparison() {
    this.setSafe({ loading: true, error: '' });
    return PopulationComparisonAPI.getTrainingComparison()
      .then(summary => this.applySummary(summary))
      .catch(error => this.setSafe({ error: '对比加载失败：' + this.message(error) }))
      .finally(() => this.setSafe({ loading: false }));
  },

  applySummary(summary) {
    const ready = summary.status === 'READY';
    const statusCopy = this.statusCopy(summary.status, summary.minimumSampleSize);
    const metrics = (summary.metrics || []).map(item => ({
      ...item,
      currentText: this.numberText(item.currentValue),
      percentileText: item.percentile == null ? '暂不计算' : `达到或超过 ${item.percentile}%`,
      barWidth: item.percentile == null ? 0 : Math.max(2, Math.min(100, Number(item.percentile)))
    }));
    const leaderboard = summary.leaderboard || null;
    const leaderboardEntries = leaderboard ? (leaderboard.entries || []).map((item, index) => ({
      ...item,
      rowKey: `${item.rank}-${index}`
    })) : [];
    this.setSafe({
      summary,
      metrics,
      leaderboardEntries,
      periodText: `${summary.startDate} 至 ${summary.endDate}`,
      statusTitle: ready ? '你的近30天位置' : statusCopy.title,
      statusDetail: ready ? '百分位越高，仅表示该指标在当前样本中的相对位置。' : statusCopy.detail
    });
  },

  statusCopy(status, minimum) {
    if (status === 'POPULATION_INSUFFICIENT') {
      return { title: '人群样本暂时不足', detail: `有效样本达到 ${minimum || 10} 人后才会计算百分位。` };
    }
    if (status === 'CURRENT_DATA_INSUFFICIENT') {
      return { title: '你的训练记录不足', detail: '近30天记录至少一次训练后，才会显示人群百分位。' };
    }
    return { title: '暂时无法计算', detail: '请稍后重新加载。' };
  },

  numberText(value) {
    const number = Number(value || 0);
    return Number.isInteger(number) ? String(number) : number.toFixed(1);
  },

  retry() { return this.loadComparison(); },

  message(error) {
    return error && error.message ? error.message : String(error || '未知错误');
  },

  setSafe(data) {
    if (!this._unloaded) this.setData(data);
  }
});
