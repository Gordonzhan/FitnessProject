-- R1 其余系统菜谱封面图：v1/v2 共 30 张 AI 生成图片已压缩并上传 OSS。
-- 依赖：20260909_system_recipe_templates.sql、20260910_expand_system_recipe_templates.sql。可重复执行。
USE `fitness_diary`;

SET @cover_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='system_recipe_templates' AND COLUMN_NAME='cover_image_url'
);
SET @add_cover_sql = IF(@cover_column_exists=0,
  'ALTER TABLE system_recipe_templates ADD COLUMN cover_image_url VARCHAR(500) NULL AFTER cuisine_type',
  'SELECT 1');
PREPARE add_cover_stmt FROM @add_cover_sql;
EXECUTE add_cover_stmt;
DEALLOCATE PREPARE add_cover_stmt;

UPDATE `system_recipe_templates`
SET `cover_image_url` = CASE `template_id`
  WHEN 'sys_oat_yogurt' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/7e3effb0-1c9b-4e49-92c3-72903c0cd6f2.jpeg'
  WHEN 'sys_spinach_egg_oat' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/57e674bc-a14b-4923-9c09-e0d545a16864.jpeg'
  WHEN 'sys_sweet_egg_spinach' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/27317e84-2cdf-4cd1-afcd-3fd8cbb62b26.jpeg'
  WHEN 'sys_chicken_rice_broccoli' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/36f427d0-a028-4915-9368-424bdb06d002.jpeg'
  WHEN 'sys_chicken_quinoa_spinach' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/56fa7d05-4b7d-43f1-b214-547d869f002d.jpeg'
  WHEN 'sys_salmon_rice_broccoli' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/1fafa5c8-30ef-49b1-81ff-86f35c168d73.jpeg'
  WHEN 'sys_salmon_quinoa_spinach' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/3af9bacb-a989-40b7-aca8-7b0b3d175fd5.jpeg'
  WHEN 'sys_chicken_sweet_broccoli' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/171a1eab-8654-4b15-b601-09f091f98325.jpeg'
  WHEN 'sys_egg_quinoa_broccoli' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/66aab0e0-6851-44a2-aa6c-a6a66bd20665.jpeg'
  WHEN 'sys_large_chicken_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/99ce6c7d-453e-4c65-9bd6-fb45b84cc86b.jpeg'
  WHEN 'sys_salmon_sweet_spinach' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/16b3e751-d85d-410a-b7c7-23807f67cb1b.jpeg'
  WHEN 'sys_quinoa_egg_spinach' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/4889e3ae-0d53-4266-916f-26aaba4d6f15.jpeg'
  WHEN 'sys_cod_sweet_broccoli' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/5b6aa520-6754-4bc5-aee6-476405e3d1a9.jpeg'
  WHEN 'sys_shrimp_quinoa_pepper' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/b9c64977-9ade-48a7-b701-044be61a48af.jpeg'
  WHEN 'sys_tuna_chickpea_cucumber' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/790b3edc-fa18-4bf5-8a62-b204b22b6c55.jpeg'
  WHEN 'sys_eggwhite_oat_spinach' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/a2a01a66-12e7-434c-a8c1-e64c5da28eb3.jpeg'
  WHEN 'sys_chicken_potato_carrot' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/aa9b7e6e-76b8-4986-9389-78246a6d76d3.jpeg'
  WHEN 'sys_scallop_quinoa_zucchini' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/4391a090-f6db-4bbd-8ae6-e3fe1796f37f.jpeg'
  WHEN 'sys_yogurt_strawberry_oat' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/c17bd131-f133-4e5f-8888-497826dcffd5.jpeg'
  WHEN 'sys_blackbean_egg_veg' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/3872d213-67e9-48d8-9680-c2dcc67bf9ec.jpeg'
  WHEN 'sys_chicken_quinoa_cabbage' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/5ecfab46-03b1-4a1c-9fe0-b71a1b222f78.jpeg'
  WHEN 'sys_beef_rice_broccoli' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/e5dd04cf-20eb-49a8-9e5f-506d8f66a848.jpeg'
  WHEN 'sys_double_chicken_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/981f4737-1078-4b5e-bbe1-ebb2938a5f00.jpeg'
  WHEN 'sys_salmon_quinoa_avocado' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/c860f532-ad0f-45d9-9fb8-e39b1a850ef4.jpeg'
  WHEN 'sys_pork_rice_pepper' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/0dd631e7-c45d-4618-8106-d86d62d5a3ea.jpeg'
  WHEN 'sys_chicken_chickpea_quinoa' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/dc344772-fb74-46ce-862c-705a8ac0af32.jpeg'
  WHEN 'sys_salmon_sweet_egg' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/fa86f71a-bc41-438c-a7a2-b9a522df6221.jpeg'
  WHEN 'sys_tuna_quinoa_egg_avocado' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/70d7db24-07cb-4266-85c8-70a6987f49d1.jpeg'
  WHEN 'sys_shrimp_rice_egg' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/7fefcdb5-16a4-4485-ba74-762b031d7617.jpeg'
  WHEN 'sys_yogurt_oat_banana_peanut' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/4612ea81-eb0c-40b8-bfda-05980448b7ef.jpeg'
  ELSE `cover_image_url`
END
WHERE `data_version` IN ('system-recipes-2026.09-v1', 'system-recipes-2026.09-v2')
  AND `template_id` IN (
    'sys_oat_yogurt',
    'sys_spinach_egg_oat',
    'sys_sweet_egg_spinach',
    'sys_chicken_rice_broccoli',
    'sys_chicken_quinoa_spinach',
    'sys_salmon_rice_broccoli',
    'sys_salmon_quinoa_spinach',
    'sys_chicken_sweet_broccoli',
    'sys_egg_quinoa_broccoli',
    'sys_large_chicken_rice',
    'sys_salmon_sweet_spinach',
    'sys_quinoa_egg_spinach',
    'sys_cod_sweet_broccoli',
    'sys_shrimp_quinoa_pepper',
    'sys_tuna_chickpea_cucumber',
    'sys_eggwhite_oat_spinach',
    'sys_chicken_potato_carrot',
    'sys_scallop_quinoa_zucchini',
    'sys_yogurt_strawberry_oat',
    'sys_blackbean_egg_veg',
    'sys_chicken_quinoa_cabbage',
    'sys_beef_rice_broccoli',
    'sys_double_chicken_rice',
    'sys_salmon_quinoa_avocado',
    'sys_pork_rice_pepper',
    'sys_chicken_chickpea_quinoa',
    'sys_salmon_sweet_egg',
    'sys_tuna_quinoa_egg_avocado',
    'sys_shrimp_rice_egg',
    'sys_yogurt_oat_banana_peanut'
  );

SELECT `data_version`, COUNT(*) AS templates_with_cover
FROM `system_recipe_templates`
WHERE `enabled`=1 AND `cover_image_url` IS NOT NULL
GROUP BY `data_version`
ORDER BY `data_version`;

SELECT
  COUNT(*) AS enabled_templates,
  SUM(`cover_image_url` IS NOT NULL AND `cover_image_url` <> '') AS templates_with_cover,
  COUNT(DISTINCT NULLIF(`cover_image_url`, '')) AS distinct_cover_images
FROM `system_recipe_templates`
WHERE `enabled`=1;
