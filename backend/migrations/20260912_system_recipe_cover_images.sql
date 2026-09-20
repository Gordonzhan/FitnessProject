-- R1 中式系统菜谱封面图：20 张 AI 生成图片已压缩并上传 OSS。
-- 依赖：20260911_chinese_system_recipe_templates.sql。可重复执行。
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
  WHEN 'sys_cn_beef_mushroom_millet_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/1a01f506-665f-4d80-af7f-41809a3ea2c3.jpeg'
  WHEN 'sys_cn_beef_potato_brown_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/9e61d683-eae3-4042-a91b-36f9d0cc2a51.jpeg'
  WHEN 'sys_cn_chicken_blackbean_grain_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/90f8c5fe-d408-4bb6-b5e8-e2076a63f5f5.jpeg'
  WHEN 'sys_cn_chicken_corn_potato_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/be0187b1-53d2-4d1d-8a21-cb56e55cd699.jpeg'
  WHEN 'sys_cn_chicken_millet_carrot_congee' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/1a09aeea-6215-4994-8a4b-64504cc8a4f9.jpeg'
  WHEN 'sys_cn_cod_tomato_rice_soup' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/ca838bfc-832b-4c1a-b3e2-37c0d1483e40.jpeg'
  WHEN 'sys_cn_corn_shrimp_steamed_egg' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/e735c0f0-873a-4a51-826d-fe3fcd707ddc.jpeg'
  WHEN 'sys_cn_large_mushroom_chicken_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/8ec1becb-5530-40ce-aea6-eb1f91895522.jpeg'
  WHEN 'sys_cn_large_pepper_pork_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/ddf7d191-3ba0-4fd9-b354-863fd77d0814.jpeg'
  WHEN 'sys_cn_millet_egg_spinach_congee' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/763881b4-03d2-44fd-88a6-f48818460093.jpeg'
  WHEN 'sys_cn_mushroom_chicken_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/752e69ff-74c9-4d6b-967b-dedf8b18d873.jpeg'
  WHEN 'sys_cn_pepper_pork_brown_rice_light' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/cbf73c53-0a14-43a9-8efe-08af052a73dc.jpeg'
  WHEN 'sys_cn_salmon_steamed_egg_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/99c7b5c5-f19a-4a48-987f-6f1cc99e51e3.jpeg'
  WHEN 'sys_cn_shrimp_egg_brown_rice_set' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/b5f7063e-60ea-4703-ba9b-c103c67f0bd4.jpeg'
  WHEN 'sys_cn_shrimp_egg_brown_rice_stew' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/0f0bc8b9-490a-4808-9fbc-61bcfd74842e.jpeg'
  WHEN 'sys_cn_steamed_cod_brown_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/1218bfbc-066a-49b7-a3ed-27dc2457a74b.jpeg'
  WHEN 'sys_cn_tofu_egg_brown_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/d6e0414f-c57e-48e7-b921-d3ebb1163962.jpeg'
  WHEN 'sys_cn_tofu_mushroom_greens_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/234ffa26-d55d-4bcf-a161-918b6269df91.jpeg'
  WHEN 'sys_cn_tomato_beef_potato_stew' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/2d8634ae-0477-4163-825e-a185def40765.jpeg'
  WHEN 'sys_cn_tomato_egg_chicken_rice' THEN 'https://gordonbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/0b7bc82d-4eba-4428-b51a-eb857d2eda40.jpeg'
  ELSE `cover_image_url`
END
WHERE `data_version`='system-recipes-2026.09-v3-zh';

SELECT COUNT(*) AS chinese_templates_with_cover
FROM `system_recipe_templates`
WHERE `data_version`='system-recipes-2026.09-v3-zh' AND `cover_image_url` IS NOT NULL;
