-- =============================================
-- 融销通 - 基于数字经济的农产品融销一体平台
-- 数据库初始化脚本（共 15 张表）
-- =============================================

DROP DATABASE IF EXISTS rongxiaotong;
CREATE DATABASE rongxiaotong DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rongxiaotong;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `phone` VARCHAR(20) COMMENT '手机号',
  `email` VARCHAR(100) COMMENT '邮箱',
  `avatar` VARCHAR(255) COMMENT '头像路径',
  `role` TINYINT NOT NULL DEFAULT 1 COMMENT '1-普通用户(农民) 2-专家 3-管理员',
  `farm_name` VARCHAR(100) COMMENT '农场/合作社名称',
  `id_card` VARCHAR(20) COMMENT '身份证号',
  `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '0-正常 1-已删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 2. 专家扩展表
-- =============================================
CREATE TABLE `expert` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL UNIQUE COMMENT '关联 user.id',
  `specialty` VARCHAR(100) COMMENT '专长领域',
  `title` VARCHAR(50) COMMENT '职称',
  `introduction` TEXT COMMENT '个人简介',
  `consult_price` DECIMAL(10,2) DEFAULT 0 COMMENT '咨询价格（元/次）',
  `rating` DECIMAL(3,2) DEFAULT 5.00 COMMENT '评分 1-5',
  `consult_count` INT DEFAULT 0 COMMENT '咨询次数',
  `certification` VARCHAR(255) COMMENT '资质证书图片',
  `audit_status` TINYINT DEFAULT 0 COMMENT '0-待审核 1-审核通过 2-已驳回',
  `audit_remark` VARCHAR(255) COMMENT '驳回原因',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家扩展表';

-- =============================================
-- 3. 收货地址表
-- =============================================
CREATE TABLE `address` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `receiver` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(20) NOT NULL,
  `province` VARCHAR(20),
  `city` VARCHAR(20),
  `district` VARCHAR(20),
  `detail` VARCHAR(255),
  `is_default` TINYINT DEFAULT 0 COMMENT '0-非默认 1-默认',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- =============================================
-- 4. 分类表
-- =============================================
CREATE TABLE `category` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `type` TINYINT NOT NULL COMMENT '1-商品分类 2-知识分类 3-问题分类',
  `icon` VARCHAR(255),
  `sort` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- =============================================
-- 5. 商品/货源表
-- =============================================
CREATE TABLE `product` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `seller_id` BIGINT NOT NULL COMMENT '发布者 user.id',
  `name` VARCHAR(100) NOT NULL,
  `category_id` BIGINT,
  `price` DECIMAL(10,2) NOT NULL,
  `unit` VARCHAR(20) DEFAULT '斤',
  `stock` INT DEFAULT 0,
  `image` VARCHAR(255),
  `images` TEXT COMMENT 'JSON数组',
  `description` TEXT,
  `origin` VARCHAR(100),
  `audit_status` TINYINT DEFAULT 1 COMMENT '0-待审核 1-审核通过 2-已驳回',
  `audit_remark` VARCHAR(255) COMMENT '驳回原因',
  `status` TINYINT DEFAULT 1 COMMENT '0-下架 1-上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_seller` (`seller_id`),
  INDEX `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品/货源表';

-- =============================================
-- 6. 采购需求表
-- =============================================
CREATE TABLE `demand` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `buyer_id` BIGINT NOT NULL COMMENT '需求发布者',
  `name` VARCHAR(100) NOT NULL,
  `category_id` BIGINT,
  `quantity` INT NOT NULL,
  `unit` VARCHAR(20) DEFAULT '斤',
  `expected_price` DECIMAL(10,2),
  `description` TEXT,
  `deadline` DATETIME,
  `status` TINYINT DEFAULT 1 COMMENT '0-已关闭 1-进行中 2-已完成',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_buyer` (`buyer_id`),
  INDEX `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购需求表';

-- =============================================
-- 7. 购物车表
-- =============================================
CREATE TABLE `cart` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `quantity` INT NOT NULL DEFAULT 1,
  `selected` TINYINT DEFAULT 1 COMMENT '0-未选中 1-选中',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- =============================================
-- 8. 订单表
-- =============================================
CREATE TABLE `orders` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_no` VARCHAR(50) NOT NULL UNIQUE,
  `buyer_id` BIGINT NOT NULL,
  `seller_id` BIGINT NOT NULL,
  `total_amount` DECIMAL(12,2) NOT NULL,
  `address_id` BIGINT,
  `receiver` VARCHAR(50),
  `receiver_phone` VARCHAR(20),
  `receiver_address` VARCHAR(255),
  `remark` VARCHAR(255),
  `status` TINYINT DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
  `refund_status` TINYINT DEFAULT 0 COMMENT '0-无退款 1-退款申请中 2-已退款 3-退款拒绝',
  `refund_remark` VARCHAR(255),
  `pay_time` DATETIME,
  `ship_time` DATETIME,
  `finish_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_buyer` (`buyer_id`),
  INDEX `idx_seller` (`seller_id`),
  INDEX `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- =============================================
-- 9. 订单明细表
-- =============================================
CREATE TABLE `order_item` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `product_name` VARCHAR(100),
  `product_image` VARCHAR(255),
  `price` DECIMAL(10,2) NOT NULL,
  `quantity` INT NOT NULL,
  `subtotal` DECIMAL(12,2) NOT NULL,
  INDEX `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- =============================================
-- 10. 在线问答表
-- =============================================
CREATE TABLE `qa` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `questioner_id` BIGINT NOT NULL,
  `expert_id` BIGINT COMMENT '指定专家（可空=公开）',
  `category_id` BIGINT,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NOT NULL,
  `images` TEXT COMMENT 'JSON数组',
  `status` TINYINT DEFAULT 0 COMMENT '0-待回答 1-已回答 2-已关闭',
  `view_count` INT DEFAULT 0,
  `reply_count` INT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_questioner` (`questioner_id`),
  INDEX `idx_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线问答表';

-- =============================================
-- 11. 问答回答表
-- =============================================
CREATE TABLE `qa_reply` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `qa_id` BIGINT NOT NULL,
  `replier_id` BIGINT NOT NULL,
  `content` TEXT NOT NULL,
  `is_expert` TINYINT DEFAULT 0 COMMENT '0-普通用户 1-专家回答',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_qa` (`qa_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答回答表';

-- =============================================
-- 12. 专家预约表
-- =============================================
CREATE TABLE `appointment` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `expert_id` BIGINT NOT NULL,
  `appointment_time` DATETIME NOT NULL,
  `duration` INT DEFAULT 30 COMMENT '分钟',
  `topic` VARCHAR(200),
  `status` TINYINT DEFAULT 0 COMMENT '0-待确认 1-已确认 2-已完成 3-已取消',
  `amount` DECIMAL(10,2),
  `remark` TEXT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user` (`user_id`),
  INDEX `idx_expert` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家预约表';

-- =============================================
-- 13. 农业知识文章表
-- =============================================
CREATE TABLE `knowledge` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `author_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `category_id` BIGINT,
  `cover_image` VARCHAR(255),
  `summary` VARCHAR(500),
  `content` MEDIUMTEXT NOT NULL,
  `view_count` INT DEFAULT 0,
  `like_count` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '0-下架 1-上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_author` (`author_id`),
  INDEX `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农业知识文章表';

-- =============================================
-- 14. 融资申请表
-- =============================================
CREATE TABLE `finance_apply` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT COMMENT '关联商品/项目',
  `title` VARCHAR(200) NOT NULL,
  `amount` DECIMAL(15,2) NOT NULL,
  `purpose` VARCHAR(500),
  `description` TEXT,
  `repay_period` INT COMMENT '月',
  `contact_name` VARCHAR(50),
  `contact_phone` VARCHAR(20),
  `status` TINYINT DEFAULT 0 COMMENT '0-待审核 1-审核通过 2-已拒绝 3-进行中 4-已完成',
  `review_remark` TEXT,
  `review_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='融资申请表';

-- =============================================
-- 15. 模拟金融产品表（智能匹配用）
-- =============================================
CREATE TABLE `finance_product` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `bank_name` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `purpose` VARCHAR(500) COMMENT '适用用途关键词（逗号分隔）',
  `min_amount` DECIMAL(15,2),
  `max_amount` DECIMAL(15,2),
  `min_period` INT COMMENT '最短期限（月）',
  `max_period` INT COMMENT '最长期限（月）',
  `interest_rate` DECIMAL(5,4) COMMENT '年利率 如 0.0435',
  `description` TEXT,
  `status` TINYINT DEFAULT 1 COMMENT '0-下架 1-上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟金融产品表';


-- =============================================
-- 示例数据（密码统一 123456，生产环境请用 BCrypt 加密存储，此处 MD5 仅供开发期占位）
-- =============================================

-- ------------ 用户 ------------
INSERT INTO `user` (`username`, `password`, `real_name`, `phone`, `email`, `role`, `farm_name`, `status`) VALUES
('admin',    'e10adc3949ba59abbe56e057f20f883e', '超级管理员', '13800000000', 'admin@rx.com',    3, NULL,                1),
('lisi',     'e10adc3949ba59abbe56e057f20f883e', '李四',       '13800000001', 'lisi@rx.com',     1, '山东烟台红富士果园', 1),
('wangwu',   'e10adc3949ba59abbe56e057f20f883e', '王五',       '13800000002', 'wangwu@rx.com',   1, '东北五常米业',       1),
('zhaoliu',  'e10adc3949ba59abbe56e057f20f883e', '赵六',       '13800000003', 'zhaoliu@rx.com',  1, '江南水乡蔬菜基地',   1),
('expert01', 'e10adc3949ba59abbe56e057f20f883e', '陈农师',     '13811111111', 'chen@rx.com',     2, NULL,                1),
('expert02', 'e10adc3949ba59abbe56e057f20f883e', '王专家',     '13822222222', 'wang@rx.com',     2, NULL,                1);

-- ------------ 专家扩展 ------------
INSERT INTO `expert` (`user_id`, `specialty`, `title`, `introduction`, `consult_price`, `rating`, `consult_count`, `audit_status`) VALUES
(5, '水稻、小麦',     '高级农艺师', '从事粮食作物研究30年，擅长病虫害防治和高产栽培技术', 100.00, 4.90, 128, 1),
(6, '果树、蔬菜栽培', '研究员',     '果树栽培专家，专注苹果、梨等果树的育种和栽培研究',   150.00, 4.80, 96,  1);

-- ------------ 收货地址 ------------
INSERT INTO `address` (`user_id`, `receiver`, `phone`, `province`, `city`, `district`, `detail`, `is_default`) VALUES
(2, '李四', '13800000001', '山东省',   '烟台市',   '栖霞区', '庄园路88号果园',       1),
(2, '李四', '13800000001', '山东省',   '烟台市',   '莱山区', '滨海路123号',          0),
(3, '王五', '13800000002', '黑龙江省', '哈尔滨市', '五常市', '五常镇稻米产区56号',   1),
(4, '赵六', '13800000003', '浙江省',   '杭州市',   '余杭区', '径山镇蔬菜基地',       1);

-- ------------ 分类 ------------
INSERT INTO `category` (`name`, `type`, `sort`) VALUES
('水果', 1, 1), ('蔬菜', 1, 2), ('粮食', 1, 3), ('畜牧', 1, 4),
('种植技术', 2, 1), ('养殖技术', 2, 2), ('病虫害防治', 2, 3), ('政策法规', 2, 4),
('种植问题', 3, 1), ('养殖问题', 3, 2), ('融资问题', 3, 3);

-- ------------ 商品 ------------
INSERT INTO `product` (`seller_id`, `name`, `category_id`, `price`, `unit`, `stock`, `image`, `description`, `origin`, `audit_status`, `status`) VALUES
(2, '山东红富士苹果（80mm以上）', 1, 5.80,  '斤', 10000, '/images/apple1.jpg', '正宗烟台红富士，脆甜多汁',                   '山东烟台',   1, 1),
(2, '烟台嘎啦苹果（小果）',       1, 3.50,  '斤', 5000,  '/images/apple2.jpg', '新鲜嘎啦苹果，皮薄汁多',                     '山东烟台',   1, 1),
(3, '东北五常稻花香大米',         3, 88.00, '袋', 2000,  '/images/rice1.jpg',  '一年一季，口感香糯，10斤真空包装',           '黑龙江五常', 1, 1),
(3, '东北长粒香大米',             3, 65.00, '袋', 1500,  '/images/rice2.jpg',  '颗粒饱满，日常食用10斤装',                   '黑龙江五常', 1, 1),
(4, '有机西兰花',                 2, 4.50,  '斤', 2000,  '/images/broccoli.jpg','绿色种植，当日采摘当日发货',                  '浙江杭州',   1, 1),
(4, '新鲜番茄',                   2, 3.20,  '斤', 3000,  '/images/tomato.jpg', '沙瓤番茄，自然成熟',                         '浙江杭州',   1, 1);

-- ------------ 采购需求 ------------
INSERT INTO `demand` (`buyer_id`, `name`, `category_id`, `quantity`, `unit`, `expected_price`, `description`, `deadline`, `status`) VALUES
(3, '需要苹果纸箱',   1, 5000,  '个', 2.50, '高质量苹果专用纸箱，需印刷',     '2026-10-01 12:00:00', 1),
(2, '采购优质稻种',   3, 1000,  '斤', 15.00,'需要抗性好的水稻种子',           '2026-09-30 18:00:00', 1),
(4, '寻求蔬菜收购商', 2, 10000, '斤', 3.00, '长期合作，每日供应新鲜蔬菜',     '2026-11-01 12:00:00', 1);

-- ------------ 购物车 ------------
INSERT INTO `cart` (`user_id`, `product_id`, `quantity`, `selected`) VALUES
(3, 1, 50,  1),
(3, 2, 100, 1),
(4, 3, 5,   0);

-- ------------ 订单 ------------
INSERT INTO `orders` (`order_no`, `buyer_id`, `seller_id`, `total_amount`, `address_id`, `receiver`, `receiver_phone`, `receiver_address`, `remark`, `status`, `pay_time`, `ship_time`, `finish_time`) VALUES
('RX20260920001', 3, 2, 290.00, 3, '王五', '13800000002', '黑龙江省哈尔滨市五常市五常镇稻米产区56号', '尽快发货',   3, '2026-09-18 10:30:00', '2026-09-18 14:00:00', '2026-09-20 09:00:00'),
('RX20260920002', 4, 3, 440.00, 4, '赵六', '13800000003', '浙江省杭州市余杭区径山镇蔬菜基地',           '10斤装一袋',  2, '2026-09-19 08:00:00', '2026-09-19 16:00:00', NULL),
('RX20260920003', 3, 4, 225.00, 3, '王五', '13800000002', '黑龙江省哈尔滨市五常市五常镇稻米产区56号',   '',            1, '2026-09-20 11:00:00', NULL,                  NULL);

-- ------------ 订单明细 ------------
INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `price`, `quantity`, `subtotal`) VALUES
(1, 1, '山东红富士苹果（80mm以上）', '/images/apple1.jpg',   5.80,  50, 290.00),
(2, 3, '东北五常稻花香大米',         '/images/rice1.jpg',    88.00, 5,  440.00),
(3, 5, '有机西兰花',                 '/images/broccoli.jpg', 4.50,  50, 225.00);

-- ------------ 在线问答 ------------
INSERT INTO `qa` (`questioner_id`, `expert_id`, `category_id`, `title`, `content`, `status`, `view_count`, `reply_count`) VALUES
(2,  5, 1, '红富士苹果采后如何保鲜？',     '我家果园刚摘了一批红富士，想问下怎么保存能放得久一些？',       1, 234, 3),
(3,  5, 3, '五常稻花香种子怎么选？',       '想今年再扩种一些，怎么挑选好的稻花香种子？',                   1, 156, 2),
(4,  NULL, 2, '西兰花黄叶是什么原因？',    '最近大棚里西兰花叶子发黄有斑点，请教专家这是什么病害？',       0, 89,  0);

-- ------------ 问答回答 ------------
INSERT INTO `qa_reply` (`qa_id`, `replier_id`, `content`, `is_expert`) VALUES
(1, 5, '红富士苹果适宜冷藏温度0~1℃，湿度90%~95%，可用塑料膜单果套袋+纸箱包装，注意通风。', 1),
(1, 3, '我也是种苹果的，学习了！',                                                         0),
(1, 5, '补充：短期保存可以放冷库，长期需要用气调库。',                                     1),
(2, 5, '建议从正规农资店购买，要看包装上的品种审定编号。',                                 1),
(2, 2, '最好自己先做发芽实验，100粒种子发芽95以上就比较好。',                               0);

-- ------------ 专家预约 ------------
INSERT INTO `appointment` (`user_id`, `expert_id`, `appointment_time`, `duration`, `topic`, `status`, `amount`, `remark`) VALUES
(2, 5, '2026-09-25 14:00:00', 30, '苹果病虫害防治咨询', 1, 100.00, '想请教下苹果轮纹病的防治方法'),
(3, 6, '2026-09-26 10:00:00', 60, '果树栽培规划',       0, 150.00, '明年想扩种果园，咨询规划建议');

-- ------------ 农业知识 ------------
INSERT INTO `knowledge` (`author_id`, `title`, `category_id`, `cover_image`, `summary`, `content`, `view_count`, `like_count`, `status`) VALUES
(5, '水稻高产栽培十大要点',       1, '/images/know1.jpg', '从选种到收获，详解水稻高产栽培全过程',     '一、选用良种\n二、适时播种\n三、合理密植', 1200, 85,  1),
(5, '小麦锈病的识别与防治',       3, '/images/know2.jpg', '小麦锈病分为条锈、叶锈、秆锈三种',         '小麦锈病的识别方法...\n防治措施...',       890,  62,  1),
(6, '苹果全年管理月历（完整版）', 1, '/images/know3.jpg', '从一月到十二月，每个月果树管理重点一览',   '一月：清园修剪\n二月：施肥\n三月：刻芽',   2300, 156, 1),
(6, '大棚蔬菜反季节栽培要点',     2, '/images/know4.jpg', '反季节蔬菜效益好，关键技术总结',           '一、品种选择\n二、温度管理\n三、水肥管理', 1560, 98,  1),
(1, '2026年农业补贴政策解读',     4, '/images/know5.jpg', '最新农业补贴政策整理',                     '今年国家继续加大对农业的扶持力度...',       3400, 220, 1);

-- ------------ 融资申请 ------------
INSERT INTO `finance_apply` (`user_id`, `product_id`, `title`, `amount`, `purpose`, `description`, `repay_period`, `contact_name`, `contact_phone`, `status`, `review_remark`) VALUES
(2, 1, '红富士果园扩建项目融资', 200000.00, '果园扩建',   '计划新增100亩苹果园，购买树苗、化肥等', 24, '李四', '13800000001', 1, '资料齐全，批准贷款'),
(3, 3, '五常大米品牌推广融资',   500000.00, '品牌推广',   '打造自有品牌，开设旗舰店',              36, '王五', '13800000002', 0, NULL),
(4, NULL, '蔬菜大棚建设融资',     80000.00,  '大棚建设',   '新建5个现代化蔬菜大棚',                 12, '赵六', '13800000003', 2, '抵押物不足，建议增加担保');

-- ------------ 模拟金融产品 ------------
INSERT INTO `finance_product` (`bank_name`, `name`, `purpose`, `min_amount`, `max_amount`, `min_period`, `max_period`, `interest_rate`, `description`) VALUES
('中国农业银行',  '农E贷-种植贷',     '种植,果园,扩建,化肥,农药',    50000.00,  3000000.00, 6,  36, 0.0435, '面向种植户的专项贷款，利率低，最高300万'),
('中国农业银行',  '农E贷-养殖贷',     '养殖,畜牧,饲料,鸡,猪,牛',     50000.00,  2000000.00, 6,  24, 0.0450, '针对养殖户的流动资金贷款'),
('中国建设银行',  '裕农快贷',         '大棚,蔬菜,基地,设施',         100000.00, 1000000.00, 3,  12, 0.0385, '线上申请，最快当天放款'),
('中国工商银行',  '兴农通-乡村振兴贷', '品牌,推广,电商,合作社',       200000.00, 5000000.00, 12, 60, 0.0420, '支持农业品牌建设和电商发展'),
('中国邮政储蓄银行', '农贷通',          '收购,粮食,仓储',              30000.00,  500000.00, 3,  12, 0.0500, '粮食收购季节性贷款，随借随还');