const app = getApp();
const { RecipeAPI, DatabaseAPI, ImageAPI } = require('../../../utils/api');
const { createUploadBatch } = require('../../../utils/image-upload');

Page({
  data: {
    recipeId: '',
    sourceTemplateId: '',
    sourceName: '',
    dataVersion: '',
    servingDescription: '',
    allergenInfo: '',
    recipeName: '',
    images: [],
    imageErrors: [],
    choosingImages: false,
    uploading: false,
    uploadCompleted: 0,
    uploadTotal: 0,
    uploadMessage: '',
    saving: false,
    removingImage: false,
    loadingRecipe: false,
    loadFailed: false,
    unknownFoods: [],
    selectorVisible: false,
    selectingIngredientIndex: -1,
    keyboardOpen: false,
    showBackToTop: false,
    ingredients: [],
    steps: '',
    totalNutrients: {
      protein: 0,
      carb: 0,
      fat: 0,
      calorie: 0
    }
  },
  /** 根据路由参数进入新增、编辑或复制系统菜谱模式。 */
  onLoad(options) {
    this._pendingUploads = new Set();
    this._unloaded = false;
    this._saveAttempted = false;
    const lookupId = options.recipeId || options.templateId || '';
    this._lookupRecipeId = lookupId;
    if (lookupId) {
      this.setData({ recipeId: options.recipeId || '', sourceTemplateId: options.templateId || '', loadingRecipe: true });
    }
    app.ensureLogin()
      .then(() => {
        if (!this._unloaded && lookupId) {
          this.loadRecipe();
        }
      })
      .catch((error) => {
        if (this._unloaded) return;
        this.setData({ loadingRecipe: false, loadFailed: true });
        wx.showToast({ title: '登录失败：' + error, icon: 'none' });
      });
  },
  /** 加载用户菜谱或系统模板，并填充表单和营养数据。 */
  loadRecipe() {
    this.setData({ loadingRecipe: true, loadFailed: false });
    // 调用API获取菜谱详情
    RecipeAPI.get(this._lookupRecipeId || this.data.recipeId)
      .then((recipe) => this.resolveIngredientReferences(recipe.ingredients || [])
        .then((ingredients) => ({ recipe, ingredients })))
      .then(({ recipe, ingredients }) => {
        if (this._unloaded) return;
        // 转换图片格式：从对象数组转换为字符串数组
        const imageUrls = recipe.images ? recipe.images.map(img => img.imageUrl) : [];
        
        this.setData({
          // 系统模板只作为初始值；保存时使用新业务编号，绝不覆盖模板。
          recipeId: this.data.sourceTemplateId ? '' : recipe.recipeId,
          recipeName: recipe.mealType,
          sourceName: recipe.sourceName || '',
          dataVersion: recipe.dataVersion || '',
          servingDescription: recipe.servingDescription || '',
          allergenInfo: recipe.allergenInfo || '',
          images: imageUrls,
          imageErrors: imageUrls.map(() => false),
          ingredients,
          steps: recipe.steps || '',
          totalNutrients: {
            protein: recipe.protein || 0,
            carb: recipe.carb || 0,
            fat: recipe.fat || 0,
            calorie: recipe.calorie || 0
          }
        });
        this.calculateNutrients();
      })
      .catch((error) => {
        if (this._unloaded) return;
        this.setData({ loadFailed: true });
        wx.showToast({
          title: '加载失败：' + error,
          icon: 'none'
        });
      }).finally(() => {
        if (!this._unloaded) this.setData({ loadingRecipe: false });
      });
  },
  /** 批量匹配食材库引用，识别历史数据中已经失效的食材名称。 */
  resolveIngredientReferences(ingredients) {
    const names = ingredients.map(item => item.foodName).filter(Boolean);
    if (!names.length) return Promise.resolve(ingredients);
    return DatabaseAPI.resolveFoods(names).then((foods) => {
      const references = new Map((foods || []).map(food => [food.foodName, food]));
      return ingredients.map(ingredient => {
        const reference = references.get((ingredient.foodName || '').trim());
        return { ...ingredient, databaseMatched: Boolean(reference), foodReference: reference || null };
      });
    });
  },
  /** 选择、压缩并以最多两张并发上传图片，逐张反馈上传结果。 */
  chooseImage() {
    if (this.imageActionsBlocked() || this.data.images.length >= 9) return;
    this.setData({ choosingImages: true, uploadMessage: '' });
    wx.chooseImage({
      count: 9 - this.data.images.length,
      // 菜谱展示不需要保留手机原图，优先压缩可明显降低上传失败概率
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        if (this._unloaded) return;
        const files = (res.tempFiles || res.tempFilePaths.map(path => ({ path })))
          .slice(0, 9 - this.data.images.length);
        if (!files.length) return;
        const originalImages = this.data.images.slice();
        const originalErrors = this.data.imageErrors.slice();
        this.setData({ uploading: true, uploadCompleted: 0, uploadTotal: files.length });
        this._uploadBatch = createUploadBatch(files, ImageAPI.upload, (result, completed) => {
          if (result.url) this._pendingUploads.add(result.url);
          if (this._unloaded) return;
          const update = { uploadCompleted: completed };
          if (result.url) {
            update.images = this.data.images.concat(result.url);
            update.imageErrors = this.data.imageErrors.concat(false);
          }
          this.setData(update);
        });
        this.calculateNutrients();
        this._uploadBatch.promise.then(({ results }) => {
          if (this._unloaded) return;
          const successes = results.filter(result => result && result.url).map(result => result.url);
          const failures = results.filter(result => result && result.error);
        this.setData({
            images: originalImages.concat(successes),
            imageErrors: originalErrors.concat(successes.map(() => false)),
            uploadMessage: failures.length
              ? '成功' + successes.length + '张，失败' + failures.length + '张：' + failures[0].error + '。可重新选择失败的图片。'
              : '已上传' + successes.length + '张图片，请保存菜谱'
          });
        }).finally(() => {
          this._uploadBatch = null;
          if (!this._unloaded) this.setData({ uploading: false });
        });
      },
      fail: (error) => {
        if (!this._unloaded && !/cancel/i.test(error.errMsg || '')) {
          wx.showToast({ title: '选择图片失败，请重试', icon: 'none' });
        }
      },
      complete: () => {
        if (!this._unloaded) this.setData({ choosingImages: false });
      }
    });
  },
  /** 判断保存或上传期间是否应锁定图片增删操作。 */
  imageActionsBlocked() {
    return this._unloaded || this.data.uploading || this.data.choosingImages || this.data.saving
      || this.data.removingImage || this.data.loadingRecipe || this.data.loadFailed || this.data.selectorVisible;
  },
  /**
   * 从表单移除图片；新上传且未保存的图片立即请求清理，
   * 已被菜谱引用的旧图片则等待保存事务提交后由服务端清理。
   */
  async removeImage(e) {
    if (this.imageActionsBlocked()) return;
    const index = Number(e.currentTarget.dataset.index);
    const imageUrl = this.data.images[index];
    if (!imageUrl) return;
    this.setData({ removingImage: true });
    try {
      // 旧图仅改表单，保存成功后由后端清理。未保存的新图可以立即清理。
      // 一旦尝试过保存，网络失败也不能证明未提交，交给后端判断引用，避免误删。
      if (this._pendingUploads.has(imageUrl) && !this._saveAttempted) {
        await ImageAPI.delete(imageUrl);
        this._pendingUploads.delete(imageUrl);
      }
      if (!this._unloaded) this.setData({
        images: this.data.images.filter((_, i) => i !== index),
        imageErrors: this.data.imageErrors.filter((_, i) => i !== index),
        uploadMessage: '图片已从表单移除，请保存菜谱使修改生效'
      });
    } catch (error) {
      if (!this._unloaded) wx.showToast({ title: '图片删除失败：' + (error.message || error), icon: 'none' });
    } finally {
      if (!this._unloaded) this.setData({ removingImage: false });
    }
  },
  /** 标记预览失败的图片，允许用户重试显示或移除。 */
  onImageError(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (this._unloaded || this.data.images[index] !== e.currentTarget.dataset.url) return;
    this.setData({ ['imageErrors[' + index + ']']: true });
  },
  /** 通过临时清空地址强制 image 组件重新请求原图片。 */
  retryImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (!this._unloaded) this.setData({ ['imageErrors[' + index + ']']: false });
  },
  /** 删除本次页面已上传但最终未随菜谱保存的 OSS 图片。 */
  cleanupPendingUploads() {
    // 小批量串行清理，失败只记录，不对用户隐藏成功保存的菜谱。
    const urls = Array.from(this._pendingUploads);
    return urls.reduce((chain, url) => chain.then(() => ImageAPI.delete(url)
      .then(() => this._pendingUploads.delete(url))
      .catch(() => console.warn('未保存图片清理失败，可能需要稍后清理'))), Promise.resolve());
  },
  /** 离开页面时取消上传批次并异步清理未保存图片。 */
  onUnload() {
    this._unloaded = true;
    // 保存结果不明时宁可留下待清理对象，也不能把可能已保存的图片删掉。
    if (this._uploadBatch) {
      const batch = this._uploadBatch;
      batch.cancel();
      batch.promise.then(({ results }) => {
        // 包含退出瞬间刚成功、尚未来得及写入页面状态的图片。
        results.forEach(result => { if (result && result.url) this._pendingUploads.add(result.url); });
        if (!this._saveAttempted) this.cleanupPendingUploads();
      });
    } else if (!this._saveAttempted) {
      this.cleanupPendingUploads();
    }
  },
  /** 更新菜谱名称。 */
  inputRecipeName(e) {
    this.setData({ recipeName: e.detail.value });
  },
  /** 更新制作步骤。 */
  inputSteps(e) {
    this.setData({ steps: e.detail.value });
  },
  /** 根据软键盘高度隐藏或恢复底部操作栏，防止遮挡输入区域。 */
  onKeyboardHeightChange(e) {
    const keyboardOpen = Number(e.detail && e.detail.height || 0) > 0;
    if (keyboardOpen !== this.data.keyboardOpen) this.setData({ keyboardOpen });
  },
  /** 仅在滚动跨过阈值时切换返回顶部入口。 */
  onPageScroll(e) {
    const showBackToTop = Number(e.scrollTop || 0) > 600;
    if (showBackToTop !== this.data.showBackToTop) this.setData({ showBackToTop });
  },
  /** 平滑返回菜谱表单顶部。 */
  scrollToTop() {
    wx.pageScrollTo({ scrollTop: 0, duration: 250 });
  },
  /** 在表单末尾新增一行空食材。 */
  addIngredient() {
    this.appendIngredient(false);
  },
  /** 从固定操作栏新增食材，并直接打开食材选择器。 */
  quickAddIngredient() {
    this.appendIngredient(true);
  },
  /** 创建食材行，可选择立即进入基础食材搜索。 */
  appendIngredient(openSelector) {
    if (this.imageActionsBlocked()) {
      wx.showToast({ title: this.data.loadFailed ? '菜谱加载失败，请返回重试' : '请等待当前操作完成', icon: 'none' });
      return;
    }
    const index = this.data.ingredients.length;
    const ingredients = [...this.data.ingredients, { foodName: '', weight: '', databaseMatched: true, foodReference: null, protein: 0, carb: 0, fat: 0, calorie: 0 }];
    const update = { ingredients };
    if (openSelector) {
      update.selectorVisible = true;
      update.selectingIngredientIndex = index;
    }
    this.setData(update, () => {
      if (wx.pageScrollTo) wx.pageScrollTo({ selector: '#ingredient-' + index, duration: 250 });
    });
  },
  /** 删除指定食材行并重新汇总营养。 */
  removeIngredient(e) {
    const index = e.currentTarget.dataset.index;
    const ingredients = this.data.ingredients.filter((_, i) => i !== index);
    this.setData({ ingredients });
    this.calculateNutrients();
  },
  /** 为指定食材行打开分页搜索选择器。 */
  openFoodSelector(e) {
    this.setData({ selectorVisible: true, selectingIngredientIndex: Number(e.currentTarget.dataset.index) });
  },
  /** 关闭食材选择器并清除当前行索引。 */
  closeFoodSelector() {
    this.setData({ selectorVisible: false, selectingIngredientIndex: -1 });
  },
  /** 将基础食材库中的选项写入当前食材行。 */
  selectIngredient(e) {
    const index = this.data.selectingIngredientIndex;
    const selected = e.detail.item;
    if (index < 0 || !selected) return;
    const ingredients = [...this.data.ingredients];
    ingredients[index] = { ...ingredients[index], foodName: selected.foodName, databaseMatched: true, foodReference: selected };
    this.setData({ ingredients, selectorVisible: false, selectingIngredientIndex: -1 });
    this.calculateNutrients();
  },
  /** 更新食材重量并按每 100 克营养数据重新计算。 */
  inputIngredientWeight(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const ingredients = [...this.data.ingredients];
    ingredients[index].weight = value;
    this.setData({ ingredients });
    this.calculateNutrients();
  },
  /** 汇总全部有效食材的蛋白质、碳水、脂肪和热量。 */
  calculateNutrients() {
    let totalProtein = 0;
    let totalCarb = 0;
    let totalFat = 0;
    let totalCalorie = 0;

    const unknownFoods = [];
    const ingredients = this.data.ingredients.map(ingredient => {
      const foodName = (ingredient.foodName || '').trim();
      const food = ingredient.foodReference;
      const weight = Number(ingredient.weight);
      const calculated = { ...ingredient, databaseMatched: !foodName || Boolean(food), protein: 0, carb: 0, fat: 0, calorie: 0 };
      if (foodName && !food) unknownFoods.push(foodName);

      if (food && weight > 0) {
          const protein = (food.protein * weight) / 100;
          const carb = (food.carb * weight) / 100;
          const fat = (food.fat * weight) / 100;
          const calorie = (food.calorie * weight) / 100;

          totalProtein += protein;
          totalCarb += carb;
          totalFat += fat;
          totalCalorie += calorie;
          calculated.protein = protein.toFixed(1);
          calculated.carb = carb.toFixed(1);
          calculated.fat = fat.toFixed(1);
          calculated.calorie = calorie.toFixed(1);
      }
      return calculated;
    });

    this.setData({
      ingredients,
      unknownFoods: [...new Set(unknownFoods)],
      totalNutrients: {
        protein: totalProtein.toFixed(1),
        carb: totalCarb.toFixed(1),
        fat: totalFat.toFixed(1),
        calorie: totalCalorie.toFixed(1)
      }
    });
  },
  /** 返回清理首尾空白后的菜谱名称。 */
  getMealType() {
    const hour = new Date().getHours();
    if (hour < 10) return '早餐';
    if (hour < 14) return '午餐';
    if (hour < 18) return '晚餐';
    return '加餐';
  },
  /** 校验表单、保存用户菜谱，并确认哪些已上传图片已经被正式引用。 */
  saveRecipe() {
    if (this.imageActionsBlocked()) {
      wx.showToast({ title: this.data.loadFailed ? '菜谱加载失败，请返回重试' : '请等待图片操作或保存完成', icon: 'none' });
      return;
    }
    // 验证：至少需要有一个食材
    const validIngredients = this.data.ingredients
      .filter(item => item.foodName && item.weight)
      .map(({ foodReference, databaseMatched, ...item }) => item);
    if (validIngredients.length === 0) {
      wx.showToast({
        title: '请至少添加一个食材',
        icon: 'none'
      });
      return;
    }
    if (this.data.unknownFoods.length) {
      wx.showToast({ title: '请选择基础食物：' + this.data.unknownFoods[0], icon: 'none' });
      return;
    }

    const { recipeId, recipeName, images, steps, totalNutrients } = this.data;
    const mealType = recipeName || this.getMealType();
    
    const recipe = {
      recipeId: recipeId || Date.now().toString(),
      name: mealType,
      description: '',
      ingredients: validIngredients,
      steps: steps,
      protein: parseFloat(totalNutrients.protein),
      carb: parseFloat(totalNutrients.carb),
      fat: parseFloat(totalNutrients.fat),
      calorie: parseFloat(totalNutrients.calorie),
      images: images
    };

    // 首次失败后重试也复用业务ID，避免网络响应丢失时创建第二份菜谱。
    this.setData({ recipeId: recipe.recipeId, saving: true });
    this._saveAttempted = true;

    // 调用API保存菜谱
    RecipeAPI.save(recipe)
      .then(() => {
        images.forEach(url => this._pendingUploads.delete(url));
        this.cleanupPendingUploads();
        if (this._unloaded) return;
        wx.showToast({ title: '保存成功' });
        setTimeout(() => {
          if (!this._unloaded) wx.navigateBack();
        }, 1000);
      })
      .catch((error) => {
        if (this._unloaded) return;
        this.setData({ saving: false });
        wx.showToast({
          title: '保存失败：' + error,
          icon: 'none'
        });
      });
  }
})
