const { TrainingPreferenceAPI, DatabaseAPI } = require('../../../utils/api');
const app = getApp();

const EXPERIENCE_OPTIONS = [
  { value: 'BEGINNER', label: '初级：正在建立动作基础' },
  { value: 'INTERMEDIATE', label: '中级：可独立完成常见训练' },
  { value: 'ADVANCED', label: '高级：有稳定训练和负荷管理经验' }
];
const LOCATION_OPTIONS = [
  { value: 'HOME', label: '居家' }, { value: 'GYM', label: '健身房' },
  { value: 'OUTDOOR', label: '户外' }, { value: 'FLEXIBLE', label: '地点灵活' }
];
const GOAL_OPTIONS = [
  { value: 'GENERAL_FITNESS', label: '综合体能' }, { value: 'FAT_LOSS', label: '减脂' },
  { value: 'MUSCLE_GAIN', label: '增肌' }, { value: 'STRENGTH', label: '力量' },
  { value: 'ENDURANCE', label: '耐力' }, { value: 'MOBILITY', label: '灵活性' }
];
// 使用通用器械名作为业务标签；泰诺健等品牌型号只用于核对器械类型和用途，不绑定用户偏好数据。
const EQUIPMENT_GROUPS = [
  { name: '基础与自由重量', items: [
    { value: '无', purpose: '徒手训练' }, { value: '哑铃', purpose: '单侧或双侧自由重量训练' },
    { value: '壶铃', purpose: '摆动、深蹲和功能性训练' }, { value: '杠铃', purpose: '深蹲、硬拉和推举等复合训练' },
    { value: '史密斯机', purpose: '沿固定轨迹完成深蹲、卧推和肩推' },
    { value: '单杠', purpose: '引体向上和悬垂训练' }, { value: '双杠', purpose: '臂屈伸和支撑训练' },
    { value: '弹力带', purpose: '热身、辅助和渐进阻力训练' }, { value: '药球', purpose: '核心、旋转和爆发力训练' },
    { value: '瑜伽垫', purpose: '地面核心、拉伸和身心训练' }
  ]},
  { name: '绳索与固定力量器械', items: [
    { value: '绳索器械', purpose: '可调滑轮、多角度拉力训练' },
    { value: '多功能训练器', purpose: '在一台设备完成多部位力量训练' },
    { value: '辅助引体双杠机', purpose: '为引体向上和双杠臂屈伸提供配重辅助' },
    { value: '推胸机', purpose: '胸部水平推举' }, { value: '肩推机', purpose: '三角肌和肱三头肌推举' },
    { value: '高位下拉机', purpose: '背阔肌垂直下拉' }, { value: '坐姿划船机', purpose: '背部水平拉力训练' },
    { value: '蝴蝶机', purpose: '胸肌夹胸或反向飞鸟' }, { value: '腿举机', purpose: '股四头肌和臀部推蹬' },
    { value: '哈克深蹲机', purpose: '固定轨迹下肢深蹲' }, { value: '腿屈伸机', purpose: '股四头肌伸膝训练' },
    { value: '腿弯举机', purpose: '腘绳肌屈膝训练' }, { value: '髋外展/内收机', purpose: '大腿内外侧和髋部训练' },
    { value: '提踵机', purpose: '小腿跖屈训练' }, { value: '背伸机', purpose: '下背部伸展训练' },
    { value: '腹肌训练机', purpose: '可控阻力卷腹训练' }, { value: '臀部训练机', purpose: '臀部后踢和伸髋训练' }
  ]},
  { name: '有氧与运动表现', items: [
    { value: '跳绳', purpose: '心肺和协调训练' }, { value: '跑步机', purpose: '步行、跑步和坡度训练' },
    { value: '单车', purpose: '室内骑行和下肢耐力训练' }, { value: '椭圆机', purpose: '低冲击全身有氧训练' },
    { value: '划船机', purpose: '全身拉力与心肺训练' }, { value: '楼梯机', purpose: '登阶、臀腿和心肺训练' },
    { value: '泳池', purpose: '游泳和水中有氧训练' }
  ]}
];
const SUMMARY_LIMIT = 3;

function buildEquipmentGroups(selected) {
  return EQUIPMENT_GROUPS.map(group => ({
    ...group,
    items: group.items.map(item => ({ ...item, selected: selected.includes(item.value) }))
  }));
}

function buildEquipmentSummary(selected) {
  return {
    equipmentPreview: selected.slice(0, SUMMARY_LIMIT),
    equipmentOverflowCount: Math.max(0, selected.length - SUMMARY_LIMIT)
  };
}

function buildExerciseSummary(items, prefix) {
  return {
    [`${prefix}ExercisePreview`]: items.slice(0, SUMMARY_LIMIT),
    [`${prefix}ExerciseOverflowCount`]: Math.max(0, items.length - SUMMARY_LIMIT)
  };
}
const LIMITATION_OPTIONS = [
  { value: 'KNEE', label: '膝部' }, { value: 'LOWER_BACK', label: '下背部' },
  { value: 'SHOULDER', label: '肩部' }, { value: 'WRIST', label: '手腕' },
  { value: 'ANKLE', label: '脚踝' }, { value: 'CARDIOVASCULAR', label: '心肺风险' },
  { value: 'BALANCE', label: '平衡能力' }, { value: 'PREGNANCY', label: '孕期' }
];

Page({
  data: {
    loading: true, saving: false, error: '',
    experienceOptions: EXPERIENCE_OPTIONS, experienceIndex: 0,
    locationOptions: LOCATION_OPTIONS, locationIndex: 3,
    goalOptions: GOAL_OPTIONS, goalIndex: 0,
    weeklyDays: 3, sessionMinutes: 45, targetRpeMin: 5, targetRpeMax: 7,
    equipmentGroups: buildEquipmentGroups(['无']), activeEquipmentItems: buildEquipmentGroups(['无'])[0].items,
    selectedEquipment: ['无'],
    equipmentPreview: ['无'], equipmentOverflowCount: 0,
    equipmentSelectorVisible: false, activeEquipmentGroupIndex: 0,
    limitationOptions: LIMITATION_OPTIONS.map(item => ({ ...item, selected: false })), selectedLimitations: [],
    preferredExercises: [], excludedExercises: [],
    preferredExercisePreview: [], preferredExerciseOverflowCount: 0,
    excludedExercisePreview: [], excludedExerciseOverflowCount: 0,
    selectorVisible: false, selectionMode: 'preferred',
    equipmentFilter: '无', limitationFilter: '', selectorSelectedCodes: [], selectorBlockedCodes: []
  },

  onLoad() {
    app.ensureLogin().then(() => this.load()).catch(error => {
      this.setData({ loading: false, error: String(error) });
    });
  },

  load() {
    this.setData({ loading: true, error: '' });
    TrainingPreferenceAPI.get().then(preferences => this.applyPreferences(preferences || {}))
      .catch(error => this.setData({ error: String(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  applyPreferences(value) {
    const preferredCodes = value.preferredExerciseCodes || [];
    const excludedCodes = value.excludedExerciseCodes || [];
    const codes = Array.from(new Set(preferredCodes.concat(excludedCodes)));
    const apply = (items) => {
      const byCode = new Map((items || []).map(item => [item.exerciseCode, item]));
      const selectedEquipment = value.equipmentTags && value.equipmentTags.length ? value.equipmentTags : ['无'];
      const selectedLimitations = value.limitationTags || [];
      const preferredExercises = preferredCodes.map(code => byCode.get(code) || { exerciseCode: code, name: code });
      const excludedExercises = excludedCodes.map(code => byCode.get(code) || { exerciseCode: code, name: code });
      const equipmentGroups = buildEquipmentGroups(selectedEquipment);
      this.setData({
        experienceIndex: this.optionIndex(EXPERIENCE_OPTIONS, value.experienceLevel, 0),
        locationIndex: this.optionIndex(LOCATION_OPTIONS, value.trainingLocation, 3),
        goalIndex: this.optionIndex(GOAL_OPTIONS, value.trainingGoal, 0),
        weeklyDays: value.weeklyDays || 3,
        sessionMinutes: value.sessionMinutes || 45,
        targetRpeMin: value.targetRpeMin || 5,
        targetRpeMax: value.targetRpeMax || 7,
        selectedEquipment,
        selectedLimitations,
        equipmentGroups,
        activeEquipmentItems: equipmentGroups[this.data.activeEquipmentGroupIndex].items,
        ...buildEquipmentSummary(selectedEquipment),
        limitationOptions: LIMITATION_OPTIONS.map(item => ({ ...item, selected: selectedLimitations.includes(item.value) })),
        preferredExercises,
        excludedExercises,
        ...buildExerciseSummary(preferredExercises, 'preferred'),
        ...buildExerciseSummary(excludedExercises, 'excluded'),
        equipmentFilter: selectedEquipment.join(','),
        limitationFilter: selectedLimitations.join(',')
      });
    };
    if (!codes.length) return apply([]);
    DatabaseAPI.resolveExerciseCodes(codes).then(apply).catch(() => apply([]));
  },

  optionIndex(options, value, fallback) {
    const index = options.findIndex(item => item.value === value);
    return index >= 0 ? index : fallback;
  },

  changeExperience(e) { this.setData({ experienceIndex: Number(e.detail.value) }); },
  changeLocation(e) { this.setData({ locationIndex: Number(e.detail.value) }); },
  changeGoal(e) { this.setData({ goalIndex: Number(e.detail.value) }); },
  inputWeeklyDays(e) { this.setData({ weeklyDays: e.detail.value }); },
  inputSessionMinutes(e) { this.setData({ sessionMinutes: e.detail.value }); },
  inputRpeMin(e) { this.setData({ targetRpeMin: e.detail.value }); },
  inputRpeMax(e) { this.setData({ targetRpeMax: e.detail.value }); },

  openEquipmentSelector() { this.setData({ equipmentSelectorVisible: true }); },
  closeEquipmentSelector() { this.setData({ equipmentSelectorVisible: false }); },
  switchEquipmentGroup(e) {
    const activeEquipmentGroupIndex = Number(e.currentTarget.dataset.index);
    this.setData({
      activeEquipmentGroupIndex,
      activeEquipmentItems: this.data.equipmentGroups[activeEquipmentGroupIndex].items
    });
  },
  noop() {},

  toggleEquipment(e) {
    const value = e.currentTarget.dataset.value;
    let selected = this.data.selectedEquipment.slice();
    if (value !== '无' && !selected.includes(value) && selected.length >= 20) {
      wx.showToast({ title: '最多选择 20 种器械', icon: 'none' });
      return;
    }
    if (value === '无') selected = ['无'];
    else {
      selected = selected.filter(item => item !== '无');
      selected = selected.includes(value) ? selected.filter(item => item !== value) : selected.concat(value);
    }
    if (!selected.length) selected = ['无'];
    const equipmentGroups = buildEquipmentGroups(selected);
    this.setData({
      selectedEquipment: selected,
      equipmentGroups,
      activeEquipmentItems: equipmentGroups[this.data.activeEquipmentGroupIndex].items,
      ...buildEquipmentSummary(selected),
      equipmentFilter: selected.join(',')
    });
  },

  toggleLimitation(e) {
    const value = e.currentTarget.dataset.value;
    let selected = this.data.selectedLimitations.slice();
    selected = selected.includes(value) ? selected.filter(item => item !== value) : selected.concat(value);
    this.setData({
      selectedLimitations: selected,
      limitationOptions: LIMITATION_OPTIONS.map(item => ({ ...item, selected: selected.includes(item.value) })),
      limitationFilter: selected.join(',')
    });
  },

  openExerciseSelector(e) {
    const selectionMode = e.currentTarget.dataset.mode || 'preferred';
    const selected = selectionMode === 'excluded' ? this.data.excludedExercises : this.data.preferredExercises;
    const blocked = selectionMode === 'excluded' ? this.data.preferredExercises : this.data.excludedExercises;
    this.setData({
      selectorVisible: true,
      selectionMode,
      selectorSelectedCodes: selected.map(item => item.exerciseCode),
      selectorBlockedCodes: blocked.map(item => item.exerciseCode)
    });
  },
  closeExerciseSelector() { this.setData({ selectorVisible: false }); },

  selectExercise(e) {
    const item = e.detail.item;
    const preferred = this.data.preferredExercises.slice();
    const excluded = this.data.excludedExercises.slice();
    const target = this.data.selectionMode === 'excluded' ? excluded : preferred;
    const other = this.data.selectionMode === 'excluded' ? preferred : excluded;
    if (other.some(existing => existing.exerciseCode === item.exerciseCode)) {
      wx.showToast({ title: '该动作已在另一列表中', icon: 'none' });
      return;
    }
    const existingIndex = target.findIndex(existing => existing.exerciseCode === item.exerciseCode);
    if (existingIndex >= 0) target.splice(existingIndex, 1);
    else if (target.length >= 100) {
      wx.showToast({ title: '最多选择 100 个动作', icon: 'none' });
      return;
    } else target.push(item);
    this.setData({
      preferredExercises: preferred,
      excludedExercises: excluded,
      ...buildExerciseSummary(preferred, 'preferred'),
      ...buildExerciseSummary(excluded, 'excluded'),
      selectorSelectedCodes: target.map(existing => existing.exerciseCode)
    });
  },

  removeExercise(e) {
    const code = e.currentTarget.dataset.code;
    const mode = e.currentTarget.dataset.mode;
    if (mode === 'excluded') {
      const excludedExercises = this.data.excludedExercises.filter(item => item.exerciseCode !== code);
      this.setData({ excludedExercises, ...buildExerciseSummary(excludedExercises, 'excluded') });
    } else {
      const preferredExercises = this.data.preferredExercises.filter(item => item.exerciseCode !== code);
      this.setData({ preferredExercises, ...buildExerciseSummary(preferredExercises, 'preferred') });
    }
  },

  save() {
    if (this.data.saving) return;
    const weeklyDays = Number(this.data.weeklyDays);
    const sessionMinutes = Number(this.data.sessionMinutes);
    const targetRpeMin = Number(this.data.targetRpeMin);
    const targetRpeMax = Number(this.data.targetRpeMax);
    if (weeklyDays < 1 || weeklyDays > 7 || sessionMinutes < 10 || sessionMinutes > 180) {
      wx.showToast({ title: '请检查训练频率和时长', icon: 'none' }); return;
    }
    if (targetRpeMin < 1 || targetRpeMax > 10 || targetRpeMin > targetRpeMax) {
      wx.showToast({ title: '请检查 RPE 范围', icon: 'none' }); return;
    }
    const payload = {
      experienceLevel: EXPERIENCE_OPTIONS[this.data.experienceIndex].value,
      weeklyDays, sessionMinutes,
      trainingLocation: LOCATION_OPTIONS[this.data.locationIndex].value,
      trainingGoal: GOAL_OPTIONS[this.data.goalIndex].value,
      equipmentTags: this.data.selectedEquipment,
      limitationTags: this.data.selectedLimitations,
      preferredExerciseCodes: this.data.preferredExercises.map(item => item.exerciseCode),
      excludedExerciseCodes: this.data.excludedExercises.map(item => item.exerciseCode),
      targetRpeMin, targetRpeMax
    };
    this.setData({ saving: true });
    TrainingPreferenceAPI.save(payload).then(() => {
      wx.showToast({ title: '训练偏好已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 700);
    }).catch(error => wx.showToast({ title: '保存失败：' + error, icon: 'none' }))
      .finally(() => this.setData({ saving: false }));
  }
});
