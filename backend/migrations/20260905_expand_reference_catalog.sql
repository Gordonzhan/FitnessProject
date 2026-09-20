-- 第六项基础数据库升级（MySQL 5.7）。仅执行一次，不会删除已有业务数据。
USE `fitness_diary`;

ALTER TABLE `food_database`
  ADD COLUMN `aliases` VARCHAR(255) NULL AFTER `food_name`,
  ADD COLUMN `category` VARCHAR(30) NOT NULL DEFAULT '其他' AFTER `aliases`,
  ADD COLUMN `serving_state` VARCHAR(20) NOT NULL DEFAULT '可食部' AFTER `category`,
  ADD COLUMN `source_name` VARCHAR(50) NOT NULL DEFAULT 'PROJECT_CURATED' AFTER `calorie`,
  ADD COLUMN `source_ref` VARCHAR(100) NULL AFTER `source_name`,
  ADD COLUMN `data_version` VARCHAR(30) NOT NULL DEFAULT 'starter-v1' AFTER `source_ref`,
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 AFTER `data_version`,
  ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 AFTER `enabled`,
  ADD INDEX `idx_food_enabled_sort` (`enabled`, `sort_order`);

ALTER TABLE `exercise_database`
  ADD COLUMN `aliases` VARCHAR(255) NULL AFTER `name`,
  ADD COLUMN `category` VARCHAR(30) NOT NULL DEFAULT '其他' AFTER `aliases`,
  ADD COLUMN `primary_muscles` VARCHAR(100) NOT NULL DEFAULT '全身' AFTER `category`,
  ADD COLUMN `equipment` VARCHAR(50) NOT NULL DEFAULT '无' AFTER `primary_muscles`,
  ADD COLUMN `intensity` VARCHAR(20) NOT NULL DEFAULT '中等' AFTER `equipment`,
  ADD COLUMN `source_name` VARCHAR(50) NOT NULL DEFAULT 'PROJECT_CURATED' AFTER `met`,
  ADD COLUMN `source_ref` VARCHAR(100) NULL AFTER `source_name`,
  ADD COLUMN `data_version` VARCHAR(30) NOT NULL DEFAULT 'starter-v1' AFTER `source_ref`,
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 AFTER `data_version`,
  ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 AFTER `enabled`,
  ADD INDEX `idx_exercise_enabled_sort` (`enabled`, `sort_order`);

UPDATE `food_database` SET
  `category` = CASE
    WHEN `food_name` IN ('鸡胸肉','牛肉','猪肉','鱼肉','虾') THEN '肉禽水产'
    WHEN `food_name` IN ('鸡蛋','牛奶','豆腐') THEN '蛋奶豆制品'
    WHEN `food_name` IN ('米饭','面包','面条','土豆') THEN '主食薯类'
    WHEN `food_name` IN ('苹果','香蕉','橙子') THEN '水果'
    ELSE '蔬菜'
  END,
  `aliases` = CASE
    WHEN `food_name` = '西红柿' THEN '番茄'
    WHEN `food_name` = '米饭' THEN '白米饭,熟米饭'
    WHEN `food_name` = '鸡胸肉' THEN '鸡胸,鸡脯肉'
    ELSE NULL
  END,
  `sort_order` = 100,
  `data_version` = 'starter-v2';

-- 每100克可食部的入门扩展数据。上线前应由营养专业人员复核并替换为获授权的数据版本。
INSERT INTO `food_database`
(`food_name`,`aliases`,`category`,`serving_state`,`protein`,`carb`,`fat`,`calorie`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('燕麦片','燕麦,纯燕麦','主食薯类','干重',13.2,67.7,6.9,389,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('糙米饭','糙米,熟糙米','主食薯类','熟重',2.6,23.0,0.9,111,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('藜麦','熟藜麦','主食薯类','熟重',4.4,21.3,1.9,120,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('蒸红薯','红薯,地瓜,番薯','主食薯类','熟重',1.6,20.7,0.1,90,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('玉米','甜玉米,煮玉米','主食薯类','熟重',3.4,21.0,1.5,96,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('全麦面包','全麦吐司','主食薯类','可食部',12.0,43.0,4.0,247,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('荞麦面','荞麦面条','主食薯类','熟重',5.0,24.0,1.0,130,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('小米粥','小米稀饭','主食薯类','熟重',1.4,8.4,0.7,46,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('南瓜','蒸南瓜','主食薯类','熟重',1.0,6.5,0.1,26,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('山药','淮山','主食薯类','可食部',1.9,12.4,0.2,57,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('鸡腿肉','去皮鸡腿','肉禽水产','熟重',24.0,0.0,8.0,177,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('鸡里脊','鸡柳','肉禽水产','熟重',26.0,0.0,3.0,135,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('瘦牛肉','牛里脊','肉禽水产','熟重',26.0,0.0,10.0,200,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('瘦猪肉','猪里脊','肉禽水产','熟重',27.0,0.0,8.0,190,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('三文鱼','鲑鱼','肉禽水产','可食部',20.0,0.0,13.0,208,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('鳕鱼','银鳕鱼','肉禽水产','可食部',18.0,0.0,0.7,82,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('金枪鱼','吞拿鱼','肉禽水产','水浸',23.0,0.0,1.0,109,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('巴沙鱼','巴沙鱼柳','肉禽水产','可食部',15.0,0.0,3.0,90,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('无糖酸奶','原味酸奶','蛋奶豆制品','可食部',4.0,5.0,3.0,63,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('希腊酸奶','高蛋白酸奶','蛋奶豆制品','可食部',10.0,3.6,0.4,59,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('脱脂牛奶','低脂牛奶','蛋奶豆制品','可食部',3.4,5.0,0.1,35,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('蛋清','鸡蛋清','蛋奶豆制品','可食部',10.9,0.7,0.2,52,'PROJECT_CURATED',NULL,'starter-v2',1,95),
('北豆腐','老豆腐','蛋奶豆制品','可食部',12.2,4.8,4.8,116,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('豆浆','无糖豆浆','蛋奶豆制品','可食部',3.0,1.2,1.6,31,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('毛豆','青豆','蛋奶豆制品','熟重',11.9,8.9,5.2,121,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('菠菜','焯菠菜','蔬菜','可食部',2.9,3.6,0.4,23,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('芹菜','西芹','蔬菜','可食部',0.7,3.0,0.2,16,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('白菜','大白菜','蔬菜','可食部',1.5,3.2,0.2,20,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('卷心菜','包菜,圆白菜','蔬菜','可食部',1.3,5.8,0.1,25,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('彩椒','甜椒,灯笼椒','蔬菜','可食部',1.0,6.0,0.3,31,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('蘑菇','白蘑菇','蔬菜','可食部',3.1,3.3,0.3,22,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('香菇','冬菇','蔬菜','可食部',2.2,5.2,0.3,34,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('秋葵','羊角豆','蔬菜','可食部',1.9,7.5,0.2,33,'PROJECT_CURATED',NULL,'starter-v2',1,70),
('紫甘蓝','红甘蓝','蔬菜','可食部',1.4,7.4,0.2,31,'PROJECT_CURATED',NULL,'starter-v2',1,70),
('洋葱','圆葱','蔬菜','可食部',1.1,9.3,0.1,40,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('牛油果','鳄梨','水果','可食部',2.0,8.5,14.7,160,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('蓝莓','越橘','水果','可食部',0.7,14.5,0.3,57,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('草莓','士多啤梨','水果','可食部',0.7,7.7,0.3,32,'PROJECT_CURATED',NULL,'starter-v2',1,90),
('猕猴桃','奇异果','水果','可食部',1.1,14.7,0.5,61,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('葡萄','提子','水果','可食部',0.7,18.1,0.2,69,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('梨','雪梨','水果','可食部',0.4,15.2,0.1,57,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('西瓜','无籽西瓜','水果','可食部',0.6,7.6,0.2,30,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('火龙果','红心火龙果','水果','可食部',1.1,13.3,0.2,55,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('杏仁','巴旦木','坚果种子','可食部',21.2,21.6,49.9,579,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('核桃','胡桃','坚果种子','可食部',15.2,13.7,65.2,654,'PROJECT_CURATED',NULL,'starter-v2',1,80),
('花生','花生仁','坚果种子','可食部',25.8,16.1,49.2,567,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('腰果','腰果仁','坚果种子','可食部',18.2,30.2,43.9,553,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('奇亚籽','奇亚种子','坚果种子','干重',16.5,42.1,30.7,486,'PROJECT_CURATED',NULL,'starter-v2',1,75),
('花生酱','无糖花生酱','调味及补剂','可食部',25.0,20.0,50.0,588,'PROJECT_CURATED',NULL,'starter-v2',1,85),
('乳清蛋白粉','蛋白粉,whey','调味及补剂','产品标示',75.0,10.0,7.0,400,'PROJECT_CURATED',NULL,'starter-v2',1,95)
ON DUPLICATE KEY UPDATE
  `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),`serving_state`=VALUES(`serving_state`),
  `source_name`=VALUES(`source_name`),`data_version`=VALUES(`data_version`),`enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

UPDATE `exercise_database` SET
  `aliases` = CASE
    WHEN `name`='卧推' THEN '杠铃卧推,平板卧推'
    WHEN `name`='深蹲' THEN '杠铃深蹲'
    WHEN `name`='硬拉' THEN '传统硬拉'
    WHEN `name`='引体向上' THEN '单杠引体'
    WHEN `name`='跑步' THEN '慢跑,户外跑'
    ELSE NULL END,
  `category` = CASE WHEN `name`='跑步' THEN '有氧' ELSE '力量训练' END,
  `primary_muscles` = CASE
    WHEN `name`='卧推' THEN '胸部,肱三头肌,肩部'
    WHEN `name` IN ('深蹲','硬拉') THEN '腿部,臀部,核心'
    WHEN `name`='引体向上' THEN '背部,肱二头肌'
    ELSE '全身' END,
  `equipment` = CASE WHEN `name`='跑步' THEN '无' WHEN `name`='引体向上' THEN '单杠' ELSE '杠铃' END,
  `source_name`='PROJECT_ESTIMATE',`source_ref`='pacompendium.com (intensity reference)',
  `data_version`='starter-v2',`sort_order`=100;

-- MET 是活动强度估算值，同类力量动作采用相同强度档，不代表单个动作的医学级测量结果。
INSERT INTO `exercise_database`
(`name`,`aliases`,`category`,`primary_muscles`,`equipment`,`intensity`,`met`,`source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
('上斜卧推','上斜杠铃卧推','力量训练','上胸,肱三头肌,肩部','杠铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('哑铃卧推','平板哑铃卧推','力量训练','胸部,肱三头肌,肩部','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,95),
('俯卧撑','伏地挺身','力量训练','胸部,肱三头肌,核心','无','中等',3.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,95),
('双杠臂屈伸','双杠撑体','力量训练','胸部,肱三头肌','双杠','高',6.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('哑铃飞鸟','平板飞鸟','力量训练','胸部','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('高位下拉','下拉','力量训练','背部,肱二头肌','绳索器械','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,95),
('杠铃划船','俯身杠铃划船','力量训练','背部,肱二头肌','杠铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('坐姿划船','绳索划船','力量训练','背部,肱二头肌','绳索器械','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('单臂哑铃划船','哑铃划船','力量训练','背部,肱二头肌','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('罗马尼亚硬拉','直腿硬拉,RDL','力量训练','腘绳肌,臀部,背部','杠铃','高',6.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('相扑硬拉','宽距硬拉','力量训练','腿部,臀部,背部','杠铃','高',6.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('前蹲','杠铃前蹲','力量训练','股四头肌,臀部,核心','杠铃','高',6.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('高脚杯深蹲','壶铃深蹲,哑铃深蹲','力量训练','腿部,臀部,核心','哑铃或壶铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('腿举','倒蹬,腿部推举','力量训练','股四头肌,臀部','腿举机','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('箭步蹲','弓步蹲','力量训练','腿部,臀部','无或哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('保加利亚分腿蹲','保加利亚蹲','力量训练','腿部,臀部','哑铃','高',6.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('腿屈伸','坐姿腿屈伸','力量训练','股四头肌','腿屈伸机','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,75),
('腿弯举','俯卧腿弯举','力量训练','腘绳肌','腿弯举机','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,75),
('臀桥','臀推','力量训练','臀部,腘绳肌','无或杠铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('提踵','站姿提踵','力量训练','小腿','无或器械','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,75),
('肩上推举','杠铃推举,军姿推举','力量训练','肩部,肱三头肌','杠铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('哑铃肩推','坐姿哑铃推举','力量训练','肩部,肱三头肌','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('哑铃侧平举','侧平举','力量训练','肩部','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('反向飞鸟','俯身飞鸟','力量训练','肩后束,上背部','哑铃或器械','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,75),
('面拉','绳索面拉','力量训练','肩后束,上背部','绳索器械','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('杠铃弯举','二头弯举','力量训练','肱二头肌','杠铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('哑铃弯举','交替弯举','力量训练','肱二头肌','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('锤式弯举','锤式哑铃弯举','力量训练','肱二头肌,前臂','哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,75),
('绳索下压','肱三头肌下压','力量训练','肱三头肌','绳索器械','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('仰卧臂屈伸','碎颅者','力量训练','肱三头肌','杠铃或哑铃','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,70),
('平板支撑','平板撑','核心训练','核心','无','中等',3.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,95),
('卷腹','仰卧卷腹','核心训练','腹部','无','中等',3.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('仰卧举腿','举腿','核心训练','腹部,髋屈肌','无','中等',3.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('俄罗斯转体','俄式转体','核心训练','腹斜肌,核心','无或药球','中等',3.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,75),
('登山跑','登山者','核心训练','核心,腿部','无','高',8.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('波比跳','Burpee','全身训练','全身','无','高',8.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('开合跳','Jumping Jack','有氧','全身','无','高',8.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('跳绳','单摇跳绳','有氧','全身','跳绳','高',11.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,95),
('快走','健步走','有氧','全身','无','中等',4.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('慢跑','轻松跑','有氧','全身','无','中等',7.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,95),
('跑步机跑步','室内跑步','有氧','全身','跑步机','高',9.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('室内单车','动感单车','有氧','腿部,心肺','单车','高',8.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('户外骑行','骑自行车','有氧','腿部,心肺','自行车','中等',7.5,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('椭圆机','椭圆仪','有氧','全身,心肺','椭圆机','中等',5.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('划船机','室内划船','有氧','全身,心肺','划船机','高',7.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('游泳','自由泳','有氧','全身,心肺','泳池','高',8.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,90),
('爬楼梯','楼梯训练','有氧','腿部,心肺','楼梯','高',8.8,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,85),
('瑜伽','哈他瑜伽','灵活性','全身','瑜伽垫','低',2.5,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('普拉提','垫上普拉提','核心训练','核心,全身','瑜伽垫','低',3.0,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80),
('拉伸','静态拉伸','灵活性','全身','无','低',2.3,'PROJECT_ESTIMATE','pacompendium.com (intensity reference)','starter-v2',1,80)
ON DUPLICATE KEY UPDATE
  `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),`primary_muscles`=VALUES(`primary_muscles`),
  `equipment`=VALUES(`equipment`),`intensity`=VALUES(`intensity`),`met`=VALUES(`met`),
  `source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),`data_version`=VALUES(`data_version`),
  `enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

SELECT COUNT(*) AS food_count FROM `food_database` WHERE `enabled`=1;
SELECT COUNT(*) AS exercise_count FROM `exercise_database` WHERE `enabled`=1;
