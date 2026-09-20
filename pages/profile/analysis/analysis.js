const echarts = require('../../../ec-canvas/echarts');
const { AnalysisAPI } = require('../../../utils/api');
const { toLocalDateString, addLocalDays, parseLocalDate, compareLocalDates } = require('../../../utils/date');
const app = getApp();

const COLORS = {
  intake: '#ef7544', burn: '#18a662', balance: '#52605a',
  protein: '#6f7bf7', carb: '#efad43', fat: '#dc6f91'
};

Page({
  data: {
    presets: [7, 30, 90],
    activePreset: 7,
    startDate: '',
    endDate: '',
    today: '',
    loading: true,
    refreshing: false,
    error: '',
    summary: null,
    stateText: '',
    completionText: '暂无训练记录',
    weightDate: '',
    weightValue: '',
    savingWeight: false,
    energyEc: { lazyLoad: true },
    macroEc: { lazyLoad: true }
  },

  onLoad() {
    const today = toLocalDateString();
    this._loadSequence = 0;
    this._unloaded = false;
    this._energyChart = null;
    this._macroChart = null;
    this.setData({ today, endDate: today, startDate: addLocalDays(today, -6), weightDate: today });
    app.ensureLogin()
      .then(() => this.loadAnalysis())
      .catch(error => this.setData({ loading: false, error: '登录失败：' + error }));
  },

  onUnload() {
    this._unloaded = true;
    this._loadSequence++;
    this.disposeCharts();
  },

  selectPreset(e) {
    const days = Number(e.currentTarget.dataset.days);
    const endDate = this.data.today;
    this.setData({ activePreset: days, startDate: addLocalDays(endDate, -(days - 1)), endDate });
    this.loadAnalysis();
  },

  changeStartDate(e) {
    this.setData({ startDate: e.detail.value, activePreset: 0 });
  },

  changeEndDate(e) {
    this.setData({ endDate: e.detail.value, activePreset: 0 });
  },

  applyCustomRange() {
    const { startDate, endDate, today } = this.data;
    const start = parseLocalDate(startDate);
    const end = parseLocalDate(endDate);
    if (!start || !end || compareLocalDates(startDate, endDate) > 0) {
      wx.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' });
      return;
    }
    if (compareLocalDates(endDate, today) > 0) {
      wx.showToast({ title: '结束日期不能晚于今天', icon: 'none' });
      return;
    }
    const days = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
    if (days > 90) {
      wx.showToast({ title: '单次分析最多90天', icon: 'none' });
      return;
    }
    this.loadAnalysis();
  },

  loadAnalysis() {
    const { startDate, endDate } = this.data;
    const sequence = ++this._loadSequence;
    // 首次进入才显示整页加载；切换范围时保留 Canvas 与 ECharts 实例。
    const hasSummary = !!this.data.summary;
    this.setData({ loading: !hasSummary, refreshing: hasSummary, error: '' });
    return AnalysisAPI.getSummary(startDate, endDate)
      .then(summary => {
        if (this._unloaded || sequence !== this._loadSequence) return false;
        this._analysis = summary;
        // 图表点仅保留在页面实例中；视图层只接收摘要，避免 90 天数组反复 setData。
        const summaryView = Object.assign({}, summary, {
          weightPointCount: (summary.weightPoints || []).length
        });
        delete summaryView.dailyPoints;
        delete summaryView.weightPoints;
        const completion = summary.trainingCompletion || {};
        const completionText = completion.rate === null || completion.rate === undefined
          ? '暂无训练记录'
          : completion.rate + '%（完成 ' + completion.completed + ' / 共 '
            + (completion.completed + completion.planned + completion.cancelled) + ' 次）';
        this.setData({
          loading: false,
          refreshing: false,
          summary: summaryView,
          stateText: this.stateText(summary.dataState),
          completionText
        });
        wx.nextTick(() => this.renderCharts());
        return true;
      })
      .catch(error => {
        if (!this._unloaded && sequence === this._loadSequence) {
          this.setData({
            loading: false,
            refreshing: false,
            error: '分析加载失败：' + (error.message || error)
          });
        }
        return false;
      });
  },

  stateText(state) {
    if (state === 'NO_DATA') return '暂无数据';
    if (state === 'COMPLETE') return '记录较完整';
    return '部分记录';
  },

  renderCharts() {
    const summary = this._analysis;
    if (!summary || summary.dataState === 'NO_DATA') return;
    const labels = summary.dailyPoints.map(item => item.date.slice(5));
    const energyOption = this.energyOption(labels, summary.dailyPoints);
    const macroOption = this.macroOption(labels, summary.dailyPoints);
    this.initOrUpdateChart('#energy-chart', '_energyChart', energyOption);
    this.initOrUpdateChart('#macro-chart', '_macroChart', macroOption);
  },

  initOrUpdateChart(selector, field, option) {
    if (this[field]) {
      this[field].setOption(option, { notMerge: true, lazyUpdate: true, silent: true });
      return;
    }
    const component = this.selectComponent(selector);
    if (!component) return;
    component.init((canvas, width, height, dpr) => {
      const chart = echarts.init(canvas, null, { width, height, devicePixelRatio: dpr });
      canvas.setChart(chart);
      chart.setOption(option, { notMerge: true, lazyUpdate: true, silent: true });
      this[field] = chart;
      return chart;
    });
  },

  disposeCharts() {
    if (this._energyChart) this._energyChart.dispose();
    if (this._macroChart) this._macroChart.dispose();
    this._energyChart = null;
    this._macroChart = null;
  },

  energyOption(labels, points) {
    return this.baseOption(labels, [
      this.barSeries('摄入', COLORS.intake, points.map(item => item.hasNutrition ? Number(item.intakeCalories) : null)),
      this.barSeries('实际消耗', COLORS.burn, points.map(item => Number(item.actualExpenditure))),
      this.lineSeries('能量差', COLORS.balance, points.map(item => item.hasNutrition ? Number(item.energyDifference) : null))
    ], 'kcal');
  },

  macroOption(labels, points) {
    return this.baseOption(labels, [
      this.lineSeries('蛋白质', COLORS.protein, points.map(item => item.hasNutrition ? Number(item.protein) : null)),
      this.lineSeries('碳水', COLORS.carb, points.map(item => item.hasNutrition ? Number(item.carb) : null)),
      this.lineSeries('脂肪', COLORS.fat, points.map(item => item.hasNutrition ? Number(item.fat) : null))
    ], 'g');
  },

  baseOption(labels, series, unit) {
    return {
      animation: false,
      color: series.map(item => item.itemStyle ? item.itemStyle.color : item.lineStyle.color),
      tooltip: { trigger: 'axis', confine: true, valueFormatter: value => value + ' ' + unit },
      legend: { top: 0, itemWidth: 12, itemHeight: 7, textStyle: { color: '#667169', fontSize: 10 } },
      grid: { left: 12, right: 14, top: 38, bottom: 18, containLabel: true },
      xAxis: { type: 'category', boundaryGap: true, data: labels, axisLine: { lineStyle: { color: '#dce3dd' } }, axisLabel: { color: '#89938c', fontSize: 9 } },
      yAxis: { type: 'value', name: unit, nameTextStyle: { color: '#9aa39d', fontSize: 9 }, splitLine: { lineStyle: { color: '#eef2ee' } }, axisLabel: { color: '#89938c', fontSize: 9 } },
      series
    };
  },

  barSeries(name, color, data) {
    return { name, type: 'bar', data, barMaxWidth: 12, itemStyle: { color, borderRadius: [3, 3, 0, 0] } };
  },

  lineSeries(name, color, data) {
    return { name, type: 'line', data, symbol: 'none', smooth: true, lineStyle: { color, width: 2 }, itemStyle: { color } };
  },

  changeWeightDate(e) {
    this.setData({ weightDate: e.detail.value });
  },

  inputWeight(e) {
    this.setData({ weightValue: e.detail.value });
  },

  saveWeight() {
    const weight = Number(this.data.weightValue);
    if (!weight || weight < 25 || weight > 350 || this.data.savingWeight) {
      wx.showToast({ title: '请输入25～350kg的体重', icon: 'none' });
      return;
    }
    this.setData({ savingWeight: true });
    AnalysisAPI.recordWeight(this.data.weightDate, weight)
      .then(() => {
        if (this._unloaded) return;
        if (this.data.weightDate === this.data.today) {
          const profile = wx.getStorageSync('userProfile') || {};
          profile.weight = weight;
          wx.setStorageSync('userProfile', profile);
          wx.setStorageSync('userWeight', weight);
          app.globalData.currentUser = profile;
        }
        this.setData({ weightValue: '' });
        wx.showToast({ title: '体重已记录', icon: 'success' });
        if (compareLocalDates(this.data.weightDate, this.data.startDate) >= 0
            && compareLocalDates(this.data.weightDate, this.data.endDate) <= 0) {
          this.loadAnalysis();
        }
      })
      .catch(error => wx.showToast({ title: '记录失败：' + (error.message || error), icon: 'none' }))
      .finally(() => {
        if (!this._unloaded) this.setData({ savingWeight: false });
      });
  }
});
