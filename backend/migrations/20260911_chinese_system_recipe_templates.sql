-- R1 中国本土化扩充：增加“中式家常”菜式筛选及 20 个中式系统菜谱。
-- 依赖：20260905_import_official_reference_data.sql、20260909、20260910。
-- 可重复执行；配方由项目按中国居民膳食指南的食物多样、粗细搭配、少油烹调原则编制。
USE `fitness_diary`;

-- MySQL 5.7 兼容的幂等加列。
SET @cuisine_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='system_recipe_templates' AND COLUMN_NAME='cuisine_type'
);
SET @add_cuisine_sql = IF(@cuisine_column_exists=0,
  'ALTER TABLE system_recipe_templates ADD COLUMN cuisine_type VARCHAR(20) NOT NULL DEFAULT ''其他'' AFTER category',
  'SELECT 1');
PREPARE add_cuisine_stmt FROM @add_cuisine_sql;
EXECUTE add_cuisine_stmt;
DEALLOCATE PREPARE add_cuisine_stmt;

UPDATE `system_recipe_templates`
SET `cuisine_type`='西式轻食'
WHERE `data_version` IN ('system-recipes-2026.09-v1','system-recipes-2026.09-v2');

-- 用已核对的 USDA FoodData Central 豆腐记录替换早期项目估算值。
INSERT INTO `food_database`
(`food_name`,`aliases`,`category`,`serving_state`,`protein`,`carb`,`fat`,`calorie`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('北豆腐','老豆腐,硬豆腐','蛋奶豆制品','可食部',17.27,2.78,8.72,144.00,'USDA_FDC','FDC:172475','sr-legacy-retrieved-2026-09-06',1,90)
ON DUPLICATE KEY UPDATE `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),`serving_state`=VALUES(`serving_state`),
 `protein`=VALUES(`protein`),`carb`=VALUES(`carb`),`fat`=VALUES(`fat`),`calorie`=VALUES(`calorie`),
 `source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),`data_version`=VALUES(`data_version`),`enabled`=1;

INSERT INTO `system_recipe_templates`
(`template_id`,`name`,`aliases`,`category`,`cuisine_type`,`goal_tags`,`serving_description`,`steps`,`protein`,`carb`,`fat`,`calorie`,`allergen_info`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('sys_cn_millet_egg_spinach_congee','小米蛋花菠菜粥','小米粥,蛋花粥,菠菜粥','中式早餐','中式家常','减脂友好','1人份；小米按干重，蛋清与鸡蛋按可食部','1. 小米洗净后加水煮至软烂。\n2. 淋入蛋清搅成蛋花并煮熟。\n3. 加入菠菜煮熟，水煮蛋切开搭配。',0,0,0,0,'含蛋','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,140),
('sys_cn_corn_shrimp_steamed_egg','玉米虾仁蒸蛋','虾仁蒸水蛋,玉米蒸蛋','中式早餐','中式家常','减脂友好','1人份；虾仁与玉米按生重，鸡蛋按可食部','1. 鸡蛋打散后加入温水。\n2. 放入玉米和虾仁，蒸至完全凝固。\n3. 小白菜焯熟后搭配；调味料另行记录。',0,0,0,0,'含蛋和甲壳类','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,139),
('sys_cn_steamed_cod_brown_rice','清蒸鳕鱼糙米青菜','清蒸鱼套餐,鳕鱼米饭','中式午餐','中式家常','减脂友好','1人份；鳕鱼和青菜按生重、糙米饭按熟重','1. 鳕鱼加姜片蒸至完全熟透。\n2. 小白菜焯熟。\n3. 与熟糙米饭搭配；酱油和用油需另行记录。',0,0,0,0,'含鱼类','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,138),
('sys_cn_mushroom_chicken_rice','香菇鸡丝糙米饭','香菇鸡肉饭,鸡丝盖饭','中式午餐','中式家常','减脂友好','1人份；鸡胸和米饭按熟重、香菇按生重','1. 香菇切片焯熟或少量水焖熟。\n2. 熟鸡胸撕成鸡丝，与香菇拌匀。\n3. 铺在糙米饭上；额外用油和酱料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,137),
('sys_cn_tomato_beef_potato_stew','番茄牛肉土豆煲','西红柿牛肉,牛肉炖土豆','中式晚餐','中式家常','减脂友好','1人份；牛肉按熟重，土豆和番茄按生重','1. 番茄切块煮出汤汁。\n2. 加入土豆块焖至软熟。\n3. 放入熟牛肉煮热；额外用油和调味料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,136),
('sys_cn_pepper_pork_brown_rice_light','青椒里脊糙米饭','青椒肉丝饭,猪柳饭','中式午餐','中式家常','减脂友好','1人份；里脊和彩椒按生重、糙米饭按熟重','1. 里脊切丝，在不粘锅中加少量水滑熟。\n2. 加入彩椒快速翻熟。\n3. 与糙米饭搭配；若使用烹调油需另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,135),
('sys_cn_shrimp_egg_brown_rice_set','虾仁蒸蛋糙米套餐','虾仁水蒸蛋,蒸蛋米饭','中式午餐','中式家常','减脂友好','1人份；虾仁和青菜按生重，蛋按可食部，米饭按熟重','1. 鸡蛋加温水打匀，放入虾仁蒸熟。\n2. 小白菜焯熟。\n3. 与糙米饭搭配；调味料另行记录。',0,0,0,0,'含蛋和甲壳类','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,134),
('sys_cn_chicken_millet_carrot_congee','鸡丝小米胡萝卜粥','鸡肉小米粥,胡萝卜鸡丝粥','中式早餐','中式家常','减脂友好','1人份；小米按干重、鸡胸按熟重、蔬菜按生重','1. 小米和胡萝卜丁加水煮至软烂。\n2. 加入鸡丝煮热。\n3. 放入菠菜煮熟，按需用香辛料调味。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,133),
('sys_cn_cod_tomato_rice_soup','鳕鱼番茄青菜汤饭','番茄鱼片饭,鳕鱼汤泡饭','中式晚餐','中式家常','减脂友好','1人份；鱼和蔬菜按生重、米饭按熟重','1. 番茄煮出汤汁后放入鳕鱼片。\n2. 鱼片熟透后加入小白菜。\n3. 配糙米饭食用；调味料另行记录。',0,0,0,0,'含鱼类','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,132),
('sys_cn_tofu_mushroom_greens_rice','家常豆腐香菇青菜饭','豆腐香菇,豆腐青菜饭','中式晚餐','中式家常','减脂友好','1人份；豆腐按可食部、香菇青菜按生重、米饭按熟重','1. 豆腐切块蒸热或不粘锅煎热。\n2. 香菇和小白菜加少量水焖熟。\n3. 与糙米饭搭配；烹调油和酱料另行记录。',0,0,0,0,'含大豆','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,131),
('sys_cn_beef_potato_brown_rice','土豆炖牛肉糙米饭','牛肉土豆饭,家常炖牛肉','中式午餐','中式家常','增肌友好','1人份；牛肉和米饭按熟重，土豆胡萝卜按生重','1. 土豆和胡萝卜加水焖至软熟。\n2. 加入熟牛肉继续炖煮入味。\n3. 配糙米饭；用油和含糖酱料需另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,130),
('sys_cn_large_mushroom_chicken_rice','加量香菇鸡肉糙米饭','香菇鸡丁饭,高蛋白鸡肉饭','中式午餐','中式家常','增肌友好','1人份；鸡胸和米饭按熟重、香菇按生重','1. 香菇切片焖熟。\n2. 熟鸡胸切丁与香菇翻拌加热。\n3. 盖在糙米饭上；烹调油和酱料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,129),
('sys_cn_tomato_egg_chicken_rice','番茄鸡蛋鸡胸盖饭','西红柿炒蛋鸡肉饭,番茄蛋盖饭','中式午餐','中式家常','增肌友好','1人份；鸡胸和米饭按熟重，番茄按生重，蛋按可食部','1. 番茄加少量水焖出汁。\n2. 加入蛋液翻至熟透，再放入鸡胸肉。\n3. 盖在糙米饭上；若使用烹调油需另行记录。',0,0,0,0,'含蛋','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,128),
('sys_cn_large_pepper_pork_rice','加量青椒里脊糙米饭','青椒肉丝盖饭,高蛋白猪柳饭','中式午餐','中式家常','增肌友好','1人份；猪里脊和彩椒按生重、米饭按熟重','1. 里脊切丝炒至完全熟透。\n2. 加入彩椒快速翻熟。\n3. 与糙米饭搭配；实际用油和酱料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,127),
('sys_cn_salmon_steamed_egg_rice','三文鱼蒸蛋糙米饭','鲑鱼蒸蛋,三文鱼鸡蛋饭','中式晚餐','中式家常','增肌友好','1人份；鱼和米饭按熟重、蛋按可食部、青菜按生重','1. 鸡蛋加温水蒸至凝固。\n2. 三文鱼蒸热，小白菜焯熟。\n3. 与糙米饭搭配；调味料另行记录。',0,0,0,0,'含鱼类和蛋','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,126),
('sys_cn_shrimp_egg_brown_rice_stew','虾仁鸡蛋糙米烩饭','虾仁蛋饭,少油虾仁饭','中式午餐','中式家常','增肌友好','1人份；虾仁和彩椒按生重、蛋按可食部、米饭按熟重','1. 虾仁加热至熟透后放入彩椒。\n2. 加入蛋液翻至完全凝固。\n3. 拌入糙米饭焖热；若用油需另行记录。',0,0,0,0,'含蛋和甲壳类','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,125),
('sys_cn_chicken_blackbean_grain_rice','鸡胸黑豆杂粮饭','鸡肉杂粮饭,黑豆鸡胸饭','中式午餐','中式家常','增肌友好','1人份；鸡胸和米饭按熟重、黑豆按沥干重、蔬菜按生重','1. 黑豆充分沥干并加热。\n2. 鸡胸切片，卷心菜焖熟。\n3. 与糙米饭混合搭配；调味料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,124),
('sys_cn_beef_mushroom_millet_rice','牛肉香菇小米饭','香菇牛肉饭,牛肉杂粮饭','中式晚餐','中式家常','增肌友好','1人份；牛肉按熟重、小米按干重、蔬菜按生重','1. 小米加水煮成小米饭。\n2. 香菇与小白菜焖熟，加入牛肉片加热。\n3. 搭配食用；烹调油和酱料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,123),
('sys_cn_tofu_egg_brown_rice','豆腐鸡蛋糙米饭','鸡蛋豆腐饭,家常豆腐套餐','中式晚餐','中式家常','增肌友好','1人份；豆腐和蛋按可食部、米饭按熟重、菠菜按生重','1. 豆腐切块蒸热。\n2. 鸡蛋煮熟，菠菜焯熟。\n3. 与糙米饭搭配；调味料另行记录。',0,0,0,0,'含大豆和蛋','PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,122),
('sys_cn_chicken_corn_potato_rice','鸡胸玉米土豆饭','鸡肉玉米饭,土豆鸡胸套餐','中式午餐','中式家常','增肌友好','1人份；鸡胸和米饭按熟重、玉米土豆按生重','1. 土豆和玉米蒸熟。\n2. 熟鸡胸切片加热。\n3. 与糙米饭搭配；额外用油和酱料另行记录。',0,0,0,0,NULL,'PROJECT_CALCULATED','按中国居民膳食指南原则编制；USDA FDC 食材数据按克重计算','system-recipes-2026.09-v3-zh',1,121)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`aliases`=VALUES(`aliases`),`category`=VALUES(`category`),
 `cuisine_type`=VALUES(`cuisine_type`),`goal_tags`=VALUES(`goal_tags`),`serving_description`=VALUES(`serving_description`),
 `steps`=VALUES(`steps`),`allergen_info`=VALUES(`allergen_info`),`source_name`=VALUES(`source_name`),
 `source_ref`=VALUES(`source_ref`),`data_version`=VALUES(`data_version`),`enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

DELETE sri FROM `system_recipe_ingredients` sri JOIN `system_recipe_templates` srt ON srt.id=sri.template_id
WHERE srt.data_version='system-recipes-2026.09-v3-zh';

INSERT INTO `system_recipe_ingredients`
(`template_id`,`food_name`,`weight`,`protein`,`carb`,`fat`,`calorie`,`source_name`,`source_ref`,`data_version`,`sort_order`)
SELECT t.id,x.food_name,x.weight,
 ROUND(f.protein*x.weight/100,2),ROUND(f.carb*x.weight/100,2),ROUND(f.fat*x.weight/100,2),ROUND(f.calorie*x.weight/100,2),
 f.source_name,f.source_ref,f.data_version,x.sort_order
FROM (
 SELECT 'sys_cn_millet_egg_spinach_congee' tid,'小米粒' food_name,40.00 weight,1 sort_order UNION ALL SELECT 'sys_cn_millet_egg_spinach_congee','鸡蛋清',150,2 UNION ALL SELECT 'sys_cn_millet_egg_spinach_congee','熟鸡蛋（水煮）',50,3 UNION ALL SELECT 'sys_cn_millet_egg_spinach_congee','嫩菠菜',100,4
 UNION ALL SELECT 'sys_cn_corn_shrimp_steamed_egg','生甜玉米粒',120,1 UNION ALL SELECT 'sys_cn_corn_shrimp_steamed_egg','生虾仁',150,2 UNION ALL SELECT 'sys_cn_corn_shrimp_steamed_egg','全鸡蛋',100,3 UNION ALL SELECT 'sys_cn_corn_shrimp_steamed_egg','生小白菜',150,4
 UNION ALL SELECT 'sys_cn_steamed_cod_brown_rice','生大西洋鳕鱼',180,1 UNION ALL SELECT 'sys_cn_steamed_cod_brown_rice','熟糙米饭',150,2 UNION ALL SELECT 'sys_cn_steamed_cod_brown_rice','生小白菜',200,3
 UNION ALL SELECT 'sys_cn_mushroom_chicken_rice','熟鸡胸肉',120,1 UNION ALL SELECT 'sys_cn_mushroom_chicken_rice','熟糙米饭',150,2 UNION ALL SELECT 'sys_cn_mushroom_chicken_rice','生香菇',150,3
 UNION ALL SELECT 'sys_cn_tomato_beef_potato_stew','熟牛里脊',130,1 UNION ALL SELECT 'sys_cn_tomato_beef_potato_stew','生去皮褐皮土豆',200,2 UNION ALL SELECT 'sys_cn_tomato_beef_potato_stew','生罗马番茄',200,3
 UNION ALL SELECT 'sys_cn_pepper_pork_brown_rice_light','生猪里脊',150,1 UNION ALL SELECT 'sys_cn_pepper_pork_brown_rice_light','熟糙米饭',180,2 UNION ALL SELECT 'sys_cn_pepper_pork_brown_rice_light','生红甜椒',150,3
 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_set','生虾仁',150,1 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_set','全鸡蛋',100,2 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_set','熟糙米饭',100,3 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_set','生小白菜',150,4
 UNION ALL SELECT 'sys_cn_chicken_millet_carrot_congee','熟鸡胸肉',100,1 UNION ALL SELECT 'sys_cn_chicken_millet_carrot_congee','小米粒',40,2 UNION ALL SELECT 'sys_cn_chicken_millet_carrot_congee','生胡萝卜',100,3 UNION ALL SELECT 'sys_cn_chicken_millet_carrot_congee','嫩菠菜',100,4
 UNION ALL SELECT 'sys_cn_cod_tomato_rice_soup','生大西洋鳕鱼',180,1 UNION ALL SELECT 'sys_cn_cod_tomato_rice_soup','熟糙米饭',150,2 UNION ALL SELECT 'sys_cn_cod_tomato_rice_soup','生罗马番茄',200,3 UNION ALL SELECT 'sys_cn_cod_tomato_rice_soup','生小白菜',150,4
 UNION ALL SELECT 'sys_cn_tofu_mushroom_greens_rice','北豆腐',150,1 UNION ALL SELECT 'sys_cn_tofu_mushroom_greens_rice','生香菇',150,2 UNION ALL SELECT 'sys_cn_tofu_mushroom_greens_rice','生小白菜',200,3 UNION ALL SELECT 'sys_cn_tofu_mushroom_greens_rice','熟糙米饭',100,4
 UNION ALL SELECT 'sys_cn_beef_potato_brown_rice','熟牛里脊',200,1 UNION ALL SELECT 'sys_cn_beef_potato_brown_rice','生去皮褐皮土豆',250,2 UNION ALL SELECT 'sys_cn_beef_potato_brown_rice','熟糙米饭',150,3 UNION ALL SELECT 'sys_cn_beef_potato_brown_rice','生胡萝卜',100,4
 UNION ALL SELECT 'sys_cn_large_mushroom_chicken_rice','熟鸡胸肉',200,1 UNION ALL SELECT 'sys_cn_large_mushroom_chicken_rice','熟糙米饭',250,2 UNION ALL SELECT 'sys_cn_large_mushroom_chicken_rice','生香菇',150,3
 UNION ALL SELECT 'sys_cn_tomato_egg_chicken_rice','熟鸡胸肉',150,1 UNION ALL SELECT 'sys_cn_tomato_egg_chicken_rice','全鸡蛋',100,2 UNION ALL SELECT 'sys_cn_tomato_egg_chicken_rice','生罗马番茄',200,3 UNION ALL SELECT 'sys_cn_tomato_egg_chicken_rice','熟糙米饭',200,4
 UNION ALL SELECT 'sys_cn_large_pepper_pork_rice','生猪里脊',220,1 UNION ALL SELECT 'sys_cn_large_pepper_pork_rice','熟糙米饭',250,2 UNION ALL SELECT 'sys_cn_large_pepper_pork_rice','生红甜椒',150,3
 UNION ALL SELECT 'sys_cn_salmon_steamed_egg_rice','熟三文鱼',180,1 UNION ALL SELECT 'sys_cn_salmon_steamed_egg_rice','全鸡蛋',100,2 UNION ALL SELECT 'sys_cn_salmon_steamed_egg_rice','熟糙米饭',200,3 UNION ALL SELECT 'sys_cn_salmon_steamed_egg_rice','生小白菜',100,4
 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_stew','生虾仁',200,1 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_stew','全鸡蛋',100,2 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_stew','熟糙米饭',300,3 UNION ALL SELECT 'sys_cn_shrimp_egg_brown_rice_stew','生红甜椒',100,4
 UNION ALL SELECT 'sys_cn_chicken_blackbean_grain_rice','熟鸡胸肉',180,1 UNION ALL SELECT 'sys_cn_chicken_blackbean_grain_rice','黑豆罐头',150,2 UNION ALL SELECT 'sys_cn_chicken_blackbean_grain_rice','熟糙米饭',250,3 UNION ALL SELECT 'sys_cn_chicken_blackbean_grain_rice','生绿甘蓝',150,4
 UNION ALL SELECT 'sys_cn_beef_mushroom_millet_rice','熟牛里脊',180,1 UNION ALL SELECT 'sys_cn_beef_mushroom_millet_rice','小米粒',70,2 UNION ALL SELECT 'sys_cn_beef_mushroom_millet_rice','生香菇',150,3 UNION ALL SELECT 'sys_cn_beef_mushroom_millet_rice','生小白菜',150,4
 UNION ALL SELECT 'sys_cn_tofu_egg_brown_rice','北豆腐',200,1 UNION ALL SELECT 'sys_cn_tofu_egg_brown_rice','全鸡蛋',100,2 UNION ALL SELECT 'sys_cn_tofu_egg_brown_rice','熟糙米饭',250,3 UNION ALL SELECT 'sys_cn_tofu_egg_brown_rice','嫩菠菜',100,4
 UNION ALL SELECT 'sys_cn_chicken_corn_potato_rice','熟鸡胸肉',200,1 UNION ALL SELECT 'sys_cn_chicken_corn_potato_rice','生甜玉米粒',150,2 UNION ALL SELECT 'sys_cn_chicken_corn_potato_rice','生去皮褐皮土豆',250,3 UNION ALL SELECT 'sys_cn_chicken_corn_potato_rice','熟糙米饭',100,4
) x JOIN `system_recipe_templates` t ON t.template_id=x.tid JOIN `food_database` f ON f.food_name=x.food_name;

UPDATE `system_recipe_templates` t JOIN (
 SELECT template_id,ROUND(SUM(protein),2) protein,ROUND(SUM(carb),2) carb,
        ROUND(SUM(fat),2) fat,ROUND(SUM(calorie),2) calorie
 FROM `system_recipe_ingredients` GROUP BY template_id
) n ON n.template_id=t.id
SET t.protein=n.protein,t.carb=n.carb,t.fat=n.fat,t.calorie=n.calorie
WHERE t.data_version='system-recipes-2026.09-v3-zh';

SELECT cuisine_type,COUNT(*) AS template_count
FROM system_recipe_templates WHERE enabled=1 GROUP BY cuisine_type ORDER BY cuisine_type;
