-- 仅限本地/测试环境：固定规则生成 15 个合成人物最近 30 天训练样本，共 450 行。
-- 不写 users/workouts；重复执行会更新同一数据集，不会增加参与者。
USE `fitness_diary`;

INSERT INTO `comparison_daily_samples`
  (`participant_key`, `masked_display_name`, `sample_date`, `goal`, `completed_sessions`, `planned_sessions`,
   `cancelled_sessions`, `actual_calories`, `source`, `dataset_version`)
SELECT CONCAT('r7-synthetic-', LPAD(person.n, 2, '0')),
       ELT(person.n, 'G****n', 'A***e', 'B*b', 'C***e', 'D***d', 'E**a', 'F***k', 'G***e',
           'H***y', 'I**s', 'J**k', 'K***n', 'L***a', 'M***n', 'N**a'),
       DATE_SUB(CURDATE(), INTERVAL day_offset.n DAY),
       CASE MOD(person.n - 1, 3)
         WHEN 0 THEN 'lose_weight'
         WHEN 1 THEN 'maintain'
         ELSE 'gain_muscle'
       END,
       CASE WHEN MOD(day_offset.n + person.n * 2, 7) < (2 + MOD(person.n, 5)) THEN 1 ELSE 0 END,
       CASE WHEN MOD(day_offset.n + person.n, 11) = 0 THEN 1 ELSE 0 END,
       CASE WHEN MOD(day_offset.n + person.n * 3, 17) = 0 THEN 1 ELSE 0 END,
       CASE WHEN MOD(day_offset.n + person.n * 2, 7) < (2 + MOD(person.n, 5))
            THEN 150 + person.n * 12 + MOD(day_offset.n, 5) * 18 ELSE 0 END,
       'SYNTHETIC',
       'R7_DEMO_V1'
FROM (
  SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
) person
CROSS JOIN (
  SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
  UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
  UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
  UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
  UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29
) day_offset
WHERE TRUE
ON DUPLICATE KEY UPDATE
  `masked_display_name` = VALUES(`masked_display_name`),
  `goal` = VALUES(`goal`),
  `completed_sessions` = VALUES(`completed_sessions`),
  `planned_sessions` = VALUES(`planned_sessions`),
  `cancelled_sessions` = VALUES(`cancelled_sessions`),
  `actual_calories` = VALUES(`actual_calories`),
  `source` = VALUES(`source`);

SELECT COUNT(*) AS `rows`, COUNT(DISTINCT `participant_key`) AS `participants`,
       MIN(`sample_date`) AS `start_date`, MAX(`sample_date`) AS `end_date`
FROM `comparison_daily_samples`
WHERE `dataset_version` = 'R7_DEMO_V1' AND `source` = 'SYNTHETIC';
