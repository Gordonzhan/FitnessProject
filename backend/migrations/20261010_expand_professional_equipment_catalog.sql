-- A4 验收优化：扩充专业健身房器械动作，器械标签使用通用名称而非品牌型号。
-- 器械类型和用途参考 Technogym Selection、Pure Strength 与 Cable Stations 官方目录；
-- 动作说明、限制标签和 MET 为项目整理值，仍需由专业人员复核。
USE `fitness_diary`;

INSERT INTO `exercise_database`
(`exercise_code`,`name`,`aliases`,`category`,`primary_muscles`,`equipment`,`intensity`,`met`,
 `source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('史密斯深蹲',256),1,16))),'史密斯深蹲','史密斯机深蹲','力量训练','股四头肌,臀部,核心','史密斯机','中等',5.0,'PROJECT_CURATED','generic guided barbell equipment','professional-equipment-v1',1,92),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('史密斯卧推',256),1,16))),'史密斯卧推','史密斯机卧推','力量训练','胸部,肱三头肌,肩部','史密斯机','中等',5.0,'PROJECT_CURATED','generic guided barbell equipment','professional-equipment-v1',1,90),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('史密斯肩推',256),1,16))),'史密斯肩推','史密斯机肩推','力量训练','肩部,肱三头肌','史密斯机','中等',5.0,'PROJECT_CURATED','generic guided barbell equipment','professional-equipment-v1',1,84),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('器械推胸',256),1,16))),'器械推胸','坐姿推胸,胸部推举器','力量训练','胸部,肱三头肌,肩部','推胸机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,90),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('器械肩推',256),1,16))),'器械肩推','坐姿肩推,肩部推举器','力量训练','肩部,肱三头肌','肩推机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,88),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('器械高位下拉',256),1,16))),'器械高位下拉','垂直牵引器,器械下拉','力量训练','背部,肱二头肌','高位下拉机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,90),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('器械坐姿划船',256),1,16))),'器械坐姿划船','低位划船器,器械划船','力量训练','背部,肱二头肌,肩后束','坐姿划船机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/pure-strength/','professional-equipment-v1',1,88),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('蝴蝶机夹胸',256),1,16))),'蝴蝶机夹胸','器械飞鸟,胸肌训练器','力量训练','胸部','蝴蝶机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,84),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('反向蝴蝶机',256),1,16))),'反向蝴蝶机','反向飞鸟机,后束训练器','力量训练','肩后束,上背部','蝴蝶机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,82),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('辅助引体向上',256),1,16))),'辅助引体向上','配重辅助引体','力量训练','背部,肱二头肌','辅助引体双杠机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,88),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('辅助双杠臂屈伸',256),1,16))),'辅助双杠臂屈伸','配重辅助双杠','力量训练','胸部,肱三头肌','辅助引体双杠机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,84),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('哈克深蹲',256),1,16))),'哈克深蹲','器械哈克蹲','力量训练','股四头肌,臀部','哈克深蹲机','高',6.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/pure-strength/','professional-equipment-v1',1,88),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('髋外展',256),1,16))),'髋外展','坐姿髋外展,大腿外展','力量训练','臀中肌,大腿外侧','髋外展/内收机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,78),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('髋内收',256),1,16))),'髋内收','坐姿髋内收,大腿内收','力量训练','大腿内侧','髋外展/内收机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,78),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('坐姿提踵',256),1,16))),'坐姿提踵','器械提踵','力量训练','小腿','提踵机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/pure-strength/','professional-equipment-v1',1,76),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('器械背伸',256),1,16))),'器械背伸','下背训练器,器械挺身','力量训练','下背部,臀部','背伸机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,74),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('器械卷腹',256),1,16))),'器械卷腹','腹肌训练器,坐姿卷腹','核心训练','腹部,核心','腹肌训练机','中等',3.8,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/selection-line/','professional-equipment-v1',1,76),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('臀部后踢',256),1,16))),'臀部后踢','器械后踢,臀肌训练器','力量训练','臀部,腘绳肌','臀部训练机','中等',5.0,'PROJECT_CURATED','https://www.technogym.cn/zh-CN/product/pure-rear-kick_MG4000-NBGJV0.html','professional-equipment-v1',1,80),
(CONCAT('EX_',UPPER(SUBSTRING(SHA2('多功能器械循环',256),1,16))),'多功能器械循环','多站综合训练','功能性训练','全身','多功能训练器','中等',5.0,'PROJECT_CURATED','https://www.technogym.com/en-US/product/unica_M310.html','professional-equipment-v1',1,72)
ON DUPLICATE KEY UPDATE
  `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),`primary_muscles`=VALUES(`primary_muscles`),
  `equipment`=VALUES(`equipment`),`intensity`=VALUES(`intensity`),`met`=VALUES(`met`),
  `source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),`data_version`=VALUES(`data_version`),
  `enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

UPDATE `exercise_database` SET
  `instructions` = CASE
    WHEN `equipment` = '史密斯机' THEN '先设置安全挂钩和限位，调整身体与杠铃轨迹，再以可控速度完成动作。'
    WHEN `equipment` = '辅助引体双杠机' THEN '选择能够稳定完成动作的辅助重量，保持躯干稳定并控制完整活动范围。'
    ELSE '按关节轴线调整座椅、靠垫和起始位置，选择可控负荷，平稳完成向心和离心阶段。'
  END,
  `safety_cues` = '先用轻负荷确认设备设置和活动范围；关节出现锐痛、麻木或明显不适时立即停止。',
  `common_mistakes` = '座椅或限位设置不当、借助惯性、关节锁死、负荷过大导致动作范围缩短。',
  `limitation_tags` = CASE
    WHEN `name` IN ('史密斯深蹲','哈克深蹲') THEN 'KNEE,LOWER_BACK,BALANCE'
    WHEN `name` IN ('器械推胸','史密斯卧推','器械肩推','史密斯肩推','蝴蝶机夹胸','反向蝴蝶机','辅助双杠臂屈伸') THEN 'SHOULDER,WRIST'
    WHEN `name` IN ('器械高位下拉','器械坐姿划船','辅助引体向上','器械背伸') THEN 'LOWER_BACK,SHOULDER'
    WHEN `name` IN ('髋外展','髋内收','坐姿提踵','臀部后踢') THEN 'KNEE,LOWER_BACK,BALANCE'
    ELSE NULL
  END,
  `media_type` = 'NONE', `default_sets` = 3, `default_reps` = 10,
  `default_duration_seconds` = NULL, `default_rest_seconds` = 60,
  `default_rpe` = 7, `tempo` = '2-0-2', `follow_along_enabled` = 1
WHERE `data_version` = 'professional-equipment-v1';

SET @a4_squat = (SELECT `exercise_code` FROM `exercise_database` WHERE `name`='深蹲' LIMIT 1);
SET @a4_bench = (SELECT `exercise_code` FROM `exercise_database` WHERE `name`='卧推' LIMIT 1);
SET @a4_press = (SELECT `exercise_code` FROM `exercise_database` WHERE `name`='肩上推举' LIMIT 1);
SET @a4_pullup = (SELECT `exercise_code` FROM `exercise_database` WHERE `name`='引体向上' LIMIT 1);
SET @a4_dip = (SELECT `exercise_code` FROM `exercise_database` WHERE `name`='双杠臂屈伸' LIMIT 1);
SET @a4_crunch = (SELECT `exercise_code` FROM `exercise_database` WHERE `name`='卷腹' LIMIT 1);
UPDATE `exercise_database` SET `alternative_exercise_codes`=@a4_squat WHERE `name` IN ('史密斯深蹲','哈克深蹲') AND @a4_squat IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes`=@a4_bench WHERE `name` IN ('史密斯卧推','器械推胸') AND @a4_bench IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes`=@a4_press WHERE `name` IN ('史密斯肩推','器械肩推') AND @a4_press IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes`=@a4_pullup WHERE `name`='辅助引体向上' AND @a4_pullup IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes`=@a4_dip WHERE `name`='辅助双杠臂屈伸' AND @a4_dip IS NOT NULL;
UPDATE `exercise_database` SET `alternative_exercise_codes`=@a4_crunch WHERE `name`='器械卷腹' AND @a4_crunch IS NOT NULL;

SELECT COUNT(*) AS professional_equipment_exercise_count
FROM `exercise_database` WHERE `data_version`='professional-equipment-v1' AND `enabled`=1;
