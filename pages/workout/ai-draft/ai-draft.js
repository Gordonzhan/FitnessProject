const { AiAPI, CompareAPI } = require('../../../utils/api');
const { toLocalDateString, addLocalDays, compareLocalDates } = require('../../../utils/date');
const app = getApp();

Page({
  data: {
    date: '', loading: false, error: '', draft: null, inputText: '', activeRequest: '',
    messages: [{ role: 'assistant', content: '告诉我今天想练什么、能练多久，也可以补充身体状态。我会结合你保存的偏好生成训练和饮食建议。' }],
    quickPrompts: ['练胸 45 分钟', '练背 40 分钟', '练腿和臀 50 分钟', '全身恢复 30 分钟'],
    replacingCode: '', removingCode: '', confirming: false, nutritionAccepted: false,
    missingWeightCount: 0, invalidSetCount: 0, replaceCancelled: false,
    nutritionMeals: [], selectedNutritionCount: 0, recordingNutrition: false,
    canRecordNutrition: false
  },

  onLoad(options) {
    const transferred = options.fromAgent === '1' ? wx.getStorageSync('agentWorkoutDraftTransfer') : null;
    const date = (transferred && transferred.date) || options.date || toLocalDateString();
    const today = toLocalDateString();
    if (compareLocalDates(date, today) < 0 || compareLocalDates(date, addLocalDays(today, 90)) > 0) {
      this.setData({ error: 'AI只能生成今天至未来90天内的训练计划' });
      return;
    }
    this._nutritionIntakeRequestIds = {};
    this.setData({ date, replaceCancelled: options.replaceCancelled === '1',
      canRecordNutrition: compareLocalDates(date, today) === 0 });
    if (transferred && transferred.draft) {
      wx.removeStorageSync('agentWorkoutDraftTransfer');
      const request = transferred.request || transferred.draft.userRequest || '';
      this.setData({ ...this.draftState(transferred.draft, false), activeRequest: request,
        messages: this.data.messages.concat({ role: 'assistant',
          content: '已从 Fitness Agent 恢复训练草稿。你可以继续修改、换动作或确认保存。' }) });
    }
    app.ensureLogin().catch(error => this.setData({ error: '登录失败：' + error }));
  },

  onInput(e) { this.setData({ inputText: e.detail.value }); },
  useQuickPrompt(e) { this.setData({ inputText: e.currentTarget.dataset.value || '' }); },

  sendMessage() {
    const message = (this.data.inputText || '').trim();
    if (!message || this.data.loading || this.data.confirming || this.data.recordingNutrition
        || this.data.replacingCode || this.data.removingCode) return;
    if (!this.ensureValidSets()) return;
    const combinedRequest = this.data.draft && this.data.activeRequest
      ? this.data.activeRequest + '\n用户补充：' + message : message;
    const activeRequest = combinedRequest.length > 500 ? combinedRequest.slice(-500) : combinedRequest;
    this.setData({ inputText: '', activeRequest,
      messages: this.data.messages.concat({ role: 'user', content: message }) });
    return this.generate(activeRequest, true);
  },

  generate(requestText, appendReply) {
    const message = typeof requestText === 'string' ? requestText : this.data.activeRequest;
    if (!message || this.data.loading || this.data.recordingNutrition) return Promise.resolve();
    if (!this.ensureValidSets()) return Promise.resolve();
    this.setData({ loading: true, error: '', replacingCode: '', removingCode: '' });
    return AiAPI.createWorkoutDraft({ date: this.data.date, message, currentDraft: this.data.draft || null })
      .then(draft => {
        this._nutritionIntakeRequestIds = {};
        const update = { ...this.draftState(draft, false), activeRequest: message, nutritionAccepted: false };
        if (appendReply) update.messages = this.data.messages.concat({ role: 'assistant',
          content: draft.assistantReply || '方案已生成，你可以继续补充要求，或检查后确认。' });
        this.setData(update);
      })
      .catch(error => this.setData({ error: String(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  replaceExercise(e) {
    const exerciseCode = e.currentTarget.dataset.code;
    if (!exerciseCode || !this.data.draft || this.data.loading
        || this.data.replacingCode || this.data.removingCode || this.data.confirming
        || this.data.recordingNutrition) return;
    if (!this.ensureValidSets()) return;
    this.setData({ replacingCode: exerciseCode, error: '' });
    AiAPI.replaceWorkoutExercise({ date: this.data.date, draft: this.data.draft,
      message: this.data.activeRequest, exerciseCode })
      .then(draft => this.setData(this.draftState(draft)))
      .catch(error => wx.showToast({ title: '替换失败：' + error, icon: 'none' }))
      .finally(() => this.setData({ replacingCode: '' }));
  },

  removeExercise(e) {
    const exerciseCode = e.currentTarget.dataset.code;
    if (!exerciseCode || !this.data.draft || this.data.loading || this.data.replacingCode
        || this.data.removingCode || this.data.confirming || this.data.recordingNutrition) return;
    if (!this.ensureValidSets()) return;
    if ((this.data.draft.exercises || []).length <= 2) {
      wx.showToast({ title: '至少保留两个动作，可以改用换一个', icon: 'none' });
      return;
    }
    const exercise = this.data.draft.exercises.find(item => item.exerciseCode === exerciseCode);
    wx.showModal({ title: '删除这个动作？',
      content: '本次训练将不再安排“' + ((exercise && exercise.name) || '该动作') + '”，不会修改长期偏好。',
      confirmText: '删除', confirmColor: '#c95742', success: result => {
        if (!result.confirm) return;
        this.setData({ removingCode: exerciseCode, error: '' });
        AiAPI.removeWorkoutExercise({ date: this.data.date, draft: this.data.draft, exerciseCode })
          .then(draft => this.setData({ ...this.draftState(draft), nutritionAccepted: false }))
          .catch(error => wx.showToast({ title: '删除失败：' + error, icon: 'none' }))
          .finally(() => this.setData({ removingCode: '' }));
      }
    });
  },

  confirmDraft() {
    if (!this.data.draft || this.data.loading || this.data.replacingCode
        || this.data.removingCode || this.data.confirming || this.data.recordingNutrition) return;
    const missing = (this.data.draft.exercises || []).find(item => this.requiresWeight(item)
      && !(Number(item.weight) > 0 && Number(item.weight) <= 1000));
    if (missing) {
      wx.showToast({ title: '请先填写“' + missing.name + '”的重量', icon: 'none' });
      return;
    }
    const invalidSets = (this.data.draft.exercises || []).find(item =>
      !Number.isInteger(Number(item.sets)) || Number(item.sets) < 1 || Number(item.sets) > 10);
    if (invalidSets) {
      wx.showToast({ title: '组数请输入1到10之间的整数', icon: 'none' });
      return;
    }
    wx.showModal({ title: '确认训练计划',
      content: this.data.replaceCancelled
        ? '新训练将覆盖当天已取消的计划，并保存为待完成计划；饮食卡不会记成已摄入。'
        : '训练将保存为待完成计划；饮食卡只作为建议，不会记成已摄入。',
      confirmText: '确认保存', confirmColor: '#159255', success: result => {
        if (!result.confirm) return;
        this.setData({ confirming: true });
        AiAPI.confirmWorkoutDraft({ date: this.data.date, draft: this.data.draft })
          .then(() => { wx.showToast({ title: '训练计划已保存', icon: 'success' });
            setTimeout(() => wx.navigateBack(), 700); })
          .catch(error => wx.showToast({ title: '保存失败：' + error, icon: 'none' }))
          .finally(() => this.setData({ confirming: false }));
      }
    });
  },

  toggleNutritionMeal(e) {
    if (this.data.recordingNutrition || this.data.nutritionAccepted) return;
    const recipeId = e.currentTarget.dataset.recipeid;
    const nutritionMeals = this.data.nutritionMeals.map(item => item.recipeId === recipeId
      ? { ...item, selectedForIntake: !item.selectedForIntake } : item);
    this.setData({ nutritionMeals,
      selectedNutritionCount: nutritionMeals.filter(item => item.selectedForIntake).length });
  },

  /** 用户明确确认后，才把勾选的系统菜谱按一份写入当天实际饮食。 */
  acceptNutritionPlan() {
    if (!this.data.draft || !this.data.draft.nutritionPlan || this.data.loading
        || this.data.recordingNutrition || this.data.nutritionAccepted) return;
    if (!this.data.canRecordNutrition) {
      wx.showToast({ title: '只能在训练当天记入实际饮食', icon: 'none' });
      return;
    }
    const selected = this.data.nutritionMeals.filter(item => item.selectedForIntake);
    if (!selected.length) {
      wx.showToast({ title: '请至少选择一餐', icon: 'none' });
      return;
    }
    wx.showModal({ title: '加入今日饮食？',
      content: '将所选 ' + selected.length + ' 餐各按 1 份记入今日饮食，并立即计入能量统计。',
      confirmText: '确认加入', confirmColor: '#159255', success: result => {
        if (!result.confirm) return;
        this.persistNutritionMeals(selected);
      }
    });
  },

  persistNutritionMeals(meals) {
    this.setData({ recordingNutrition: true });
    const requests = meals.map((meal, index) => {
      const key = this.data.date + ':' + meal.recipeId;
      const requestId = this._nutritionIntakeRequestIds[key]
        || ('ai-intake-' + Date.now().toString(36) + '-' + index + '-'
          + Math.random().toString(36).slice(2, 8));
      this._nutritionIntakeRequestIds[key] = requestId;
      const slot = this.nutritionMealSlot(meal.mealSlot);
      return CompareAPI.addRecipe({ date: this.data.date, recipeId: meal.recipeId, requestId,
        mealSlot: slot.code, servingCount: 1, consumedTime: slot.time });
    });
    Promise.all(requests)
      .then(() => {
        Object.keys(this._nutritionIntakeRequestIds).forEach(key => delete this._nutritionIntakeRequestIds[key]);
        const addedCalories = meals.reduce((sum, item) => sum + Number(item.calories || 0), 0);
        const plan = this.data.draft.nutritionPlan;
        const consumedCalories = Number(plan.consumedCalories || 0) + addedCalories;
        const nutritionPlan = { ...plan, consumedCalories,
          remainingCalories: Math.max(0, Number(plan.dailyTargetCalories || 0) - consumedCalories) };
        this.setData({ draft: { ...this.data.draft, nutritionPlan }, nutritionAccepted: true });
        wx.showToast({ title: '已加入今日饮食', icon: 'success' });
      })
      .catch(error => wx.showToast({
        title: '添加结果未确认，可再次点击重试：' + error, icon: 'none'
      }))
      .finally(() => this.setData({ recordingNutrition: false }));
  },

  nutritionMealSlot(mealSlot) {
    const slots = {
      '早餐': { code: 'BREAKFAST', time: '08:00' },
      '午餐': { code: 'LUNCH', time: '12:00' },
      '晚餐': { code: 'DINNER', time: '18:00' },
      '加餐': { code: 'SNACK', time: '15:00' }
    };
    return slots[mealSlot] || { code: 'SNACK', time: '15:00' };
  },

  inputExerciseWeight(e) {
    if (!this.data.draft || this.data.loading || this.data.confirming) return;
    const index = Number(e.currentTarget.dataset.index);
    const value = String(e.detail.value || '').trim();
    const exercises = (this.data.draft.exercises || []).map((item, itemIndex) => itemIndex === index
      ? { ...item, weight: value || null, weightSource: Number(value) > 0
        ? 'USER' : (item.loadType === 'OPTIONAL_LOAD' ? 'OPTIONAL' : 'UNSET'),
        weightNote: Number(value) > 0 ? '使用你本次填写的重量' : item.weightNote }
      : item);
    const draft = { ...this.data.draft, exercises };
    this.setData(this.draftState(draft));
  },

  inputExerciseSets(e) {
    if (!this.data.draft || this.data.loading || this.data.confirming) return;
    const index = Number(e.currentTarget.dataset.index);
    const value = String(e.detail.value || '').trim();
    const exercises = (this.data.draft.exercises || []).map((item, itemIndex) => itemIndex === index
      ? { ...item, sets: value === '' ? null : Number(value) }
      : item);
    this.setData(this.draftState({ ...this.data.draft, exercises }));
  },

  requiresWeight(item) {
    return item && (item.loadType === 'EXTERNAL_LOAD' || item.loadType === 'ASSISTANCE');
  },

  draftState(draft, preserveNutritionSelections = true) {
    const missingWeightCount = (draft && draft.exercises || []).filter(item => this.requiresWeight(item)
      && !(Number(item.weight) > 0 && Number(item.weight) <= 1000)).length;
    const invalidSetCount = (draft && draft.exercises || []).filter(item =>
      !Number.isInteger(Number(item.sets)) || Number(item.sets) < 1 || Number(item.sets) > 10).length;
    const previous = new Map((preserveNutritionSelections ? this.data.nutritionMeals : [])
      .map(item => [item.recipeId, item.selectedForIntake]));
    const nutritionMeals = (draft && draft.nutritionPlan && draft.nutritionPlan.meals || []).map(item => ({
      ...item,
      selectedForIntake: previous.has(item.recipeId) ? previous.get(item.recipeId) : true
    }));
    return { draft, missingWeightCount, invalidSetCount, nutritionMeals,
      selectedNutritionCount: nutritionMeals.filter(item => item.selectedForIntake).length };
  },

  ensureValidSets() {
    if (!this.data.draft || this.data.invalidSetCount === 0) return true;
    wx.showToast({ title: '组数请输入1到10之间的整数', icon: 'none' });
    return false;
  },

  rejectDraft() {
    if (this.data.confirming || this.data.replacingCode || this.data.removingCode
        || this.data.recordingNutrition) return;
    wx.showModal({ title: '放弃这份草稿？',
      content: '草稿尚未保存，放弃后可以重新告诉助手你的需求。',
      confirmText: '放弃', confirmColor: '#c95742', success: result => {
        if (result.confirm) this.setData({ draft: null, activeRequest: '', error: '',
          nutritionAccepted: false, nutritionMeals: [], selectedNutritionCount: 0,
          missingWeightCount: 0, invalidSetCount: 0 });
      }
    });
  }
});
