-- ============================================================
-- 秒杀系统初始化脚本（演示数据）
-- 注意：会 DROP 并重建三张表，请勿在存有数据的库上直接执行。
-- 时间使用 NOW() 相对生成，导入后即可直接演示「进行中/未开始/已结束」。
-- ============================================================
CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seckill;

DROP TABLE IF EXISTS seckill_order;
DROP TABLE IF EXISTS transaction_log;
DROP TABLE IF EXISTS seckill_goods;
DROP TABLE IF EXISTS sys_user;

-- 用户表（登录用，密码为 BCrypt）
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录名',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    nickname VARCHAR(64) COMMENT '昵称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 0-禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 秒杀商品表
CREATE TABLE seckill_goods (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    goods_name VARCHAR(100) NOT NULL COMMENT '商品名称',
    category VARCHAR(32) NOT NULL DEFAULT '其他' COMMENT '商品分类',
    goods_img VARCHAR(200) COMMENT '商品图片',
    original_price DECIMAL(10,2) NOT NULL COMMENT '原价',
    seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    stock_count INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    start_time DATETIME NOT NULL COMMENT '秒杀开始时间',
    end_time DATETIME NOT NULL COMMENT '秒杀结束时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_start_time(start_time),
    INDEX idx_end_time(end_time),
    INDEX idx_category(category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- 订单表
CREATE TABLE seckill_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    goods_id BIGINT NOT NULL COMMENT '商品ID',
    goods_name VARCHAR(100) NOT NULL COMMENT '商品名称',
    seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-创建中 1-已支付 2-已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    pay_time DATETIME COMMENT '支付时间',
    UNIQUE KEY uk_user_goods(user_id, goods_id),
    INDEX idx_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 本地事务表（用于MQ消费对账）
CREATE TABLE transaction_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(64) NOT NULL UNIQUE COMMENT '事务ID',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型：SECKILL_ORDER',
    business_id VARCHAR(64) NOT NULL COMMENT '业务ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-处理中 1-成功 2-失败',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_create_time(status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地事务日志表';

-- ============================================================
-- 演示账号：alice / bob / carol，密码统一为 123456
-- ============================================================
INSERT INTO sys_user (username, password, nickname, status) VALUES
    ('alice', '$2a$10$upgJAYfJbhvCJEhmI6bwU.h9sWnuf0ZLIyztR33U6sHr.qJkKU1U.', 'Alice', 1),
    ('bob',   '$2a$10$upgJAYfJbhvCJEhmI6bwU.h9sWnuf0ZLIyztR33U6sHr.qJkKU1U.', 'Bob',   1),
    ('carol', '$2a$10$upgJAYfJbhvCJEhmI6bwU.h9sWnuf0ZLIyztR33U6sHr.qJkKU1U.', 'Carol', 1);

-- ============================================================
-- 演示商品：10 个进行中 + 1 个未开始 + 1 个已结束
-- 时间全部相对 NOW() 生成，任何时间点导入都能演示三种状态
-- ============================================================
INSERT INTO seckill_goods
    (goods_name, category, goods_img, original_price, seckill_price, stock_count, start_time, end_time)
VALUES
    ('iPhone 15 Pro 256G',        '手机数码', 'https://picsum.photos/seed/iphone15/400/400',   8999.00, 6999.00, 500, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('MacBook Pro 14 M3',         '电脑办公', 'https://picsum.photos/seed/macbook/400/400',   14999.00, 11999.00, 200, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('小米 14 Ultra',             '手机数码', 'https://picsum.photos/seed/mi14/400/400',        6499.00,  5499.00, 300, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('AirPods Pro 2',             '手机数码', 'https://picsum.photos/seed/airpods/400/400',     1899.00,  1499.00, 400, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('iPad Air 11 M2',            '电脑办公', 'https://picsum.photos/seed/ipad/400/400',        4799.00,  3999.00, 150, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('戴森 V12 无线吸尘器',        '家用电器', 'https://picsum.photos/seed/dyson/400/400',       3499.00,  2799.00, 120, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('索尼 WH-1000XM5 头戴耳机',   '手机数码', 'https://picsum.photos/seed/sony/400/400',        2899.00,  2299.00, 180, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('海尔 10kg 洗烘一体机',       '家用电器', 'https://picsum.photos/seed/haier/400/400',       3999.00,  3199.00,  80, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('兰蔻小黑瓶精华 50ml',        '美妆个护', 'https://picsum.photos/seed/lancome/400/400',     1080.00,   799.00, 300, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('三只松鼠坚果大礼包',         '食品生鲜', 'https://picsum.photos/seed/nuts/400/400',         199.00,   129.00, 1000, NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 7 DAY),
    ('任天堂 Switch OLED',        '游戏影音', 'https://picsum.photos/seed/switch/400/400',      2599.00,  2099.00, 100, NOW() + INTERVAL 2 DAY,  NOW() + INTERVAL 4 DAY),
    ('戴森 Supersonic 吹风机',     '家用电器', 'https://picsum.photos/seed/hairdryer/400/400',   2999.00,  2399.00,  60, NOW() - INTERVAL 5 DAY,  NOW() - INTERVAL 3 DAY);
