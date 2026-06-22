CREATE TABLE IF NOT EXISTS sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(64) NOT NULL UNIQUE COMMENT '货品编码',
    sku_name VARCHAR(255) NOT NULL COMMENT '货品名称',
    unit VARCHAR(32) NOT NULL DEFAULT '件' COMMENT '计量单位',
    shelf_life_days INT COMMENT '默认保质期天数',
    description VARCHAR(500) COMMENT '描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sku_code (sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='货品表';

CREATE TABLE IF NOT EXISTS batch_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(64) NOT NULL COMMENT '货品编码',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    location_code VARCHAR(64) NOT NULL COMMENT '库位编码',
    production_date DATE NOT NULL COMMENT '生产日期',
    expiry_date DATE NOT NULL COMMENT '到期日期',
    inbound_time DATETIME NOT NULL COMMENT '入库时间',
    physical_qty INT NOT NULL DEFAULT 0 COMMENT '实物数量',
    reserved_qty INT NOT NULL DEFAULT 0 COMMENT '预占数量',
    available_qty INT NOT NULL DEFAULT 0 COMMENT '可用数量(=实物-预占)',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sku_batch_location (sku_code, batch_no, location_code),
    INDEX idx_sku_expiry (sku_code, expiry_date),
    INDEX idx_location (location_code),
    INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次库存表(核心)';

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_no VARCHAR(64) NOT NULL UNIQUE COMMENT '预占单号',
    order_no VARCHAR(64) COMMENT '关联业务单号',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE-预占中,CONFIRMED-已确认拣货,RELEASED-已释放,EXPIRED-已超时',
    expire_at DATETIME NOT NULL COMMENT '预占超时时间',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_expire_at (expire_at),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预占单';

CREATE TABLE IF NOT EXISTS inventory_reservation_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL COMMENT '预占单ID',
    sku_code VARCHAR(64) NOT NULL COMMENT '货品编码',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    location_code VARCHAR(64) NOT NULL COMMENT '库位编码',
    reserved_qty INT NOT NULL COMMENT '预占数量',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reservation_id (reservation_id),
    INDEX idx_sku_batch (sku_code, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预占明细';

CREATE TABLE IF NOT EXISTS inventory_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_no VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    txn_type VARCHAR(32) NOT NULL COMMENT '流水类型:INBOUND-入库,OUTBOUND-出库,RESERVE-预占,RELEASE-释放预占,CONFIRM-确认拣货,TRANSFER_IN-调拨入,TRANSFER_OUT-调拨出',
    ref_no VARCHAR(64) COMMENT '关联单号',
    sku_code VARCHAR(64) NOT NULL COMMENT '货品编码',
    batch_no VARCHAR(64) COMMENT '批次号',
    location_code VARCHAR(64) COMMENT '库位编码',
    qty_change INT NOT NULL COMMENT '数量变动(正负)',
    physical_before INT COMMENT '变动前实物量',
    physical_after INT COMMENT '变动后实物量',
    reserved_before INT COMMENT '变动前预占量',
    reserved_after INT COMMENT '变动后预占量',
    available_before INT COMMENT '变动前可用量',
    available_after INT COMMENT '变动后可用量',
    operator VARCHAR(64) COMMENT '操作人',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_txn_type (txn_type),
    INDEX idx_sku (sku_code),
    INDEX idx_ref_no (ref_no),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水';

CREATE TABLE IF NOT EXISTS inventory_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_no VARCHAR(64) NOT NULL UNIQUE COMMENT '调拨单号',
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '状态:PENDING-待处理,COMPLETED-已完成,CANCELLED-已取消',
    sku_code VARCHAR(64) NOT NULL COMMENT '货品编码',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    from_location VARCHAR(64) NOT NULL COMMENT '源库位',
    to_location VARCHAR(64) NOT NULL COMMENT '目标库位',
    qty INT NOT NULL COMMENT '调拨数量',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_transfer_no (transfer_no),
    INDEX idx_sku (sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨单';
