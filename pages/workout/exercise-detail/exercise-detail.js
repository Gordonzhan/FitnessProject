const { DatabaseAPI } = require('../../../utils/api');

const LIMITATION_LABELS = {
  KNEE: '膝部', LOWER_BACK: '下背部', SHOULDER: '肩部', WRIST: '手腕',
  ANKLE: '脚踝', CARDIOVASCULAR: '心肺风险', BALANCE: '平衡能力', PREGNANCY: '孕期'
};

Page({
  data: { loading: true, error: '', exercise: null, limitationLabels: [], alternatives: [] },
  onLoad(options) {
    this.exerciseCode = decodeURIComponent(options.exerciseCode || '');
    this.load();
  },
  load() {
    if (!this.exerciseCode) return this.setData({ loading: false, error: '缺少动作编号' });
    this.setData({ loading: true, error: '' });
    DatabaseAPI.getExerciseByCode(this.exerciseCode).then(exercise => {
      const limitations = String(exercise.limitationTags || '').split(',').filter(Boolean);
      const alternativeCodes = String(exercise.alternativeExerciseCodes || '').split(',').filter(Boolean);
      this.setData({
        exercise,
        limitationLabels: limitations.map(code => LIMITATION_LABELS[code] || code)
      });
      if (alternativeCodes.length) {
        DatabaseAPI.resolveExerciseCodes(alternativeCodes)
          .then(alternatives => this.setData({ alternatives: alternatives || [] }))
          .catch(() => this.setData({ alternatives: [] }));
      }
    }).catch(error => this.setData({ error: String(error) }))
      .finally(() => this.setData({ loading: false }));
  },
  openAlternative(e) {
    const exerciseCode = e.currentTarget.dataset.code;
    wx.redirectTo({ url: '/pages/workout/exercise-detail/exercise-detail?exerciseCode=' + encodeURIComponent(exerciseCode) });
  }
});
