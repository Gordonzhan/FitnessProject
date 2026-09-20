-- R1 扩充：系统菜谱由 12 个增加到 30 个，并统一增肌/减脂友好标签。
-- 依赖：20260905_import_official_reference_data.sql、20260909_system_recipe_templates.sql。
USE `fitness_diary`;

-- 统一首版分类口径，避免同一标签下混入不符合当前规则的模板。
UPDATE `system_recipe_templates` SET `goal_tags` = CASE `template_id`
  WHEN 'sys_oat_yogurt' THEN '减脂友好,保持'
  WHEN 'sys_spinach_egg_oat' THEN '减脂友好,保持'
  WHEN 'sys_sweet_egg_spinach' THEN '减脂友好,保持'
  WHEN 'sys_chicken_rice_broccoli' THEN '增肌友好,保持'
  WHEN 'sys_chicken_quinoa_spinach' THEN '减脂友好,增肌友好'
  WHEN 'sys_salmon_rice_broccoli' THEN '增肌友好,保持'
  WHEN 'sys_salmon_quinoa_spinach' THEN '增肌友好,保持'
  WHEN 'sys_chicken_sweet_broccoli' THEN '增肌友好'
  WHEN 'sys_egg_quinoa_broccoli' THEN '减脂友好,保持'
  WHEN 'sys_large_chicken_rice' THEN '增肌友好'
  WHEN 'sys_salmon_sweet_spinach' THEN '增肌友好,保持'
  WHEN 'sys_quinoa_egg_spinach' THEN '减脂友好,保持'
  ELSE `goal_tags` END
WHERE `template_id` LIKE 'sys_%';

INSERT INTO `system_recipe_templates`
(`template_id`,`name`,`aliases`,`category`,`goal_tags`,`serving_description`,`steps`,`protein`,`carb`,`fat`,`calorie`,`allergen_info`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('sys_cod_sweet_broccoli','鳕鱼红薯西兰花','鳕鱼地瓜,低脂鱼餐','午餐','减脂友好','1人份；鳕鱼和西兰花生重、红薯熟重','1. 鳕鱼蒸熟或烤熟。\n2. 西兰花蒸熟，红薯提前烤熟。\n3. 一同装盘；额外用油和酱料需另行记录。',0,0,0,0,'含鱼类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,64),
('sys_shrimp_quinoa_pepper','虾仁藜麦彩椒碗','虾仁藜麦,彩椒虾仁','午餐','减脂友好','1人份；虾仁和彩椒生重、藜麦熟重','1. 虾仁炒熟或焯熟。\n2. 彩椒切块快速翻炒。\n3. 与熟藜麦装碗。',0,0,0,0,'含甲壳类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,63),
('sys_tuna_chickpea_cucumber','金枪鱼鹰嘴豆黄瓜沙拉','吞拿鱼沙拉,鹰嘴豆沙拉','午餐','减脂友好','1人份；罐头食材按沥干重、黄瓜按生重','1. 金枪鱼和鹰嘴豆充分沥干。\n2. 黄瓜切块。\n3. 混合后用香辛料调味。',0,0,0,0,'含鱼类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,62),
('sys_eggwhite_oat_spinach','蛋清燕麦菠菜碗','蛋清燕麦,咸燕麦碗','早餐','减脂友好','1人份；燕麦干重、菠菜生重','1. 燕麦加水煮熟。\n2. 加入蛋清搅拌至完全熟透。\n3. 放入焯熟菠菜。',0,0,0,0,'含蛋；燕麦过敏者慎用','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,61),
('sys_chicken_potato_carrot','鸡胸土豆胡萝卜盘','鸡胸土豆,鸡肉根茎餐','午餐','减脂友好','1人份；鸡胸熟重，土豆和胡萝卜生重','1. 土豆和胡萝卜蒸熟。\n2. 鸡胸肉烤熟切片。\n3. 一同装盘。',0,0,0,0,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,60),
('sys_scallop_quinoa_zucchini','扇贝藜麦西葫芦','带子藜麦,扇贝能量碗','晚餐','减脂友好','1人份；扇贝和西葫芦生重、藜麦熟重','1. 扇贝煎熟。\n2. 西葫芦切片煎熟。\n3. 与熟藜麦装盘。',0,0,0,0,'含贝类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,59),
('sys_yogurt_strawberry_oat','草莓酸奶燕麦杯','草莓酸奶,燕麦酸奶杯','早餐','减脂友好','1人份；燕麦干重，其余为可食部','1. 燕麦与脱脂希腊酸奶拌匀。\n2. 草莓洗净切块铺在顶部。\n3. 冷藏后食用。',0,0,0,0,'含乳制品；燕麦过敏者慎用','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,58),
('sys_blackbean_egg_veg','黑豆鸡蛋蔬菜碗','黑豆鸡蛋,豆类蔬菜碗','晚餐','减脂友好','1人份；黑豆沥干重、蔬菜生重','1. 黑豆充分沥干。\n2. 鸡蛋煮熟切块，彩椒和菠菜炒熟。\n3. 混合装碗。',0,0,0,0,'含蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,57),
('sys_chicken_quinoa_cabbage','鸡胸藜麦紫甘蓝碗','鸡胸紫甘蓝,鸡胸藜麦沙拉','午餐','减脂友好','1人份；鸡胸和藜麦熟重、紫甘蓝生重','1. 鸡胸肉烤熟切片。\n2. 紫甘蓝切丝。\n3. 与熟藜麦混合；酱汁需另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,56),
('sys_beef_rice_broccoli','牛里脊糙米西兰花','牛肉糙米,高蛋白牛肉饭','午餐','增肌友好','1人份；牛肉和米饭熟重、西兰花生重','1. 牛里脊切片加热至熟。\n2. 西兰花蒸熟。\n3. 与熟糙米饭装盘。',0,0,0,0,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,55),
('sys_double_chicken_rice','双倍鸡胸糙米菠菜','加量鸡胸饭,高蛋白鸡胸餐','午餐','增肌友好','1人份；鸡胸和米饭熟重、菠菜生重','1. 鸡胸肉烤熟后切片。\n2. 菠菜焯熟。\n3. 与熟糙米饭装盘。',0,0,0,0,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,54),
('sys_salmon_quinoa_avocado','三文鱼藜麦牛油果碗','鲑鱼牛油果,三文鱼增肌碗','晚餐','增肌友好','1人份；三文鱼和藜麦熟重、牛油果可食部','1. 三文鱼烤熟切块。\n2. 牛油果切片。\n3. 与熟藜麦装碗。',0,0,0,0,'含鱼类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,53),
('sys_pork_rice_pepper','猪里脊糙米彩椒','瘦猪肉饭,猪柳彩椒','午餐','增肌友好','1人份；猪里脊和彩椒生重、米饭熟重','1. 猪里脊切片炒熟。\n2. 加入彩椒快速翻炒。\n3. 与熟糙米饭装盘。',0,0,0,0,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,52),
('sys_chicken_chickpea_quinoa','鸡胸鹰嘴豆藜麦碗','鸡胸鹰嘴豆,高蛋白藜麦碗','午餐','增肌友好','1人份；鸡胸和藜麦熟重、鹰嘴豆沥干重','1. 鸡胸肉烤熟切片。\n2. 鹰嘴豆充分沥干。\n3. 与熟藜麦混合装碗。',0,0,0,0,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,51),
('sys_salmon_sweet_egg','三文鱼红薯鸡蛋盘','鲑鱼鸡蛋,三文鱼地瓜','晚餐','增肌友好','1人份；三文鱼和红薯熟重、鸡蛋可食部','1. 三文鱼和红薯分别烤熟。\n2. 鸡蛋煮熟切开。\n3. 一同装盘。',0,0,0,0,'含鱼类和蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,50),
('sys_tuna_quinoa_egg_avocado','金枪鱼藜麦鸡蛋碗','吞拿鱼藜麦,金枪鱼牛油果','午餐','增肌友好','1人份；金枪鱼沥干重、藜麦熟重','1. 金枪鱼充分沥干。\n2. 鸡蛋煮熟，牛油果切片。\n3. 与熟藜麦装碗。',0,0,0,0,'含鱼类和蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,49),
('sys_shrimp_rice_egg','虾仁糙米鸡蛋碗','虾仁鸡蛋饭,高蛋白虾仁饭','午餐','增肌友好','1人份；虾仁生重、米饭熟重、鸡蛋可食部','1. 虾仁加热至完全熟透。\n2. 鸡蛋煮熟切块。\n3. 与熟糙米饭装碗。',0,0,0,0,'含甲壳类和蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,48),
('sys_yogurt_oat_banana_peanut','香蕉花生酱高蛋白燕麦杯','香蕉燕麦,花生酱酸奶杯','早餐','增肌友好','1人份；燕麦干重，其余为可食部','1. 燕麦与脱脂希腊酸奶拌匀。\n2. 香蕉切片铺在顶部。\n3. 加入花生酱拌匀。',0,0,0,0,'含乳制品和花生；燕麦过敏者慎用','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算；目标标签为项目规则','system-recipes-2026.09-v2',1,47)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`aliases`=VALUES(`aliases`),`category`=VALUES(`category`),
 `goal_tags`=VALUES(`goal_tags`),`serving_description`=VALUES(`serving_description`),`steps`=VALUES(`steps`),
 `allergen_info`=VALUES(`allergen_info`),`source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),
 `data_version`=VALUES(`data_version`),`enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

DELETE sri FROM `system_recipe_ingredients` sri JOIN `system_recipe_templates` srt ON srt.id=sri.template_id
WHERE srt.data_version='system-recipes-2026.09-v2';

INSERT INTO `system_recipe_ingredients`
(`template_id`,`food_name`,`weight`,`protein`,`carb`,`fat`,`calorie`,`source_name`,`source_ref`,`data_version`,`sort_order`)
SELECT t.id,x.food_name,x.weight,
 ROUND(f.protein*x.weight/100,2),ROUND(f.carb*x.weight/100,2),ROUND(f.fat*x.weight/100,2),ROUND(f.calorie*x.weight/100,2),
 f.source_name,f.source_ref,f.data_version,x.sort_order
FROM (
 SELECT 'sys_cod_sweet_broccoli' tid,'生大西洋鳕鱼' food_name,180.00 weight,1 sort_order UNION ALL SELECT 'sys_cod_sweet_broccoli','烤红薯',200,2 UNION ALL SELECT 'sys_cod_sweet_broccoli','生西兰花',150,3
 UNION ALL SELECT 'sys_shrimp_quinoa_pepper','生虾仁',180,1 UNION ALL SELECT 'sys_shrimp_quinoa_pepper','熟藜麦',160,2 UNION ALL SELECT 'sys_shrimp_quinoa_pepper','生红甜椒',150,3
 UNION ALL SELECT 'sys_tuna_chickpea_cucumber','水浸金枪鱼罐头',150,1 UNION ALL SELECT 'sys_tuna_chickpea_cucumber','鹰嘴豆罐头',150,2 UNION ALL SELECT 'sys_tuna_chickpea_cucumber','生带皮黄瓜',200,3
 UNION ALL SELECT 'sys_eggwhite_oat_spinach','鸡蛋清',200,1 UNION ALL SELECT 'sys_eggwhite_oat_spinach','传统燕麦片',40,2 UNION ALL SELECT 'sys_eggwhite_oat_spinach','嫩菠菜',100,3
 UNION ALL SELECT 'sys_chicken_potato_carrot','熟鸡胸肉',120,1 UNION ALL SELECT 'sys_chicken_potato_carrot','生去皮褐皮土豆',250,2 UNION ALL SELECT 'sys_chicken_potato_carrot','生胡萝卜',100,3
 UNION ALL SELECT 'sys_scallop_quinoa_zucchini','扇贝柱',200,1 UNION ALL SELECT 'sys_scallop_quinoa_zucchini','熟藜麦',150,2 UNION ALL SELECT 'sys_scallop_quinoa_zucchini','生西葫芦',200,3
 UNION ALL SELECT 'sys_yogurt_strawberry_oat','脱脂希腊酸奶',250,1 UNION ALL SELECT 'sys_yogurt_strawberry_oat','草莓',150,2 UNION ALL SELECT 'sys_yogurt_strawberry_oat','传统燕麦片',30,3
 UNION ALL SELECT 'sys_blackbean_egg_veg','黑豆罐头',180,1 UNION ALL SELECT 'sys_blackbean_egg_veg','全鸡蛋',100,2 UNION ALL SELECT 'sys_blackbean_egg_veg','生红甜椒',100,3 UNION ALL SELECT 'sys_blackbean_egg_veg','嫩菠菜',100,4
 UNION ALL SELECT 'sys_chicken_quinoa_cabbage','熟鸡胸肉',130,1 UNION ALL SELECT 'sys_chicken_quinoa_cabbage','熟藜麦',150,2 UNION ALL SELECT 'sys_chicken_quinoa_cabbage','生紫甘蓝',150,3
 UNION ALL SELECT 'sys_beef_rice_broccoli','熟牛里脊',180,1 UNION ALL SELECT 'sys_beef_rice_broccoli','熟糙米饭',250,2 UNION ALL SELECT 'sys_beef_rice_broccoli','生西兰花',150,3
 UNION ALL SELECT 'sys_double_chicken_rice','熟鸡胸肉',200,1 UNION ALL SELECT 'sys_double_chicken_rice','熟糙米饭',300,2 UNION ALL SELECT 'sys_double_chicken_rice','嫩菠菜',100,3
 UNION ALL SELECT 'sys_salmon_quinoa_avocado','熟三文鱼',180,1 UNION ALL SELECT 'sys_salmon_quinoa_avocado','熟藜麦',220,2 UNION ALL SELECT 'sys_salmon_quinoa_avocado','哈斯牛油果',100,3
 UNION ALL SELECT 'sys_pork_rice_pepper','生猪里脊',200,1 UNION ALL SELECT 'sys_pork_rice_pepper','熟糙米饭',300,2 UNION ALL SELECT 'sys_pork_rice_pepper','生红甜椒',150,3
 UNION ALL SELECT 'sys_chicken_chickpea_quinoa','熟鸡胸肉',180,1 UNION ALL SELECT 'sys_chicken_chickpea_quinoa','鹰嘴豆罐头',150,2 UNION ALL SELECT 'sys_chicken_chickpea_quinoa','熟藜麦',180,3
 UNION ALL SELECT 'sys_salmon_sweet_egg','熟三文鱼',180,1 UNION ALL SELECT 'sys_salmon_sweet_egg','烤红薯',250,2 UNION ALL SELECT 'sys_salmon_sweet_egg','熟鸡蛋（水煮）',100,3
 UNION ALL SELECT 'sys_tuna_quinoa_egg_avocado','水浸金枪鱼罐头',180,1 UNION ALL SELECT 'sys_tuna_quinoa_egg_avocado','熟藜麦',250,2 UNION ALL SELECT 'sys_tuna_quinoa_egg_avocado','熟鸡蛋（水煮）',100,3 UNION ALL SELECT 'sys_tuna_quinoa_egg_avocado','哈斯牛油果',80,4
 UNION ALL SELECT 'sys_shrimp_rice_egg','生虾仁',220,1 UNION ALL SELECT 'sys_shrimp_rice_egg','熟糙米饭',300,2 UNION ALL SELECT 'sys_shrimp_rice_egg','熟鸡蛋（水煮）',100,3
 UNION ALL SELECT 'sys_yogurt_oat_banana_peanut','脱脂希腊酸奶',250,1 UNION ALL SELECT 'sys_yogurt_oat_banana_peanut','传统燕麦片',70,2 UNION ALL SELECT 'sys_yogurt_oat_banana_peanut','成熟香蕉',150,3 UNION ALL SELECT 'sys_yogurt_oat_banana_peanut','顺滑花生酱',30,4
) x JOIN `system_recipe_templates` t ON t.template_id=x.tid JOIN `food_database` f ON f.food_name=x.food_name;

UPDATE `system_recipe_templates` t JOIN (
 SELECT template_id,ROUND(SUM(protein),2) protein,ROUND(SUM(carb),2) carb,
        ROUND(SUM(fat),2) fat,ROUND(SUM(calorie),2) calorie
 FROM `system_recipe_ingredients` GROUP BY template_id
) n ON n.template_id=t.id
SET t.protein=n.protein,t.carb=n.carb,t.fat=n.fat,t.calorie=n.calorie
WHERE t.data_version='system-recipes-2026.09-v2';

SELECT goal_tags,COUNT(*) AS template_count FROM system_recipe_templates WHERE enabled=1 GROUP BY goal_tags ORDER BY goal_tags;
