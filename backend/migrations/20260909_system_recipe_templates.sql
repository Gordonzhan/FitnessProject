-- R1 系统菜谱模板库（MySQL 5.7，可重复执行）。
-- 模板配方由项目维护；营养值 = 各食材每100g数据 × 本模板克重 / 100。
USE `fitness_diary`;

CREATE TABLE IF NOT EXISTS `system_recipe_templates` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` VARCHAR(50) NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `aliases` VARCHAR(255) NULL,
  `category` VARCHAR(30) NOT NULL,
  `goal_tags` VARCHAR(100) NULL,
  `serving_description` VARCHAR(100) NOT NULL,
  `steps` TEXT NOT NULL,
  `protein` DECIMAL(10,2) NOT NULL,
  `carb` DECIMAL(10,2) NOT NULL,
  `fat` DECIMAL(10,2) NOT NULL,
  `calorie` DECIMAL(10,2) NOT NULL,
  `allergen_info` VARCHAR(255) NULL,
  `source_name` VARCHAR(50) NOT NULL,
  `source_ref` VARCHAR(255) NOT NULL,
  `data_version` VARCHAR(50) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_system_recipe_template_id` (`template_id`),
  KEY `idx_system_recipe_enabled_sort` (`enabled`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='只读系统菜谱模板';

CREATE TABLE IF NOT EXISTS `system_recipe_ingredients` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `food_name` VARCHAR(50) NOT NULL,
  `weight` DECIMAL(10,2) NOT NULL,
  `protein` DECIMAL(10,2) NOT NULL,
  `carb` DECIMAL(10,2) NOT NULL,
  `fat` DECIMAL(10,2) NOT NULL,
  `calorie` DECIMAL(10,2) NOT NULL,
  `source_name` VARCHAR(50) NOT NULL,
  `source_ref` VARCHAR(100) NOT NULL,
  `data_version` VARCHAR(50) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_system_recipe_ingredient_template` (`template_id`),
  CONSTRAINT `fk_system_recipe_ingredient_template` FOREIGN KEY (`template_id`)
    REFERENCES `system_recipe_templates` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜谱食材及来源快照';

-- 补充模板使用的“熟重”记录；另 6 项直接沿用 20260905 导入的 Foundation Foods。
INSERT INTO `food_database`
(`food_name`,`aliases`,`category`,`serving_state`,`protein`,`carb`,`fat`,`calorie`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('熟糙米饭','糙米饭,熟糙米','谷物主食','熟重',2.74,25.58,0.97,123.00,'USDA_FDC','FDC:169704','sr-legacy-retrieved-2026-09-05',1,70),
('熟藜麦','藜麦,煮藜麦','谷物主食','熟重',4.40,21.30,1.92,120.00,'USDA_FDC','FDC:168917','sr-legacy-retrieved-2026-09-05',1,70),
('烤红薯','红薯,地瓜,番薯','薯类','熟重',2.01,20.71,0.15,90.00,'USDA_FDC','FDC:168483','sr-legacy-retrieved-2026-09-05',1,70),
('熟三文鱼','三文鱼,鲑鱼','肉禽水产','熟重',22.10,0.00,12.35,206.00,'USDA_FDC','FDC:175168','sr-legacy-retrieved-2026-09-05',1,70),
('熟鸡蛋（水煮）','水煮蛋,鸡蛋','蛋奶豆制品','可食部',12.58,1.12,10.61,155.00,'USDA_FDC','FDC:173424','sr-legacy-retrieved-2026-09-05',1,70)
ON DUPLICATE KEY UPDATE `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),`serving_state`=VALUES(`serving_state`),
 `protein`=VALUES(`protein`),`carb`=VALUES(`carb`),`fat`=VALUES(`fat`),`calorie`=VALUES(`calorie`),
 `source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),`data_version`=VALUES(`data_version`),`enabled`=1;

INSERT INTO `system_recipe_templates`
(`template_id`,`name`,`aliases`,`category`,`goal_tags`,`serving_description`,`steps`,`protein`,`carb`,`fat`,`calorie`,`allergen_info`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('sys_oat_yogurt','高蛋白燕麦酸奶杯','酸奶燕麦,早餐杯','早餐','减脂,增肌,保持','1人份；食材均按标注状态称重','1. 燕麦片放入碗中。\n2. 加入脱脂希腊酸奶拌匀。\n3. 冷藏10分钟后食用。',26.00,34.76,3.10,273.60,'含乳制品；燕麦过敏者慎用','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,120),
('sys_spinach_egg_oat','菠菜鸡蛋燕麦碗','鸡蛋燕麦,咸燕麦','早餐','增肌,保持','1人份；菠菜按生重、鸡蛋按可食部','1. 燕麦加水煮熟。\n2. 菠菜焯熟，鸡蛋切块。\n3. 混合装碗，可按需加香辛料。',21.61,37.40,14.06,361.06,'含蛋；燕麦过敏者慎用','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,115),
('sys_sweet_egg_spinach','红薯鸡蛋菠菜盘','红薯餐,地瓜鸡蛋','早餐','减脂,保持','1人份；红薯熟重、菠菜生重','1. 红薯烤熟或蒸熟。\n2. 鸡蛋煮熟切开。\n3. 菠菜焯熟后一同装盘。',20.46,55.31,11.61,400.70,'含蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,110),
('sys_chicken_rice_broccoli','鸡胸糙米西兰花','鸡胸饭,健身餐','午餐','减脂,增肌,保持','1人份；鸡胸和米饭熟重、西兰花生重','1. 鸡胸肉无油煎熟或烤熟。\n2. 西兰花蒸熟。\n3. 与熟糙米饭装盘；若加油或酱料需另行记录。',56.94,55.45,7.12,516.90,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,105),
('sys_chicken_quinoa_spinach','鸡胸藜麦菠菜碗','鸡胸藜麦,能量碗','午餐','减脂,增肌','1人份；鸡胸和藜麦熟重、菠菜生重','1. 鸡胸肉烤熟后切片。\n2. 菠菜焯熟。\n3. 与熟藜麦混合装碗。',58.92,40.75,8.94,485.70,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,100),
('sys_salmon_rice_broccoli','三文鱼糙米西兰花','鲑鱼糙米,三文鱼饭','晚餐','增肌,保持','1人份；鱼和米饭熟重、西兰花生重','1. 三文鱼煎熟或烤熟。\n2. 西兰花蒸熟。\n3. 与熟糙米饭装盘；额外用油需另行记录。',41.94,55.45,20.79,576.90,'含鱼类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,95),
('sys_salmon_quinoa_spinach','三文鱼藜麦菠菜碗','鲑鱼藜麦,三文鱼能量碗','晚餐','增肌,保持','1人份；鱼和藜麦熟重、菠菜生重','1. 三文鱼烤熟。\n2. 菠菜焯熟。\n3. 与熟藜麦装碗。',43.92,40.75,22.61,545.70,'含鱼类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,90),
('sys_chicken_sweet_broccoli','鸡胸红薯西兰花','鸡胸地瓜,减脂餐','午餐','减脂,增肌','1人份；鸡胸和红薯熟重、西兰花生重','1. 鸡胸肉烤熟后切片。\n2. 红薯烤熟，西兰花蒸熟。\n3. 一同装盘。',57.04,61.19,5.75,520.50,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,85),
('sys_egg_quinoa_broccoli','鸡蛋藜麦西兰花碗','鸡蛋藜麦,素食能量碗','午餐','保持','1人份；鸡蛋可食部、藜麦熟重、西兰花生重','1. 鸡蛋煮熟切块。\n2. 西兰花蒸熟。\n3. 与熟藜麦混合装碗。',25.24,53.13,14.96,441.50,'含蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,80),
('sys_large_chicken_rice','加量鸡胸糙米菠菜','高蛋白鸡胸饭','午餐','增肌','1人份；鸡胸和米饭熟重、菠菜生重','1. 鸡胸肉烤熟切片。\n2. 菠菜焯熟。\n3. 与熟糙米饭装盘。',66.11,53.57,8.39,565.50,NULL,'PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,75),
('sys_salmon_sweet_spinach','三文鱼红薯菠菜','鲑鱼红薯,三文鱼地瓜','晚餐','保持','1人份；鱼和红薯熟重、菠菜生重','1. 三文鱼烤熟。\n2. 红薯烤熟，菠菜焯熟。\n3. 一同装盘。',41.03,54.19,19.53,554.70,'含鱼类','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,70),
('sys_quinoa_egg_spinach','藜麦鸡蛋菠菜碗','鸡蛋菠菜藜麦','晚餐','减脂,保持','1人份；鸡蛋可食部、藜麦熟重、菠菜生重','1. 鸡蛋煮熟切块。\n2. 菠菜焯熟。\n3. 与熟藜麦混合装碗。',25.11,50.39,15.45,439.70,'含蛋','PROJECT_CALCULATED','USDA FoodData Central 食材数据按克重计算','system-recipes-2026.09-v1',1,65)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`aliases`=VALUES(`aliases`),`category`=VALUES(`category`),
 `goal_tags`=VALUES(`goal_tags`),`serving_description`=VALUES(`serving_description`),`steps`=VALUES(`steps`),
 `protein`=VALUES(`protein`),`carb`=VALUES(`carb`),`fat`=VALUES(`fat`),`calorie`=VALUES(`calorie`),
 `allergen_info`=VALUES(`allergen_info`),`source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),
 `data_version`=VALUES(`data_version`),`enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

DELETE sri FROM `system_recipe_ingredients` sri JOIN `system_recipe_templates` srt ON srt.id=sri.template_id
WHERE srt.data_version='system-recipes-2026.09-v1';

INSERT INTO `system_recipe_ingredients`
(`template_id`,`food_name`,`weight`,`protein`,`carb`,`fat`,`calorie`,`source_name`,`source_ref`,`data_version`,`sort_order`)
SELECT t.id, x.food_name, x.weight,
 ROUND(f.protein*x.weight/100,2), ROUND(f.carb*x.weight/100,2), ROUND(f.fat*x.weight/100,2), ROUND(f.calorie*x.weight/100,2),
 f.source_name, f.source_ref, f.data_version, x.sort_order
FROM (
 SELECT 'sys_oat_yogurt' tid,'传统燕麦片' food_name,40.00 weight,1 sort_order UNION ALL SELECT 'sys_oat_yogurt','脱脂希腊酸奶',200,2
 UNION ALL SELECT 'sys_spinach_egg_oat','传统燕麦片',50,1 UNION ALL SELECT 'sys_spinach_egg_oat','熟鸡蛋（水煮）',100,2 UNION ALL SELECT 'sys_spinach_egg_oat','嫩菠菜',80,3
 UNION ALL SELECT 'sys_sweet_egg_spinach','烤红薯',250,1 UNION ALL SELECT 'sys_sweet_egg_spinach','熟鸡蛋（水煮）',100,2 UNION ALL SELECT 'sys_sweet_egg_spinach','嫩菠菜',100,3
 UNION ALL SELECT 'sys_chicken_rice_broccoli','熟鸡胸肉',150,1 UNION ALL SELECT 'sys_chicken_rice_broccoli','熟糙米饭',180,2 UNION ALL SELECT 'sys_chicken_rice_broccoli','生西兰花',150,3
 UNION ALL SELECT 'sys_chicken_quinoa_spinach','熟鸡胸肉',150,1 UNION ALL SELECT 'sys_chicken_quinoa_spinach','熟藜麦',180,2 UNION ALL SELECT 'sys_chicken_quinoa_spinach','嫩菠菜',100,3
 UNION ALL SELECT 'sys_salmon_rice_broccoli','熟三文鱼',150,1 UNION ALL SELECT 'sys_salmon_rice_broccoli','熟糙米饭',180,2 UNION ALL SELECT 'sys_salmon_rice_broccoli','生西兰花',150,3
 UNION ALL SELECT 'sys_salmon_quinoa_spinach','熟三文鱼',150,1 UNION ALL SELECT 'sys_salmon_quinoa_spinach','熟藜麦',180,2 UNION ALL SELECT 'sys_salmon_quinoa_spinach','嫩菠菜',100,3
 UNION ALL SELECT 'sys_chicken_sweet_broccoli','熟鸡胸肉',150,1 UNION ALL SELECT 'sys_chicken_sweet_broccoli','烤红薯',250,2 UNION ALL SELECT 'sys_chicken_sweet_broccoli','生西兰花',150,3
 UNION ALL SELECT 'sys_egg_quinoa_broccoli','熟鸡蛋（水煮）',100,1 UNION ALL SELECT 'sys_egg_quinoa_broccoli','熟藜麦',200,2 UNION ALL SELECT 'sys_egg_quinoa_broccoli','生西兰花',150,3
 UNION ALL SELECT 'sys_large_chicken_rice','熟鸡胸肉',180,1 UNION ALL SELECT 'sys_large_chicken_rice','熟糙米饭',200,2 UNION ALL SELECT 'sys_large_chicken_rice','嫩菠菜',100,3
 UNION ALL SELECT 'sys_salmon_sweet_spinach','熟三文鱼',150,1 UNION ALL SELECT 'sys_salmon_sweet_spinach','烤红薯',250,2 UNION ALL SELECT 'sys_salmon_sweet_spinach','嫩菠菜',100,3
 UNION ALL SELECT 'sys_quinoa_egg_spinach','熟藜麦',220,1 UNION ALL SELECT 'sys_quinoa_egg_spinach','熟鸡蛋（水煮）',100,2 UNION ALL SELECT 'sys_quinoa_egg_spinach','嫩菠菜',100,3
) x JOIN `system_recipe_templates` t ON t.template_id=x.tid JOIN `food_database` f ON f.food_name=x.food_name;

SELECT data_version, COUNT(*) AS template_count FROM system_recipe_templates GROUP BY data_version;
