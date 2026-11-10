-- 常见日常食材扩充：覆盖中国家庭、餐饮和便利店高频食品，而不仅限于健身餐。
-- 数值均为每 100g（液体按每 100ml 近似）的代表值；品牌/配方差异大的条目标记为通用估算，必须由用户确认。
USE `fitness_diary`;
START TRANSACTION;

-- 先补齐已有标准食材的常见叫法，避免为同一食物创建重复记录。
UPDATE `food_database` SET `aliases`='杏仁饮料,杏仁奶,无糖杏仁饮料,almond milk'
WHERE `food_name`='无糖杏仁奶';
UPDATE `food_database` SET `aliases`='燕麦饮料,燕麦奶,无糖燕麦饮料,oat milk'
WHERE `food_name`='无糖燕麦奶';
UPDATE `food_database` SET `aliases`='豆浆,无糖豆浆,豆奶,无糖豆奶'
WHERE `food_name`='无糖豆奶';
UPDATE `food_database` SET `aliases`='无脂酸奶,无糖酸奶,原味酸奶,酸奶'
WHERE `food_name`='脱脂原味酸奶';
UPDATE `food_database` SET `aliases`='全麦吐司,吐司,全麦方包'
WHERE `food_name`='全麦面包';
UPDATE `food_database` SET `aliases`='低脂奶,低脂牛奶,一分奶'
WHERE `food_name`='低脂牛奶（1%）';
UPDATE `food_database` SET `aliases`='上海青,青江菜,小白菜,bok choy'
WHERE `food_name`='生小白菜';

INSERT INTO `food_database`
(`food_name`,`aliases`,`category`,`serving_state`,`protein`,`carb`,`fat`,`calorie`,
 `source_name`,`source_ref`,`data_version`,`enabled`,`sort_order`) VALUES
-- 禽畜水产：使用熟重代表值，适合成品照片确认后的重量估算。
('熟鸭肉（带皮）','鸭肉,鸭腿,烤鸭,烤鸭肉,烧鸭,烧鸭肉','肉禽水产','熟重',19.00,0.00,28.40,337.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟鸭胸肉（去皮）','鸭胸,鸭胸肉','肉禽水产','熟重',23.50,0.00,2.70,123.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟羊肉（通用）','羊肉,熟羊肉','肉禽水产','熟重',25.60,0.00,16.50,258.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟羊排','羊排,烤羊排','肉禽水产','熟重',24.50,0.00,20.50,294.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟牛腩','牛腩,炖牛腩','肉禽水产','熟重',28.80,0.00,18.00,280.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟牛排','牛排,煎牛排','肉禽水产','熟重',27.30,0.00,15.00,250.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟五花肉','五花肉,三层肉','肉禽水产','熟重',9.30,0.00,53.00,518.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟猪排','猪排,排骨,排骨肉','肉禽水产','熟重',27.00,0.00,14.00,242.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟鸡翅','鸡翅,鸡中翅,烤鸡翅','肉禽水产','熟重',30.50,0.00,8.10,203.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟鸡爪','鸡爪,凤爪','肉禽水产','熟重',19.00,2.70,15.00,215.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('熟猪肝','猪肝','肉禽水产','熟重',26.50,4.70,5.30,165.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('熟鸡肝','鸡肝','肉禽水产','熟重',24.50,1.00,6.50,167.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('熟鲈鱼','鲈鱼,海鲈鱼','肉禽水产','熟重',24.20,0.00,2.30,124.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟草鱼','草鱼','肉禽水产','熟重',22.90,0.00,5.60,146.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟带鱼','带鱼','肉禽水产','熟重',20.60,0.00,8.10,161.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟黄花鱼','黄花鱼,黄鱼','肉禽水产','熟重',22.00,0.00,4.00,124.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟螃蟹肉','螃蟹,蟹肉','肉禽水产','熟重',19.40,0.00,1.50,97.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟牡蛎','牡蛎,生蚝','肉禽水产','熟重',9.00,4.90,2.50,81.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('熟蛤蜊','蛤蜊,花甲','肉禽水产','熟重',25.60,5.10,2.00,148.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('熟章鱼','章鱼,八爪鱼','肉禽水产','熟重',29.80,4.40,2.10,164.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('培根（熟，通用估算）','培根,烟肉','加工肉制品','熟重',33.00,1.40,42.00,541.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('香肠（通用估算）','香肠,腊肠,烤肠','加工肉制品','可食部',16.00,3.00,27.00,315.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('午餐肉（通用估算）','午餐肉,罐头午餐肉','加工肉制品','可食部',13.00,4.00,28.00,315.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('鱼丸（通用估算）','鱼丸,鱼蛋','加工水产','熟重',11.00,12.00,4.00,128.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('熟鹅肉（通用）','鹅肉,烧鹅肉','肉禽水产','熟重',22.80,0.00,21.90,283.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('虾滑（通用估算）','虾滑,虾肉滑','加工水产','熟重',10.00,8.00,3.00,100.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),

-- 主食面点：复合馅料食品采用通用估算，用户仍可按实际选择更具体菜谱。
('白米粥','白粥,大米粥,稀饭','谷物主食','熟重',1.10,9.80,0.20,46.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('馒头','白馒头,蒸馒头','谷物主食','熟重',7.00,47.00,1.10,223.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('花卷','葱花卷','谷物主食','熟重',7.30,45.60,4.00,244.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('熟米粉','米粉,米线','谷物主食','熟重',1.80,24.90,0.20,109.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('熟河粉','河粉,粿条','谷物主食','熟重',1.40,25.00,0.50,110.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('熟意大利面','意大利面,意面,熟意面','谷物主食','熟重',5.80,30.90,0.90,158.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('油条','炸油条','谷物主食','可食部',6.90,51.00,17.60,388.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('水饺（通用估算）','饺子,水饺,煮饺子','复合主食','熟重',8.00,22.00,7.00,183.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,45),
('包子（通用估算）','包子,肉包,菜包','复合主食','熟重',8.00,30.00,7.00,215.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,45),
('馄饨（通用估算）','馄饨,云吞','复合主食','熟重',7.00,18.00,5.00,145.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,45),
('方便面（冲泡，通用估算）','方便面,泡面,即食面','复合主食','熟重',4.50,26.00,7.00,180.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,40),
('蛋糕（通用估算）','蛋糕,奶油蛋糕','烘焙甜点','可食部',5.00,50.00,15.00,350.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,35),
('饼干（通用估算）','饼干,曲奇','烘焙甜点','可食部',7.00,68.00,20.00,480.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,35),
('三明治（通用估算）','三明治,夹心吐司','复合主食','可食部',10.00,25.00,8.00,210.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('披萨（通用估算）','披萨,比萨','复合主食','可食部',11.00,27.00,10.00,250.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('汉堡（通用估算）','汉堡,汉堡包','复合主食','可食部',12.00,24.00,12.00,250.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),

-- 常见蔬菜、菌藻与水果，以可食部生重为主。
('菜心','广东菜心,菜薹','蔬菜','可食部',2.80,4.60,0.50,28.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,65),
('油菜','青菜,上海油菜','蔬菜','可食部',1.80,2.70,0.50,20.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('韭菜','韭黄除外','蔬菜','可食部',2.40,4.50,0.40,26.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('茄子','紫茄子','蔬菜','可食部',1.00,5.90,0.20,25.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('冬瓜','白瓜','蔬菜','可食部',0.40,2.60,0.20,13.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('丝瓜','水瓜','蔬菜','可食部',1.20,4.40,0.20,20.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('苦瓜','凉瓜','蔬菜','可食部',1.00,3.70,0.20,17.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('莲藕','藕','蔬菜','可食部',1.90,16.40,0.10,74.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('芦笋','石刁柏','蔬菜','可食部',2.20,3.90,0.10,20.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('豆角','四季豆,长豆角','蔬菜','可食部',1.90,7.00,0.20,34.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,60),
('木耳（泡发）','木耳,黑木耳','菌菇','泡发重',1.50,6.00,0.20,27.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('海带（泡发）','海带,昆布','菌藻','泡发重',1.70,9.60,0.60,43.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('紫菜（干）','紫菜,海苔原料','菌藻','干重',26.70,44.10,1.10,250.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('酸菜（通用估算）','酸菜,腌酸菜','腌制蔬菜','可食部',1.40,3.20,0.20,21.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,40),
('泡菜（通用估算）','泡菜,韩式泡菜','腌制蔬菜','可食部',1.10,4.20,0.50,23.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,40),
('桃','水蜜桃,毛桃','水果','可食部',0.90,9.50,0.30,39.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('柚子','蜜柚,西柚除外','水果','可食部',0.80,9.60,0.10,38.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('柠檬','黄柠檬','水果','可食部',1.10,9.30,0.30,29.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('木瓜','番木瓜','水果','可食部',0.50,10.80,0.30,43.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('榴莲','榴莲果肉','水果','可食部',1.50,27.10,5.30,147.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('甜樱桃','樱桃,车厘子','水果','可食部',1.10,16.00,0.20,63.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('鲜红枣','红枣,鲜枣','水果','可食部',1.20,30.50,0.20,125.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('石榴','石榴籽','水果','可食部',1.70,18.70,1.20,83.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('柿子','甜柿','水果','可食部',0.60,18.60,0.20,70.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('哈密瓜','甜瓜,哈蜜瓜','水果','可食部',0.80,8.20,0.20,34.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('李子','鲜李子,布李','水果','可食部',0.70,11.40,0.30,46.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('椰子肉','椰子,鲜椰肉','水果','可食部',3.30,15.20,33.50,354.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('熟豌豆','豌豆,青豌豆','蔬菜','熟重',5.40,15.60,0.40,84.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('竹笋','笋,鲜竹笋','蔬菜','可食部',2.60,5.20,0.30,27.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),

-- 蛋奶豆制品。
('内酯豆腐','嫩豆腐,盒装豆腐','蛋奶豆制品','可食部',5.00,2.00,2.70,50.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('豆腐皮','豆皮,千张','蛋奶豆制品','可食部',24.50,6.20,17.10,262.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('腐竹（泡发）','腐竹,豆腐竹','蛋奶豆制品','泡发重',13.40,7.60,7.10,151.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('熟黄豆','黄豆,大豆','豆类','熟重',18.20,14.40,9.00,173.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('熟红豆','红豆,赤小豆','豆类','熟重',7.50,25.00,0.50,127.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('熟绿豆','绿豆','豆类','熟重',7.00,19.20,0.40,105.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('切达奶酪','奶酪,芝士,切达芝士','蛋奶豆制品','可食部',24.90,1.30,33.10,403.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('冰淇淋（通用估算）','冰淇淋,雪糕','乳制甜品','可食部',3.50,23.60,11.00,207.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,35),
('淡奶油','稀奶油,鲜奶油,heavy cream','蛋奶豆制品','液体',2.10,2.80,36.10,340.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,35),
('甜炼乳','炼乳,加糖炼乳','蛋奶豆制品','可食部',7.90,54.40,8.70,321.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,35),

-- 饮料：无糖单品可直接使用；含糖、奶制和酒类为常见代表值，包装食品优先以标签为准。
('含糖可乐（通用估算）','可乐,普通可乐,含糖可乐','饮料','液体',0.00,10.60,0.00,42.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,45),
('无糖可乐（通用估算）','无糖可乐,零度可乐,零糖可乐,健怡可乐','饮料','液体',0.00,0.10,0.00,1.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,45),
('柠檬味汽水（通用估算）','雪碧,七喜,柠檬汽水','饮料','液体',0.00,10.40,0.00,41.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,40),
('无糖苏打水','苏打水,气泡水,无糖气泡水','饮料','液体',0.00,0.00,0.00,0.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('矿泉水','饮用水,纯净水,白水','饮料','液体',0.00,0.00,0.00,0.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('黑咖啡','美式咖啡,咖啡,无糖咖啡','饮料','液体',0.10,0.00,0.00,2.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,55),
('拿铁咖啡（通用估算）','拿铁,咖啡拿铁','饮料','液体',3.00,4.80,2.50,54.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,40),
('奶茶（不含珍珠，通用估算）','奶茶,港式奶茶','饮料','液体',1.50,11.00,2.50,72.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,35),
('珍珠奶茶（通用估算）','珍珠奶茶,波霸奶茶','饮料','液体',1.20,18.00,2.20,96.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,35),
('纯橙汁','橙汁,100%橙汁','饮料','液体',0.70,10.40,0.20,45.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('纯苹果汁','苹果汁,100%苹果汁','饮料','液体',0.10,11.30,0.10,46.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('椰子水','天然椰子水','饮料','液体',0.70,3.70,0.20,19.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,50),
('运动饮料（通用估算）','运动饮料,电解质饮料','饮料','液体',0.00,6.00,0.00,24.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,35),
('能量饮料（通用估算）','能量饮料,功能饮料','饮料','液体',0.00,11.00,0.00,45.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('啤酒（通用估算）','啤酒','酒精饮料','液体',0.50,3.60,0.00,43.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('红葡萄酒（通用估算）','红酒,红葡萄酒','酒精饮料','液体',0.10,2.60,0.00,85.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('白酒（通用估算）','白酒,蒸馏酒','酒精饮料','液体',0.00,0.00,0.00,298.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,25),
('绿茶（无糖）','绿茶,无糖茶','饮料','液体',0.00,0.00,0.00,1.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('红茶（无糖）','红茶,无糖红茶','饮料','液体',0.00,0.30,0.00,1.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),

-- 基础油脂与调味料；照片通常无法判断用量，仍由用户确认克数。
('花生油','花生食用油','油脂调味','液体',0.00,0.00,100.00,884.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('大豆油','豆油','油脂调味','液体',0.00,0.00,100.00,884.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('玉米油','玉米胚芽油','油脂调味','液体',0.00,0.00,100.00,884.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('芝麻油','香油,麻油','油脂调味','液体',0.00,0.00,100.00,884.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('猪油','猪脂肪','油脂调味','可食部',0.00,0.00,100.00,902.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,35),
('黄油','黄油块,butter','油脂调味','可食部',0.90,0.10,81.10,717.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('食盐','盐,食用盐','调味料','干重',0.00,0.00,0.00,0.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,35),
('白砂糖','白糖,砂糖','调味料','干重',0.00,100.00,0.00,400.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,40),
('蜂蜜','天然蜂蜜','调味料','可食部',0.30,82.40,0.00,304.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,40),
('生抽酱油','酱油,生抽','调味料','液体',8.10,4.90,0.60,53.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,45),
('食醋','醋,米醋,陈醋','调味料','液体',0.00,0.60,0.00,18.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,40),
('蚝油（通用估算）','蚝油','调味料','可食部',1.40,11.00,0.20,51.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('番茄酱（通用估算）','番茄酱,茄汁','调味料','可食部',1.30,27.40,0.20,112.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('芝麻酱（通用估算）','芝麻酱,麻酱','调味料','可食部',19.00,21.00,52.00,630.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,30),
('沙拉酱（通用估算）','沙拉酱,蛋黄酱','调味料','可食部',1.00,7.00,44.00,430.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,25),
('红糖','赤砂糖,红砂糖','调味料','干重',0.00,98.00,0.00,380.00,'PROJECT_CURATED','common-food-catalog-2026-10','common-v1',1,30),
('辣椒酱（通用估算）','辣椒酱,辣酱','调味料','可食部',1.50,15.00,3.00,90.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,25),
('豆瓣酱（通用估算）','豆瓣酱,郫县豆瓣酱','调味料','可食部',4.00,18.00,7.00,145.00,'SYSTEM_GENERIC_ESTIMATE','common-food-catalog-2026-10','common-v1',1,25)
ON DUPLICATE KEY UPDATE
  `aliases`=VALUES(`aliases`),`category`=VALUES(`category`),`serving_state`=VALUES(`serving_state`),
  `protein`=VALUES(`protein`),`carb`=VALUES(`carb`),`fat`=VALUES(`fat`),`calorie`=VALUES(`calorie`),
  `source_name`=VALUES(`source_name`),`source_ref`=VALUES(`source_ref`),
  `data_version`=VALUES(`data_version`),`enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

-- 已有待审核名称若现在能精确命中标准名或别名，标记为已有目录候选，保留历史次数供复盘。
UPDATE `food_database_candidates` c
JOIN `food_database` f
  ON f.`enabled`=1
 AND (f.`food_name`=c.`recognized_name`
      OR FIND_IN_SET(c.`recognized_name`, REPLACE(COALESCE(f.`aliases`,''),'，',',')) > 0)
SET c.`review_status`='MATCH_AVAILABLE';

COMMIT;
