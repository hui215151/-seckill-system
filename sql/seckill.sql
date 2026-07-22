CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seckill;

-- 秒杀商品表
CREATE TABLE seckill_goods (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    goods_name VARCHAR(100) NOT NULL COMMENT '商品名称',
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
    INDEX idx_end_time(end_time)
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

-- 插入测试数据
INSERT INTO seckill_goods (goods_name, original_price, seckill_price, stock_count, start_time, end_time) VALUES
('iPhone 15 Pro', 8999.00, 6999.00, 100, '2026-07-22 00:00:00', '2026-07-23 00:00:00'),
('MacBook Pro', 14999.00, 11999.00, 50, '2026-07-22 00:00:00', '2026-07-23 00:00:00');
