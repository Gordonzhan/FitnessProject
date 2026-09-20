-- 清理 R7 本地合成样本；不会删除任何真实用户、训练或匿名正式样本。
USE `fitness_diary`;

DELETE FROM `comparison_daily_samples`
WHERE `dataset_version` = 'R7_DEMO_V1' AND `source` = 'SYNTHETIC';

SELECT ROW_COUNT() AS `deleted_synthetic_rows`;
