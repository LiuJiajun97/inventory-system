-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V5: 全表统一审计字段(creator/createdAt/updater/updatedAt)
-- 1) Supplier/Customer 重命名 createdBy→creator, updatedBy→updater
-- 2) 12 张表补缺列

-- ---------- Supplier: 重命名列 ----------
ALTER TABLE supplier RENAME COLUMN created_by TO creator;
ALTER TABLE supplier RENAME COLUMN updated_by TO updater;

-- ---------- Customer: 重命名列 ----------
ALTER TABLE customer RENAME COLUMN created_by TO creator;
ALTER TABLE customer RENAME COLUMN updated_by TO updater;

-- ---------- Item: 补 creator/createdAt/updater/updatedAt ----------
ALTER TABLE item ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE item ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE item ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- Warehouse: 补 creator/updater/updatedAt ----------
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- Location: 补全部四件套 ----------
ALTER TABLE location ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE location ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE location ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE location ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- User: 补 creator/updater/updatedAt ----------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- Dict: 补全部四件套 ----------
ALTER TABLE dict ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE dict ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE dict ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE dict ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- DictType: 补 creator/updater ----------
ALTER TABLE dict_type ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE dict_type ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;

-- ---------- InboundDoc: 补 updater/updatedAt ----------
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- OutboundDoc: 补 updater/updatedAt ----------
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS updater VARCHAR(64) NULL;
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

-- ---------- 5 张明细表: 补 creator ----------
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE transfer_doc_item ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE stocktake_doc_item ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
ALTER TABLE stock_adjust_doc_item ADD COLUMN IF NOT EXISTS creator VARCHAR(64) NULL;
